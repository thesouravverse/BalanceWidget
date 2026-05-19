# Store Assets

Source SVGs for Play Store graphics. Convert to PNG before uploading.

## Files

| File | Purpose | Required size | Notes |
|---|---|---|---|
| `play_store_icon_512.svg` | Play Store listing icon | 512×512 PNG | **No transparency**, no rounded corners (Play crops). |
| `play_feature_graphic_1024x500.svg` | Feature graphic (top of store listing) | 1024×500 PNG | Opaque background, no critical text near edges. |

## Converting SVG → PNG

**Easiest (no install):** drag the `.svg` file into <https://svgtopng.com/>, choose the correct size, download. Make sure "transparent background" is **OFF** for the icon.

**On your phone:** open <https://svgtopng.com/> in Chrome, upload the file from Google Drive, download the PNG.

**Inkscape (if you ever install it):** File → Export → PNG, set DPI to match target size.

## Screenshots

Capture from your phone after installing the app:

1. Volume-Down + Power to screenshot the home screen with the widget visible.
2. Open the app, screenshot the main screen with at least one account configured.
3. Screenshot the widget look settings (opacity slider).

Required: **minimum 2** phone screenshots (1080×1920 portrait recommended). Up to 8 allowed.

Save them in a new `store-assets/screenshots/` folder before uploading.
