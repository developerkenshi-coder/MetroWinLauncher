# Changelog

The version name is `versionName` in `app/build.gradle.kts`; each release is a tag named `v` plus the version name.
La storia delle versioni; ogni versione è un tag con il nome `v` più il nome della versione.

## 26.4S: 2026-10-29

- **Calling by voice**: "call Mario" looks for the name among your contacts, by name only, and takes the closest one (the exact name first). It no longer picks a contact because of a number or an e-mail, or the first one in alphabetical order. If several fit equally well it says which and asks for the full name. It tries the other ways the phone heard the name too, and the same goes for "write to…". With several numbers it takes the default one, else the mobile.
- **Numbers said in digits** ("call 333 1234567") are dialed as they are, and no longer looked up among the names.
- **Voice recognition**: where the phone has no offline model for your language it now uses the online service instead of failing every time; a busy or stuck recognizer is tried again once; if the phone gives up after having heard words, those words are used; it keeps more alternatives and waits a little longer for the end of the phrase.
- **Try again ends**: after three failures in a row the voice screen says so and stops offering to try again.
- **Flashlight**: at the top of the slider it now shines at its true maximum (it was lit at the phone's default level, dimmer than just below the top).
- **VPN button**: opens the VPN app of the phone. With one VPN app it takes that; with several it asks which one, and a long press chooses again. It lights while a VPN is connected and shows the server when the app names it in its notification.
- **Hotspot**: the button is called "Hotspot"; a touch or a long press opens the hotspot screen of Android (Android does not let an app switch it on).
- **Apps tile**: how many apps are installed; wide and large also show the five used most this week (needs the usage access, a switch in the settings of Android).
- **Battery tile**: the charge with a graph of how long it should last by the estimate Android already keeps, small on medium and extended on wide and large.
- **Data usage tile**: what went over Wi-Fi and mobile data today; wide and large add the monthly average (needs the usage access).
- **Memory tile**: used and free with a bar; wide and large add what takes the space: apps, photos, videos, music, other files.
- **Lists in the app list**: the chips under the search box are lists now. The categories are the lists you start with and any of them can be deleted; *+* makes a new one, a long press on a chip renames or deletes it, and *add to list* in the menu of an app puts it in one.

Download: [`MetroWinLauncher-26.4S.apk`](https://github.com/developerkenshi-coder/MetroWinLauncher/releases/tag/v26.4S).

## 26.3S: 2026-10-28

- **Updates from GitHub**: *settings, about, updates* looks for a new release now and every 12 hours, tells you what is new before you update, downloads the APK (checked against the checksum of the release) and starts the installer by itself. It can be switched off.
- **Photo tiles in high quality**: the pictures of the Photos, Album, Camera, People and Group tiles are read at the size the tile is drawn, and the faces of the contacts at their full size, instead of 512-pixel thumbnails.
- **Notifications of an app you open go away**: also the ones that reach the list a few seconds late, and the one you tap in the Action Center (with its sister notifications of the same app).
- **Action Center**: *arrange* moves any button, not only the four on top, with two touches; every button has a long press of its own; a long press on the flashlight sets how strong it shines (kept for next time, where the phone has several strengths); a button and a link for the settings of Android, told apart from the settings of the launcher (the Metro tiles); the rotation button switches the rotation on the spot.
- **Full weather tile** (medium and wide): temperature, high and low, what is felt, wind, humidity, rain, UV, sunrise and sunset, air quality and, wide, the next days.
- **Today card**: the weather, the places near me, the date and the next alarm can each be switched off in the settings or with a long press on them.
- **Photos tile**: choose the gallery app it opens and the albums its pictures come from.
- **Icon packs from the Play Store**: found for more launchers (Nova, ADW, Apex, Smart, Solo, Atom, OnePlus, Lawnchair, Go), with calendar icons, masks and a button that searches the Play Store.
- **Voice assistant icon** on Start, next to the gesture (it can be switched off); the glide sound of the Action Center has its own switch.
- The *Debug developer* section is gone.
- The stock tiles that carried the names of Microsoft apps now have generic names: Game hub, Assistant, Video calls, Cloud storage, Documents, Text editor, Spreadsheet, Presentations and Notes. They only appear next to the real app when it is installed.
- The browser tile has a new icon: a window with a small globe.
- Documentation and the F-Droid files are brought to this version.

Download: [`MetroWinLauncher-26.3S.apk`](https://github.com/developerkenshi-coder/MetroWinLauncher/releases/tag/v26.3S).

## 26.2S: 2026-10-27

- The browser tile on Start is now called "Browser" (it was "Internet Explorer"). Layouts you already have keep the tile.
- Documentation and the F-Droid files are brought to this version.

Download: [`MetroWinLauncher-26.2S.apk`](https://github.com/developerkenshi-coder/MetroWinLauncher/releases/tag/v26.2S).

## 26.1S: 2026-10-27

The first public release (`versionCode 2`).

- **Start screen**: flat live tiles in four sizes, up to three Start pages, folders, named layouts, a photo behind Start with the accent color taken from it.
- **Live tiles**: calendar, photos, people, phone, weather (rain soon, hour by hour, air quality, pollen), battery, steps, music, news and finance (optional), and the live tiles of other apps from their notifications (optional).
- **App list and search**: alphabetical list with jump list, categories and recent apps; indexed search with quick answers (calculator, conversions, currencies, dates, timers, reminders).
- **Action Center**: quick buttons that name the Wi-Fi network and the Bluetooth device, volume, ringer and brightness, notifications with quick reply, grouping, snooze and silence.
- **Metro lock screen**, voice commands, configurable gestures.
- **Three looks**: Classic, Modern Glass and Modern Droid, with the 20 Windows Phone colors or your own.
- **Backup** to a file or a folder and **encrypted sync between phones**; work profile and private space; Kids Corner; accessibility options.
- Free software under the GPL-3.0-or-later; no ads, no analytics, no Google service required; English and Italian.

Download: [`MetroWinLauncher-26.1S.apk`](https://github.com/developerkenshi-coder/MetroWinLauncher/releases/tag/v26.1S).
