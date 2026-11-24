# Auto Rally - complete project (Phases 1 to 5)

Native Android game, Kotlin + LibGDX. Single Player, AI Competition and Bluetooth multiplayer work with no
internet. Online multiplayer needs internet and a free Firebase Realtime Database (setup below).

## What is inside
- Core race: looping road, 3 laps, timer, coins, obstacles, Nitro boost, best times, sound + mute, pause
- 6 vehicles with riders and animations, 5 tracks, coin unlocks
- AI Competition: 3 rivals, Easy to Expert
- Missions per track, surprise events, achievements, daily challenge, profile, confetti rewards
- Bluetooth multiplayer (up to 6 players): host a game, nearby phones find it and join
- Online multiplayer (up to 6 players): room code + invitation link (autorally://join/CODE), lobby, host picks track,
  multiple rounds, reconnect after short connection drops
- Lobby shows each player's name, vehicle and ready status
- Live positions during multiplayer races (cars of other players are drawn on your road)
- RACE RESULTS screen after every multiplayer round: ranks, times, coins, points
- ROOM LEADERBOARD that adds up points, wins and best time for everyone in that room across rounds
- LEADERBOARD screen: your best times, history of your multiplayer races, and online best times per track

## Build and install
1. Install Android Studio (Hedgehog or newer, JDK 17 is bundled).
2. File > Open > select the AutoRally folder and let Gradle sync (internet needed once).
   If the Gradle wrapper is reported missing, choose "Use Gradle from distribution" (8.4) or run `gradle wrapper`.
3. Enable USB debugging on your phone, plug it in, press Run.
4. APK to share: Build > Build Bundle(s)/APK(s) > Build APK(s) -> app/build/outputs/apk/debug/app-debug.apk


## Build the APK on GitHub (no Android Studio needed)
1. Create a new GitHub repository (private is fine).
2. Upload the CONTENTS of the AutoRally folder (so settings.gradle is at the top level), including the
   hidden .github folder. If the .github folder will not upload, use Add file > Create new file, type
   `.github/workflows/build-apk.yml` as the name and paste the file from this project.
3. Open the Actions tab > Build APK > Run workflow. It takes about 5 to 10 minutes.
4. Open the finished run, scroll to Artifacts, download AutoRally-debug-apk, unzip it and install app-debug.apk
   on your phone (allow "install unknown apps" when asked).
If the run fails, open it and send me the red error lines.

## Online setup (about 5 minutes, free)
1. Go to console.firebase.google.com > Add project (Analytics can be off).
2. Build > Realtime Database > Create database (any location) > start in LOCKED mode.
3. Open the Rules tab and paste the rules below, then Publish.
4. Copy the database URL shown at the top of the Data tab (looks like https://NAME-default-rtdb.firebaseio.com).
5. In the app: Play > Online Multiplayer > SET SERVER > paste the URL. (Or put it in Config.kt and rebuild.)
Every friend needs the same URL, so set it in Config.kt before you build the APK you share.

```json
{
  "rules": {
    "rooms": {
      "$room": {
        ".read": true,
        ".write": true,
        "players": { "$p": { "fin": { ".validate": "newData.isNumber() && (newData.val() == -1 || newData.val() >= 15)" } } }
      }
    },
    "leaderboard": {
      "$track": { "$p": {
        ".read": true,
        ".write": true,
        "time": { ".validate": "newData.isNumber() && newData.val() >= 15" }
      } }
    }
  }
}
```

## Bluetooth: how to play
1. Everyone opens Play > Bluetooth Multiplayer and enters a name.
2. One person taps HOST A GAME (allow the permissions, Bluetooth, and "discoverable").
3. Friends tap FIND A GAME, wait for the scan, and tap the host's phone name.
4. In the lobby choose a vehicle and tap I AM READY. The host picks the track and taps START RACE.
Android 11 or older also needs Location turned on for scanning. Friends do NOT need to pair first.

## Test checklist
- Flight mode: Single Player and AI Competition; then switch Bluetooth ON (flight mode allows it) and race 2 phones
- Bluetooth with Wi-Fi and mobile data off, 2 to 3 phones, 2 rounds in a row, one phone leaving mid-race
- Online with 2 phones on different networks: create room, join by code, join by invitation link, 2 rounds
- Check the results screen and room leaderboard agree on both phones
- Close and reopen: coins, unlocks, best times, history and name are still there

## Known limits (please read)
- I could not compile or run this in my environment, and could not test on real phones. Expect to fix small
  things on first sync and while testing multiplayer.
- Online sync polls the database a few times a second, so other players' cars can look slightly delayed.
  Cars of different players drive through each other (no collisions between players).
- Online results are checked only by sanity rules (impossible finish times are rejected on the phone and by the
  rules above). Anyone with the database URL could still write fake data. Real server-side validation needs a
  server of your own (for example Firebase Cloud Functions).
- Invitation links use a custom scheme (autorally://join/CODE). Some chat apps do not make these clickable;
  the room code always works. HTTPS app links would need your own domain.
- No Google/Firebase login: players are identified by a random id stored on the phone.
- Bluetooth range and speed are limited; 2 to 4 phones is the sweet spot.

## Files
- AutoRallyGame.kt: game loop, screens, input, rewards, multiplayer screens
- Mp.kt, BluetoothSession.kt, OnlineSession.kt: multiplayer rooms, Bluetooth transport, online transport + online times
- Platform.kt, MainActivity.kt: Android permissions, Bluetooth enabling, sharing, invitation links
- Config.kt: Firebase URL
- Ai.kt, Missions.kt, Track.kt, Vehicles.kt, Scenery.kt, Sfx.kt: game content
