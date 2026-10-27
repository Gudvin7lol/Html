# Xiaomi TV Custom — v1.4

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

## Version 1.3 UI
- Consistent graphite glass gradients with peach focus ring.
- Native vector navigation and quick-action icons independent of emoji fonts.
- Improved full-width cinematic hero illustration with optional imported poster.
- Compact left navigation, responsive app banners and a two-row home layout.
- Streamlined VPN status display, transparent handoff to v2RayTun.

Note: It is an installable build, not a device-tested exact recreation of artwork in the previously approved concept. Service-specific movie posters and unattended v2RayTun start/stop require distinct integrations.

## Version 1.4
- Keeps scroll and focus intact after opening installed apps (unless app count changes).
- Persistent favourites with left/right reordering from a long press on OK; v1.3 favourite preferences migrate.
- Active navigation section highlighted; accessible native icon rail.
- Safer VPN handoff, expires if not connected within 2 minutes; still no unattended VPN activation.
- Direct shortcut to Android's Home selection settings where supported, retains Projectivy fallback.
- GitHub Actions verifies APK signature, manifest and writes SHA-256 checksum.

## Version 1.5 changes
- User-facing import/export for launcher preferences via Android Storage Access Framework. JSON schema is explicitly versioned, import capped at 128 KiB, invalid files rejected, user confirmation required. No VPN profiles or passwords included.
- Custom large/compact app cards and recently-opened row can be toggled. Both are included in backups.
- Main "Watch" action opens last-launched video service where available, otherwise YouTube (with the existing VPN handoff) or the media section, never an arbitrary app.
- MENU/Settings opens and closes the control centre; Back returns to the last visited screen.
- Native vector menu icon removes another Android TV font dependency.
- No root, no privileged HDMI operations, no unattended VPN start/stop.
- CI builds and checks APK signing, package badging and checksum.

## Version 1.6
- Long OK: add/remove/reorder favourites, hide/unhide an installed app without uninstalling it.
- App list has an explicit Hidden filter. Settings can restore all hidden apps; hidden apps are excluded from home, media and recently-opened rows.
- Foreground peach D-pad focus outline remains visible even on banner-backed app tiles.
- Selected app filter renders a high-contrast solid peach selection.
- Hidden apps included in JSON backup; v1.5 backups remain importable.
- No system apps disabled, no root, no privileged package modifications.
- CI checks Android package ID, version, debug signature and SHA-256.
- Note: GitHub runner debug key may rotate; uninstall or later stable signing may be required to upgrade. Export settings before uninstalling.

## Version 1.7
- Main banner adapts to compact-screen Android TVs (prevents cropped text and controls).
- Menu-right D-pad navigation explicitly targets first real focusable content item; back-left returns to active section.
- Recent apps refresh when returning from playback; attempts to retain selected app tile and homepage scroll position.
- Device diagnostics from Android APIs: free/total storage, RAM, screen pixel resolution, Android version, network and VPN status.
- No new background services or privileged access. The VPN handoff still requires user interaction in v2RayTun.
- CI compiles and verifies debug-signed package and version identity, plus SHA-256.

## Version 1.8
- On-device cinematic home is more like the approved glass interface: two real action panels float over the poster on 870dp-wide or larger TVs, including compact 720p TV layouts.
- First panel launches last used video app or offers the video section. It does NOT pretend to resume a movie.
- Smart VPN panel shows current Android VPN transport state and opens v2RayTun for manual connection, not fake auto-toggle.
- Improved 720p home spacing; hidden apps are no longer shown in video section and empty/hidden favourites fall back to recommended apps.
- Static low-memory background and existing responsive application cards retained.
- CI verifies APK signature, package identity and SHA-256.
- Device D-pad layout and app launch still need testing on physical MiTV-MOOQ1. Never disable stock or Projectivy launcher before testing.
