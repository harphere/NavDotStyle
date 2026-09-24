# Nav Dot Style v1.1.1

An LSPosed module for Android 16 three-button navigation. It replaces Back,
Home, and Recents with one of three selectable styles:

- three small dots
- three short horizontal hashmarks
- small / large / small dots (large Home)

The module targets both Pixel Launcher/Quickstep and SystemUI navigation paths.
It does not replace the navigation bar background, touch targets, button actions,
or accessibility labels.

## Install

1. Build the release APK with the included GitHub Actions workflow.
2. Install the APK.
3. Enable **Nav Dot Style** in LSPosed.
4. Open the app and tap **Grant required LSPosed scopes**.
5. Choose a style. If the icons do not update after initial setup, restart Pixel
   Launcher/SystemUI or reboot once.

## Device target

- Pixel 8 Pro
- Android 16 / Infinity-X
- 3-button navigation
- LSPosed API 101+

## Notes

The buttons are redrawn inside their existing ImageViews, preserving their
original positions and touch areas. Icon colour is reduced to pure black or
pure white from Launcher/SystemUI's current navigation-bar appearance signal.

## Version 1.1.1

- Fixed icons staying white on a white navigation bar on Pixel Launcher.
- The module now follows Launcher3's live `ImageView` tint first, then reduces
  that colour to pure black or pure white.

## Version 1.1.0

- The Back hashmark becomes vertical while the keyboard is visible.
- Icons use pure black on a white navigation background and pure white on a
  non-white background. For exact colour coordination, use Nav Bar Status Match
  v1.0.8 or newer.
