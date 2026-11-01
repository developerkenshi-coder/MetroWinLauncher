# Changelog

The version name is `versionName` in `app/build.gradle.kts`; each release is a tag named `v` plus the version name.
La storia delle versioni; ogni versione è un tag con il nome `v` più il nome della versione.

## 26.5.5S: 2026-11-01

**English:**

*Mini apps*
- **Phone**: starts on the call history with the keypad one tap away; Contacts, Favourites, filter by source; contacts can be created on an account or imported from a `.vcf` file.
- **Call screen** redesigned as on Windows Phone. It crashed on opening (missing wake lock permission), so Android showed the call in Google's phone app: fixed. The call notification goes away when the call ends.
- **Messages** (new): conversations, send and receive text messages through the Android messaging role; drafts are kept encrypted.
- **Mail** signs in for real (IMAP/SMTP over TLS, provider detected as you type). Accounts and passwords are encrypted with the Android Keystore; Outlook, Hotmail and Live sign in on Microsoft's page and the app keeps only an encrypted token. A button opens the provider's page for app passwords.
- **Mail, reading**: sender, date, "to" and "cc", HTML shown as a page (scripts off); pictures from the internet wait for "Show images". Attachments can be downloaded or opened; reply, reply all, forward, mark unread, flag, delete; new messages take "cc" and files (up to 20 MB).
- On the first start after the update, the phone, contacts, messages and mail apps pinned to Start as ordinary apps become the mini app tiles. Back goes one step back before it leaves the app.

*Start and live tiles*
- **Live tiles of notifications are on by default for every app.** The content is read before Android's copy is hidden, so a tile no longer shows a bare "1". With two or more notifications a tile shows a list, newest first. Small tiles show who wrote and the first words too.
- **Large icons on tiles** (Settings, Icons). Any Start screen can be home, with one on each side; the secondary Start can sit to the right, to the left or on both sides.
- Folders open and close app by app; panorama titles no longer cross; the calendar widget can be removed with a long press; edit buttons shrink on small tiles; several apps can be pinned to Start in one step; the Alarm tile follows the clock. Settings and screens move with softer transitions.

*Bars, Action Center, notifications*
- **Windows Phone status bar** (new Settings page, Status bar): notifications, mobile signal with the kind of network (5G, 4G, 3G, E), Wi-Fi with its real bars, Bluetooth, airplane, hotspot, VPN, location, alarm, silent or vibrate, do not disturb, battery saver, battery (charging bolt, percentage) and time. Every icon can be turned off. With the Android bar off, every page keeps clear of the camera hole.
- **Status bar in other apps**: a switch draws the bar over Android's in apps outside the launcher. It stays on screen when you change app, takes no touch, covers the top of the app and steps away in video and games. It is not drawn over the lock screen. A camera hole in the middle or in a corner is read from the phone: the icons are split around it and never sit under the glass.
- **Navigation bar**: Windows Phone buttons over Android's three, exactly its height, following rotation and gestures, out of the way in full screen. The logo held for 0.3 to 1.5 s opens the recent apps.
- **Action Center**: time and date, the song playing with previous, play/pause, next; several messages of a chat collapse under the newest.
- **Notifications**: no leftovers in the Action Center, no double banner, no missed chime, failed reads retried; an app's notifications go away when you open it. Anti burn-in moves everything a few pixels every minute.

*Lock screen*
- **Rebuilt as on Windows Phone 8.1**: full-screen photo, clock, date, weather, alarm, next event and the apps with notifications (icon and counter). Swipe up lifts it with a bounce. The clock and the battery keep clear of a camera hole, at the middle or in a corner.
- **Fingerprint inside the Metro lock screen**: Android reads the finger and the screen lifts with the swipe animation. Options for an under-screen sensor (switch and height), "Use MetroWin fingerprint", and the photo as Android's lock wallpaper. If the phone is unlocked another way, the screen goes away by itself within a moment. No more graphic glitches when swiping up.

*Settings, languages, speed*
- Settings reordered into more pages (Start is now three), with indexed search and a reset for each page. New **Bug Ticket Report** that attaches the launcher's log.
- Complete French and German; Italian, Spanish, Portuguese and English updated.
- Faster on low and mid-range phones: smaller photo and icon caches, fewer redraws, settings read once, Action Center readings off the main thread, tiles stand still behind the app list and Settings.
- Debug build beside the real app: `./gradlew assembleDebug -PidSuffix=.test`.

**Italiano:**

*Mini app*
- **Telefono**: parte dalla cronologia con il tastierino a un tocco; Contatti, Preferiti, filtro per sorgente; i contatti si creano su un account o si importano da un file `.vcf`.
- **Schermata di chiamata** ridisegnata come su Windows Phone. Andava in crash all'apertura (mancava il permesso del wake lock) e Android mostrava la chiamata nell'app telefono di Google: corretto. La notifica della chiamata sparisce quando finisce.
- **Messaggi** (nuova): conversazioni, invio e ricezione di SMS con il ruolo di app messaggi di Android; le bozze sono cifrate.
- **Posta** accede davvero (IMAP/SMTP su TLS, provider riconosciuto mentre scrivi). Account e password sono cifrati con l'Android Keystore; Outlook, Hotmail e Live accedono sulla pagina di Microsoft e l'app tiene solo un token cifrato. Un pulsante apre la pagina del provider per le password per app.
- **Posta, lettura**: mittente, data, "a" e "cc", HTML mostrato come pagina (senza script); le immagini da internet aspettano "Mostra immagini". Gli allegati si scaricano o si aprono; rispondi, rispondi a tutti, inoltra, segna come da leggere, contrassegna, elimina; i nuovi messaggi accettano "cc" e file (fino a 20 MB).
- Al primo avvio dopo l'aggiornamento, le app telefono, contatti, messaggi e posta fissate su Start come app normali diventano i tile delle mini app. Indietro fa un passo indietro prima di uscire dall'app.

*Start e live tile*
- **I live tile delle notifiche sono attivi di default per ogni app.** Il contenuto viene letto prima di nascondere la copia di Android, quindi un tile non mostra più un semplice "1". Con due o più notifiche il tile mostra un elenco, dalla più recente. Anche i tile piccoli mostrano chi ha scritto e le prime parole.
- **Icone grandi sui riquadri** (Impostazioni, Icone). Qualsiasi schermata Start può essere la home, anche con una per lato; lo Start secondario può stare a destra, a sinistra o su entrambi i lati.
- Le cartelle si aprono e chiudono app per app; i titoli panorama non si sovrappongono più; il widget calendario si rimuove con la pressione prolungata; i pulsanti di modifica si riducono sulle tile piccole; si possono fissare più app su Start in un passaggio; la tile Sveglia segue l'orologio. Impostazioni e schermate hanno transizioni più morbide.

*Barre, Action Center, notifiche*
- **Barra di stato Windows Phone** (nuova pagina Impostazioni, Barra di stato): notifiche, segnale mobile con il tipo di rete (5G, 4G, 3G, E), Wi-Fi con le tacche reali, Bluetooth, aereo, hotspot, VPN, posizione, sveglia, silenzioso o vibrazione, non disturbare, risparmio energetico, batteria (fulmine in carica, percentuale) e ora. Ogni icona si può spegnere. Con la barra di Android disattivata ogni pagina sta fuori dal buco della fotocamera.
- **Barra di stato nelle altre app**: un interruttore la disegna sopra quella di Android nelle app fuori dal launcher. Resta a schermo quando cambi app, non prende i tocchi, copre la parte alta dell'app e si ritira nei video e nei giochi. Non viene disegnata sopra la schermata di blocco. Un buco della fotocamera al centro o in un angolo viene letto dal telefono: le icone si dividono attorno ad esso e non finiscono mai sotto il vetro.
- **Barra di navigazione**: pulsanti Windows Phone sopra i tre di Android, della stessa altezza, che seguono rotazione e gesti e si tolgono a schermo intero. Il logo tenuto premuto da 0,3 a 1,5 s apre le app recenti.
- **Action Center**: ora e data, il brano in riproduzione con precedente, play/pausa, successivo; più messaggi di una chat si raccolgono sotto l'ultimo.
- **Notifiche**: niente residui nel Centro azioni, niente doppio banner, nessun suono perso, letture fallite ritentate; le notifiche di un'app spariscono quando la apri. L'anti burn-in sposta tutto di pochi pixel ogni minuto.

*Schermata di blocco*
- **Rifatta come su Windows Phone 8.1**: foto a tutto schermo, ora, data, meteo, sveglia, prossimo evento e le app con notifiche (icona e contatore). Scorrere verso l'alto la solleva con rimbalzo. L'ora e la batteria stanno fuori dal buco della fotocamera, al centro o in un angolo.
- **Impronta dentro il blocco Metro**: Android legge il dito e la schermata si solleva con l'animazione dello swipe. Opzioni per il sensore sotto lo schermo (interruttore e altezza), "Usa fingerprint MetroWin" e la foto come sfondo del blocco di Android. Se il telefono si sblocca in un altro modo, la schermata si toglie da sola in un attimo. Niente più glitch grafici scorrendo verso l'alto.

*Impostazioni, lingue, velocità*
- Impostazioni riordinate in più pagine (Start ora è tre), con ricerca indicizzata e ripristino per ogni pagina. Nuovo **Bug Ticket Report** che allega il log del launcher.
- Francese e Tedesco completi; Italiano, Spagnolo, Portoghese e Inglese aggiornati.
- Più veloce sui telefoni di fascia bassa e media: cache di foto e icone più piccole, meno ridisegni, impostazioni lette una volta, letture dell'Action Center fuori dal main thread, tile fermi dietro l'elenco app e le Impostazioni.
- Build di debug accanto all'app vera: `./gradlew assembleDebug -PidSuffix=.test`.

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
