package com.chet.navdotstyle;

import android.app.Application;
import java.util.concurrent.CopyOnWriteArraySet;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApp extends Application implements XposedServiceHelper.OnServiceListener {
    public interface Listener { void onServiceChanged(XposedService service); }

    private static volatile XposedService service;
    private static final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();

    public static void addListener(Listener listener) {
        listeners.add(listener);
        listener.onServiceChanged(service);
    }

    public static void removeListener(Listener listener) { listeners.remove(listener); }

    @Override public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override public void onServiceBind(XposedService value) {
        service = value;
        for (Listener listener : listeners) listener.onServiceChanged(value);
    }

    @Override public void onServiceDied(XposedService value) {
        service = null;
        for (Listener listener : listeners) listener.onServiceChanged(null);
    }
}
