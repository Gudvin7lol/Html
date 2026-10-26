# Xiaomi TV Custom — v1.1

### Target
Xiaomi MiTV-MOOQ1 · Android TV 11.

This branch contains a standalone Android TV HOME/Leanback launcher, separate from the main repository code and the Zamer application.

## Interface
- Original graphite/peach design. Offline cinematic mountain wallpaper and cinematic hero panel.
- Responsive D-pad app grids, categories, favourites (hold OK), app search and installed-app discovery.
- Home, Movies & TV, Applications, HDMI, Smart VPN, Settings, and a Control Centre via remote MENU key.
- Local Wi-Fi status, free storage display, time, live VPN network indicator; links to actual Android settings.

## VPN
- v2RayTun can be opened directly.
- Optional check before YouTube. If Android reports no VPN, launcher opens v2RayTun for the user to connect. Upon return with a VPN established, it opens YouTube.
- No unverified one-tap VPN toggle; no automatic disconnect; no secret/background control of other apps.

## Safety/compatibility
- No root, Accessibility Service, invasive debloating or disabling stock Xiaomi launcher.
- Some proprietary HDMI selectors and automatic HOME replacement may not work on Xiaomi firmware.
- Streaming-provider continue-watching/movie poster APIs are intentionally excluded until legal supported integrations exist.
- Keep Projectivy as rollback until TV tested.
- Uses lightweight Android native Java views and vector artwork. No runtime network image loading or video wallpapers.

## Build
In branch xiaomi-tv-custom-build, GitHub Actions workflow \`tv-apk.yml\` builds an installable debug-signed APK with JDK 17, Gradle 8.7, Android SDK 35. Debug signature is for personal sideload testing. Use Android Studio/real Xiaomi TV to validate runtime navigation and compatibility.

**Note:** A successful compilation is not proof the launcher works on this exact TV. Installation testing remains necessary.
