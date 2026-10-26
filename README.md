# Xiaomi TV Custom — installable Android TV launcher v1.0

**Target:** Xiaomi MiTV-MOOQ1, Android TV 11. This project is completely separate from Zamer's application files. Built on branch xiaomi-tv-custom-build.

## Included
- Graphite + peach theme, locally rendered cinematic mountain wallpaper; no remote media/downloads.
- Android TV launcher / Home intents. Whether HOME is replaceable depends on Xiaomi firmware.
- Full remote D-pad interaction: six sections, Android apps, favorites via long-OK, shortcuts, settings, HDMI settings entry.
- Installed apps are discovered locally. YouTube, Kinopoisk, Ivi, Okko etc. appear if installed.
- VPN status based on Android VPN network state; v2RayTun launcher shortcut.
- YouTube VPN **check-and-handoff**: if disconnected, opens v2RayTun to connect, launches YouTube after returning with VPN active. This is *not* an automatic VPN toggle and requires user interaction.

## Excluded
- Direct VPN start/stop with no interaction (no verified exported API).
- Streaming provider watch history/film metadata, privileged HDMI switch, privileged overlay.
- User telemetry, ads, forced removal/disable of system packages.

## Build
Push branch xiaomi-tv-custom-build to GitHub; Actions builds using Gradle 8.7 and Android SDK 35. Download artifact XiaomiTVCustom-1.0.0-apk, extract app-debug.apk. APK has DEBUG signature, which is suitable for sideload tests but not final distribution. Device testing is required before claiming complete release readiness.

## TV installation
1. Keep Projectivy and Xiaomi stock launcher installed as fallback.
2. Transfer app-debug.apk to TV and install (allow unknown apps for the file manager).
3. Open Xiaomi TV Custom from Apps. Do not disable Xiaomi home until new launcher confirmed.
4. Test Back/Home/D-pad, YouTube, VPN handoff, media apps, and display resolution.
