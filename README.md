# Shadow Hall — Kotlin Android Prototype

A starter project for KlencodIDE / Android Studio using Kotlin + OpenGL ES 2.0. The enhanced prototype includes a low-poly corridor, third-person movement/camera, monster patrol, sound-based investigation, last-known-position search and chase behavior, three keys and an exit objective, hiding zones, stamina, flashlight toggle, simple collision, and basic system-tone feedback.

## Import
1. In KlencodIDE, choose **Import project from ZIP** and select this ZIP.
2. If the IDE asks for Android SDK / Gradle sync, use compile SDK 35 and Android Gradle Plugin 8.6.1 with Kotlin 2.0.20, or adapt plugin versions to the versions supported by your IDE.
3. Run on an Android device in landscape mode.

## Current prototype controls
- Drag on the left half of the screen to move.
- Drag on the right half to rotate the camera.
- Tap **اختباء** near furniture to hide; hiding only works near designated cover locations.
- Walk near the gold cubes to collect keys.

## Important status
- This is source code for an early playable rendering prototype, not a finished game.
- Local multiplayer over hotspot is **not implemented yet**. It needs a host/client protocol (for example, Android UDP/TCP sockets), player-state synchronization, and testing on two phones.
- Visuals remain simple OpenGL shapes; audio is limited to system tones, and monster navigation is lightweight, with simple obstacle/closed-door avoidance and axis sliding. Textured models, animation, spatial audio, save state, and local co-op are not implemented.
- No APK is included because this environment does not have a configured Android SDK/Gradle build toolchain, and this project has not been built or tested on a device here.

## Design roadmap
1. **Milestone A — playable base:** third-person camera, movement, collision, key collection, chase/reset loop.
2. **Milestone B — stealth:** noise meter, monster patrol/investigate/search/chase states, actual hiding spots, stamina and flashlight battery.
3. **Milestone C — objective loop:** three-key inventory, locked basement, symbol puzzle, escape gate and two endings.
4. **Milestone D — local co-op:** one phone hosts, second phone joins over the hotspot LAN; synchronize player position, keys, monster state, revive state and game phase. Validate firewall/permissions and both phones on the same local network.
5. **Milestone E — polish:** low-poly assets, animation, sound cues, pause/settings, performance checks on Galaxy A23.


## Alpha 4 update — hide-spot discovery
- The monster can now discover the player if it searches very close to a hiding spot for about 4.5 seconds. Moving away from the hiding spot gives the discovery timer time to cool down.
- This is a source change only; it has not been compiled or device-tested in this environment.


## Alpha 5 — stealth and investigation tuning
- Sprinting can be heard from farther away (9.5 world units); ordinary walking is quieter (3.2 units).
- Visual detection and noise now hold the chase/investigation state for a slightly different duration.
- After losing the player, the monster searches a wider area for longer and moves between search points a little faster.
- Source-only update. Build and device behavior have not been verified in this environment.
