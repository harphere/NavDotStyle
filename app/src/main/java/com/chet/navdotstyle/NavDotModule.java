package com.chet.navdotstyle;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageView;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;

/** Android 16 LSPosed module that replaces 3-button navigation glyphs. */
public final class NavDotModule extends XposedModule {
    private static final String TAG = "NavDotStyle";
    private static final String PREFS = "settings";
    private static final Set<String> TARGETS = Set.of(
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.systemui");

    private enum Role { BACK, HOME, RECENTS }

    private final Map<View, Role> buttons = Collections.synchronizedMap(new WeakHashMap<>());
    private SharedPreferences prefs;
    private volatile String style = "mixed";

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        try {
            prefs = getRemotePreferences(PREFS);
            reloadStyle();
            prefs.registerOnSharedPreferenceChangeListener((p, key) -> {
                if ("style".equals(key)) {
                    reloadStyle();
                    synchronized (buttons) {
                        for (View button : buttons.keySet()) if (button != null) button.postInvalidate();
                    }
                }
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Could not open remote settings; using mixed dots", t);
        }
    }

    private void reloadStyle() {
        String value = prefs == null ? "mixed" : prefs.getString("style", "mixed");
        style = "dots".equals(value) || "marks".equals(value) ? value : "mixed";
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        String pkg = param.getPackageName();
        if (!TARGETS.contains(pkg)) return;

        installImageDrawHook(pkg);
        installAttachFallback(pkg);

        ClassLoader loader = classLoaderOf(param);
        if (loader != null && (pkg.contains("launcher") || pkg.contains("nexuslauncher"))) {
            hookLauncherController(loader, pkg);
        }
        log(Log.INFO, TAG, "Hooks installed for " + pkg);
    }

    private void installImageDrawHook(String pkg) {
        try {
            Method onDraw = ImageView.class.getDeclaredMethod("onDraw", Canvas.class);
            onDraw.setAccessible(true);
            hook(onDraw).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                ImageView view = (ImageView) chain.getThisObject();
                Role role = buttons.get(view);
                if (role == null) return chain.proceed();
                Canvas canvas = (Canvas) chain.getArg(0);
                drawGlyph(view, canvas, role);
                return null;
            });
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Unable to hook ImageView.onDraw in " + pkg, t);
        }
    }

    private void installAttachFallback(String pkg) {
        try {
            Method attached = View.class.getDeclaredMethod("onAttachedToWindow");
            attached.setAccessible(true);
            hook(attached).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                Object result = chain.proceed();
                View view = (View) chain.getThisObject();
                if (view instanceof ImageView && isInsideNavigationHost(view)) {
                    Role role = roleOf(view);
                    if (role != null) bind(view, role, "attach-fallback/" + pkg);
                }
                return result;
            });
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Unable to install attachment fallback in " + pkg, t);
        }
    }

    private void hookLauncherController(ClassLoader loader, String pkg) {
        try {
            Class<?> controller = Class.forName(
                    "com.android.launcher3.taskbar.NavbarButtonsViewController", false, loader);
            for (Method method : controller.getDeclaredMethods()) {
                if (!"addButton".equals(method.getName())) continue;
                method.setAccessible(true);
                hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    if (result instanceof View) bindIfButton((View) result, "launcher-addButton");
                    return result;
                });
            }

            for (String name : new String[]{"init", "onConfigurationChanged", "updateButtonLayoutSpacing"}) {
                for (Method method : controller.getDeclaredMethods()) {
                    if (!name.equals(method.getName())) continue;
                    method.setAccessible(true);
                    hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(chain -> {
                        Object result = chain.proceed();
                        captureControllerFields(chain.getThisObject());
                        return result;
                    });
                }
            }
            log(Log.INFO, TAG, "Launcher navigation controller resolved in " + pkg);
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Launcher controller unavailable in " + pkg + "; fallback remains active", t);
        }
    }

    private void captureControllerFields(Object controller) {
        captureField(controller, "mBackButton", Role.BACK);
        captureField(controller, "mHomeButton", Role.HOME);
        captureField(controller, "mRecentsButton", Role.RECENTS);
        captureField(controller, "mRecentButton", Role.RECENTS);
    }

    private void captureField(Object owner, String name, Role role) {
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value instanceof View) bind((View) value, role, "controller-field/" + name);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return;
            }
        }
    }

    private void bindIfButton(View view, String source) {
        Role role = roleOf(view);
        if (role != null) bind(view, role, source);
    }

    private void bind(View view, Role role, String source) {
        if (!(view instanceof ImageView)) {
            View image = findImageChild(view, role);
            if (image != null) view = image;
        }
        if (!(view instanceof ImageView)) return;
        Role previous = buttons.put(view, role);
        if (previous == null) {
            view.postInvalidate();
            log(Log.INFO, TAG, "Bound " + role + " button via " + source + ": " + describe(view));
        }
    }

    private View findImageChild(View view, Role expected) {
        if (!(view instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ImageView && (roleOf(child) == expected || group.getChildCount() == 1)) return child;
            View nested = findImageChild(child, expected);
            if (nested != null) return nested;
        }
        return null;
    }

    private Role roleOf(View view) {
        String entry = resourceEntry(view).toLowerCase(java.util.Locale.ROOT);
        if (entry.equals("back") || entry.equals("back_button")) return Role.BACK;
        if (entry.equals("home") || entry.equals("home_button")) return Role.HOME;
        if (entry.equals("recent_apps") || entry.equals("recents") || entry.equals("recent_button")
                || entry.equals("overview")) return Role.RECENTS;
        return null;
    }

    private boolean isInsideNavigationHost(View view) {
        View current = view;
        for (int depth = 0; depth < 9 && current != null; depth++) {
            String name = current.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            String entry = resourceEntry(current).toLowerCase(java.util.Locale.ROOT);
            if (name.contains("navigationbar") || name.contains("navbar") || name.contains("taskbar")
                    || entry.contains("navigation_bar") || entry.contains("nav_buttons")
                    || entry.contains("navbar_buttons")) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void drawGlyph(ImageView view, Canvas canvas, Role role) {
        float density = view.getResources().getDisplayMetrics().density;
        float cx = view.getWidth() / 2f;
        float cy = view.getHeight() / 2f;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(resolveColor(view));
        paint.setAlpha(Math.round(Color.alpha(paint.getColor()) * (view.isPressed() ? 0.62f : 1f)));

        if ("marks".equals(style)) {
            boolean verticalBack = role == Role.BACK && isImeVisible(view);
            float width = (verticalBack ? 2.6f : 12f) * density;
            float height = (verticalBack ? 12f : 2.6f) * density;
            RectF rect = new RectF(cx - width / 2f, cy - height / 2f,
                    cx + width / 2f, cy + height / 2f);
            float corner = Math.min(width, height) / 2f;
            canvas.drawRoundRect(rect, corner, corner, paint);
            return;
        }

        float radius = 2.2f * density;
        if ("mixed".equals(style) && role == Role.HOME) radius = 4.3f * density;
        canvas.drawCircle(cx, cy, radius, paint);
    }

    private boolean isImeVisible(View view) {
        try {
            WindowInsets insets = view.getRootWindowInsets();
            if (insets != null) return insets.isVisible(WindowInsets.Type.ime());
        } catch (Throwable ignored) { }

        // Fallback for navigation windows whose IME visibility is not exposed
        // through WindowInsets. A keyboard normally removes a substantial part
        // of the visible root height.
        try {
            android.graphics.Rect visible = new android.graphics.Rect();
            View root = view.getRootView();
            root.getWindowVisibleDisplayFrame(visible);
            return root.getHeight() > 0 && root.getHeight() - visible.bottom > root.getHeight() * 0.15f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int resolveColor(ImageView view) {
        // Launcher3 computes the live navigation-button colour, including the
        // app-requested light-navigation appearance, then installs that colour
        // directly on each ImageView. This is the authoritative signal on Pixel
        // taskbar navigation and must take precedence over drawable internals,
        // which can retain a stale dark-intensity value.
        try {
            android.content.res.ColorStateList tint = view.getImageTintList();
            if (tint != null) {
                int color = tint.getColorForState(view.getDrawableState(), tint.getDefaultColor());
                return isDarkColor(color) ? Color.BLACK : Color.WHITE;
            }
        } catch (Throwable ignored) { }

        // Other Launcher/SystemUI variants expose their current light-navigation
        // decision as a dark intensity. Convert it to a strict binary colour.
        float dark = reflectedDarkIntensity(view);
        if (dark >= 0f) return dark >= 0.5f ? Color.BLACK : Color.WHITE;

        try {
            WindowInsetsController controller = view.getWindowInsetsController();
            if (controller != null) {
                boolean lightNav = (controller.getSystemBarsAppearance()
                        & WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) != 0;
                return lightNav ? Color.BLACK : Color.WHITE;
            }
        } catch (Throwable ignored) { }

        boolean lightNav = (view.getSystemUiVisibility() & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0;
        return lightNav ? Color.BLACK : Color.WHITE;
    }

    private boolean isDarkColor(int color) {
        // Perceptual integer luminance. Alpha is intentionally ignored because
        // Launcher can animate opacity separately from the tint colour.
        int luminance = (299 * Color.red(color)
                + 587 * Color.green(color)
                + 114 * Color.blue(color)) / 1000;
        return luminance < 128;
    }

    private float reflectedDarkIntensity(ImageView view) {
        Object[] candidates = {view, view.getDrawable()};
        for (Object candidate : candidates) {
            if (candidate == null) continue;
            Class<?> type = candidate.getClass();
            while (type != null) {
                try {
                    var field = type.getDeclaredField("mDarkIntensity");
                    field.setAccessible(true);
                    Object value = field.get(candidate);
                    if (value instanceof Number) {
                        float f = ((Number) value).floatValue();
                        return Math.max(0f, Math.min(1f, f));
                    }
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                    continue;
                } catch (Throwable ignored) { }
                break;
            }
        }
        return -1f;
    }

    private ClassLoader classLoaderOf(PackageReadyParam param) {
        try {
            Method getter = param.getClass().getMethod("getClassLoader");
            return (ClassLoader) getter.invoke(param);
        } catch (Throwable ignored) {
            return Thread.currentThread().getContextClassLoader();
        }
    }

    private String resourceEntry(View view) {
        if (view == null || view.getId() == View.NO_ID) return "";
        try { return view.getResources().getResourceEntryName(view.getId()); }
        catch (Throwable ignored) { return ""; }
    }

    private String describe(View view) {
        return view.getClass().getName() + "#" + resourceEntry(view)
                + " (" + view.getWidth() + "x" + view.getHeight() + ")";
    }
}
