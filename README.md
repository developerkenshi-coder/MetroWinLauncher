<p align="center">
  <img src="docs/readme/en/hero.svg" width="900" alt="MetroWin Launcher: the Windows Phone Start screen, on Android">
</p>

<p align="center">
  <b>The Windows Phone Start screen, on Android.</b><br>
  Flat live tiles that flip with real content, an alphabetical app list, an Action Center, and a Metro lock screen.<br>
  Free software. No ads, no analytics, no Google services required.
</p>

<p align="center">
  <a href="https://github.com/developerkenshi-coder/MetroWinLauncher/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/developerkenshi-coder/MetroWinLauncher?label=release&color=1ba1e2"></a>
  <a href="LICENSE"><img alt="License: GPL-3.0-or-later" src="https://img.shields.io/badge/license-GPL--3.0--or--later-1ba1e2"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-1ba1e2">
  <a href="https://github.com/developerkenshi-coder/MetroWinLauncher/releases"><img alt="Downloads" src="https://img.shields.io/github/downloads/developerkenshi-coder/MetroWinLauncher/total?color=1ba1e2"></a>
</p>

<p align="center"><b>English</b> · <a href="README.it.md">Italiano</a></p>

<p align="center">
<a href="#start-screen"><img src="docs/readme/en/tile-start.svg" width="312" height="156" alt="start screen"></a><a href="#live-tiles"><img src="docs/readme/en/tile-live.svg" width="156" height="156" alt="live tiles"></a><a href="#app-list-and-search"><img src="docs/readme/en/tile-apps.svg" width="156" height="156" alt="apps &amp; search"></a><br>
<a href="#action-center-and-notifications"><img src="docs/readme/en/tile-action.svg" width="156" height="156" alt="action center"></a><a href="#lock-screen-voice-and-gestures"><img src="docs/readme/en/tile-lock.svg" width="156" height="156" alt="lock screen"></a><a href="#looks-and-colors"><img src="docs/readme/en/tile-looks.svg" width="312" height="156" alt="looks &amp; colors"></a><br>
<a href="#apps-built-in"><img src="docs/readme/en/tile-builtin.svg" width="312" height="156" alt="apps built in"></a><a href="#lock-screen-voice-and-gestures"><img src="docs/readme/en/tile-gestures.svg" width="156" height="156" alt="voice &amp; gestures"></a><a href="#backup-profiles-and-accessibility"><img src="docs/readme/en/tile-backup.svg" width="156" height="156" alt="backup"></a><br>
<a href="#settings-and-tutorial"><img src="docs/readme/en/tile-settings.svg" width="156" height="156" alt="settings"></a><a href="#for-app-developers-your-own-tile-on-start"><img src="docs/readme/en/tile-developers.svg" width="156" height="156" alt="for developers"></a><a href="#build-from-source"><img src="docs/readme/en/tile-build.svg" width="312" height="156" alt="build from source"></a><br>
<a href="#privacy-and-permissions"><img src="docs/readme/en/tile-privacy.svg" width="312" height="156" alt="privacy"></a><a href="CHANGELOG.md"><img src="docs/readme/en/tile-changelog.svg" width="156" height="156" alt="changelog"></a><a href="#documentation"><img src="docs/readme/en/tile-docs.svg" width="156" height="156" alt="documentation"></a>
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="24%" alt="Start screen with live tiles">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="24%" alt="Start screen, Modern Glass theme">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="24%" alt="Action Center with notifications">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="24%" alt="Metro lock screen">
</p>

> [!CAUTION]
> **Important Installation Notice:**
> 
> In recent versions of Google Play Services / Google Play Store, Google has begun restricting sideloading for apps not distributed through its official store, flagging them as potentially harmful. I am actively working on resolving this issue, and updates will follow soon. If you wish to install the app and are blocked from doing so, you can temporarily disable Google Play Protect in the Google Play Store settings.

## download

<p align="center"><a href="https://github.com/developerkenshi-coder/MetroWinLauncher/releases/latest"><img src="docs/readme/en/download.svg" width="480" alt="download the latest release"></a></p>

`MetroWinLauncher-26.10.5S.apk`, for Android 8.0 (API 26) or newer.

1. Download the APK on your phone.
2. Open it. When Android asks, allow your browser or file manager to install unknown apps.
3. Press Home and choose **MetroWin Launcher** as the Home app. The first start shows a short tour, and every permission is optional.

<details>
<summary>Check the download</summary>

The APK is signed with this certificate (SHA-256):

```
B5:E0:52:EB:C5:48:EF:A1:C5:71:6B:85:E0:D2:0F:FC:BC:D3:0E:31:C6:76:1E:BC:C2:34:A9:DD:E7:DF:0C:61
```

Each release also has a `SHA256SUMS` file. To check both:

```sh
shasum -a 256 -c SHA256SUMS
apksigner verify --print-certs MetroWinLauncher-26.10.5S.apk
```

Android keeps the app updatable only with the same signing key, so install updates from this page. An F-Droid release is planned
for later; its APKs will be signed by F-Droid, so moving from one to the other will need an uninstall (back up from the launcher's settings first).
</details>

## what it is

MetroWin Launcher brings the Start screen of Windows Phone (Metro, 8 and 8.1) to Android: flat tiles that flip with live content,
an alphabetical app list, turnstile and tilt animations, and the system features a launcher can really offer. It has three looks
(*Classic*, *Modern Glass*, *Modern Droid*) with customizable colors, and it does nothing while it is not on screen. It also brings its own apps (phone, people, messages, mail, calendar, photos, notes and more) in the same look.

<details>
<summary>More screenshots</summary>
<br>
<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="24%" alt="Start, scrolled: calendar and forecast tiles">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="24%" alt="Alphabetical app list with the Today card and search">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="24%" alt="Forecast page">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width="24%" alt="Settings">
</p>
</details>

## features

### start screen
- **Tiles** in small, medium, wide and large sizes on a block grid; the number of columns follows the screen width, so tiles keep the same size in landscape and on tablets.
- **Up to three Start pages**, **folders** (drop a tile on another, multi-select, or "add to folder" from the app list), drag to reorder, **undo**, and **named layouts** (Home, Work, Travel) you can switch with one tap.
- **Tile styles**: standard flip, *Iconic* (big number and icon), *Cycle* and *collage* for photos; flip or slide transitions.
- **Add to Start**: contacts (with their photo), websites, Android widgets, Android settings, app shortcuts, quick-dial numbers, notes, lists, counters, countdowns, timers, a stopwatch, a wallet for loyalty cards.
- **Your photo behind Start** with parallax, zoom and framing, and the **accent color taken from the photo**; clock and date, a second time zone, titles for groups of tiles.
- **After the last tile** you choose the round arrow, three plain dots or nothing; **free mode** snaps big tiles to blocks and lets the others flow around the one you drop; the photo of Start stays still between two Starts; **landscape** has six columns on a phone on its side.

### live tiles
Every one is optional and reads data only while it is pinned to Start.
- **Calendar**, **Photos** (and single albums), **People** (faces, birthdays, groups), **Phone** (missed calls).
- **Weather** with rain-soon, hour by hour, temperature chart, air quality and pollen (Open-Meteo), plus **Battery**, **Flashlight**, **Steps**, **Moon phase**, **World clock**, **Storage** and **Alarms**.
- **News and Finance** (off until you add them): news from Google News or any RSS feed you choose, exchange rates and a few cryptocurrencies.
- **Music** with cover and controls, and the **live tiles of other apps** (WhatsApp, Telegram, Instagram, Reddit, any app): the tile flips to show the conversation, the picture, the download progress or the song, by reading that app's notifications. Off until you turn it on, with a switch for each app.

### app list and search
- All the apps of every profile in alphabetical order, with a jump list, recent apps (★), most used, new apps, **categories**, app shortcuts, rename, hide and **lock an app** behind your screen lock.
- **Indexed search** that finds apps by name, initials (`gm` for Google Maps), several words in any order, what an app is for, and even typos; it also searches the launcher's settings, what is on Start, contacts, Android settings, calendar events and the web (DuckDuckGo, Google, Bing, Brave, Startpage, Ecosia or Qwant).
- **Quick answers** while you type: calculator, unit conversions, currencies, dates, time in other cities, timers and alarms, reminders, dice. A T9 dial finds contacts from digits.
- A **Today** card with the date, weather, next event and reminders.

### action center and notifications
- Four quick buttons you choose (eight more behind them): the **Wi-Fi** and **Bluetooth** buttons name the network and the device you are connected to, and a long press lists the networks around you or the paired devices.
- **Volume, ringer, brightness**, flashlight strength, a timed silent mode and quiet hours.
- **Notifications** you swipe away, **reply to** from the panel, **group by app**, snooze, silence per app or by keyword, with a short history. Opening an app clears its notifications and the number on its tile.
- An optional **Metro banner** and an optional **Action Center over other apps** (they use an accessibility service that reads nothing from the screen).

### lock screen, voice and gestures
- A **Metro lock screen** with photo, big clock, next alarm, notification counts per app, weather and music. Android does not let a launcher replace its lock screen, so it works as the screensaver and has a preview in the launcher.
- **Voice commands** in English and Italian, recognized on the phone when your language is available: open an app, call someone, set a timer or alarm, flashlight, search, weather, and more.
- **Gestures** you configure: double tap, two-finger swipes, home pressed twice; predictive back; open-app animations (turnstile or expand).
- A **Glance screen** as a screensaver (the time and the icons on black, moving every minute) and **charging animations**: a banner when you plug the cable in, inside the launcher and over other apps, and a battery that fills in the status bar and on the lock screen (one switch turns it off).

### looks and colors
- **Classic** (flat Windows Phone), **Modern Glass** (frosted, rounded glass over a glowing backdrop) and **Modern Droid** (Material You), each in dark, light, by-the-hour or sunset-to-sunrise.
- The **20 Windows Phone colors** or a color of your own, a different accent at night, **OLED black**, text size and weight, icon shape, **icon packs** from the Play Store (ADW, Nova, Apex, Smart, Go) and **theme files** you can share.

### backup, profiles and accessibility
- **Backup** of the layout to a file or a folder (any folder Android can open), by hand or every 1, 7 or 30 days, and **encrypted sync between phones** through that folder (AES-256-GCM, key from your passphrase with PBKDF2-SHA256).
- **Work profile** and **private space** (Android 15), **Kids Corner**, and accessibility: TalkBack for every tile and row, text size, reduced motion, stronger contrast.

### apps built in
The launcher brings its own apps, in the same look and with the same theme, opening with the turnstile. They need no account of their own.
- **Phone** (keypad, recent calls, favourites, the photo of the contact on the whole screen during a call), a **People hub** (all, what's new, favourites, a jump list and a card with the history of calls and messages), **Messages** (bubbles with their tail, a pivot for all and unread) and **Mail** (unread in bold, previews, thumbnails of attached pictures, several messages at once).
- **Calendar** (months as pages you swipe, a day view with the hours and the line of "now"), **Photos** (a hub with albums, dates and favourites, and a full viewer with pinch, double tap, share, delete and info) and **Now Playing** (the cover on the whole page, a seek bar, the controls and the queue).
- **Notes** (as tiles with colour and date, and sketches with pen, highlighter and eraser), **To do** (drag to reorder), the **Recorder** (the live wave while it records), the **Calculator**, **Wallet** (stacked cards, and scanning a code with the camera: a camera permission, used only there), **News** and **Files** (letters and a jump list).
- **Shizuku Terminal**, for people who run Shizuku: direct buttons you choose one by one, a battery doctor, sensors and monitor, more information about the phone, a terminal, apps and system pages, and **Metro settings** for Wi-Fi, mobile network, Bluetooth, date and time and sounds.

### for app developers: your own tile on start

If you develop an app, it can show a title, a short text and a counter on its own Start tile, like the live tiles of
Windows Phone. The tile flips to show the text, and the counter appears as a number. An app can only write to **its own**
tile, never to another app's: the launcher checks which app is actually calling, whatever the call claims.

## privacy and permissions

- **Every permission is optional** and powers one tile or one feature; the tour asks for each one only when you reach that feature.
- **Network** is used for the weather (Open-Meteo; only the city is sent, over https), and for the news and finance tiles only if you add them. There are no analytics, no ads and no trackers.
- **No Google service is required**: the news tile reads Google News by default or any RSS feed you choose, web search uses the engine you pick, and links to maps, notes or calendar open whatever app you have for them.
- Calendar, contacts, photos and call log are read **only for the tiles you pin**; counters, searches and history stay on the phone and are not part of backups.

The full table of permissions, with what each one is for, is in the [documentation](docs/DOCUMENTAZIONE.md#permessi).

## build from source

You need JDK 17 or newer and the Android SDK (platform 36).

```sh
git clone https://github.com/developerkenshi-coder/MetroWinLauncher.git
cd MetroWinLauncher
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleOptimized      # the release build signed with the debug key: lighter on battery, installs over the debug build
./gradlew testDebugUnitTest      # unit and UI tests on the JVM
```

`assembleRelease` signs with the key described in `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), which is never
committed; without that file the release APK is left unsigned, which is what F-Droid needs.

## documentation

The complete documentation, in Italian, covers every feature, every setting, the permissions, the architecture and the limits of what a launcher can do on Android:
**[docs/DOCUMENTAZIONE.md](docs/DOCUMENTAZIONE.md)**. The release history is in [CHANGELOG.md](CHANGELOG.md).

## contributing

Bug reports and ideas are welcome in the [issue tracker](https://github.com/developerkenshi-coder/MetroWinLauncher/issues), in English or Italian.
For a bug, tell us the phone, the Android version and the launcher version (*settings → tools → about*). Pull requests are welcome; please open an issue first for anything big.

## license and credits

Released under the **GNU General Public License v3.0 or later** ([LICENSE](LICENSE)).
Weather data by [Open-Meteo.com](https://open-meteo.com/), licensed CC BY 4.0. Fonts are Selawik (Microsoft, SIL Open Font License 1.1). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

MetroWin Launcher is an independent project. It is not affiliated with or endorsed by Microsoft; Windows Phone is a trademark of Microsoft Corporation.
