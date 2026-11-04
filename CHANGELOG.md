# Changelog

The version name is `versionName` in `app/build.gradle.kts`; each release is a tag named `v` plus the version name.
La storia delle versioni; ogni versione è un tag con il nome `v` più il nome della versione.

## 26.6.2S: 2026-11-04

It replaces 26.6S, which was withdrawn the day it came out: everything of 26.6S is below, with the fixes at the end of each list.
Sostituisce la 26.6S, ritirata il giorno stesso: qui sotto c'è tutto quello della 26.6S, con le correzioni in fondo a ogni elenco.

**English:**
- **Move several tiles at once**: in the edit bar of Start, a new select button lets you pick several tiles and folders and move them together to another Start screen. A long press takes a whole group (from its title to the next one).
- **Several apps to Start at once**: a button next to the search box of the app list starts picking apps; it is no longer only in the menu of an app.
- **Secondary Start**: choosing the left or the right now moves the screens (your main Start stays the main one) and Start follows at once, instead of changing nothing until the next start.
- **New mini apps**, each in the Windows Phone look:
  - **Calculator**: standard and scientific (trigonometry in degrees or radians, logarithms, roots, powers, factorial, constants, memory), the history of the calculations, and a converter of 11 kinds of units and of currencies.
  - **Notes**: a title, text with headings, lists, bold and boxes to tick, **sketches** (pen, lines, arrows, rectangles, ellipses, labels, eraser, undo) for diagrams, colours, folders, pins and search. Kept encrypted. A note goes out as `.md`, a sketch as `.png`, and **all of them as a backup** (`.zip` with a readable text and picture for each note) that can be restored later.
  - **To do**: lists, a date for each item (it rings as a reminder), stars, late items first.
  - **Voice recorder**: record, play, rename, send, delete; **cut a piece** on the waveform and save it as a new recording (the original stays); **write down what is said** with the speech service of the phone (Android 13 or later) and send the text to Notes.
  - **Calendar**: agenda, month and week; create, change and delete events (with the calendars and reminders of the phone).
  - **Data Sense and Storage Sense**: data by day or hour (Wi-Fi and mobile), by app (in front and in the background), a monthly limit; the memory by kind and the biggest apps; time on screen; battery and network details.
  - **Wallet**: loyalty cards and tickets as barcodes and QR codes (QR, Aztec, PDF417, EAN-13, Code 128, Code 39), read from a picture, expiry date, kept encrypted, backup and restore. The cards of the old wallet are brought in.
  - **News**: one page for each source (Google News, its topics, or any RSS/Atom feed); the choice of the source of the News tile is here now, no longer in Settings.
  - **Files**: two panels, copy and move between them with the choice for names already taken, a trash (30 days), zip archives (create and extract), search (with subfolders), sorting, bookmarks, properties with checksums, a text editor. It needs the access to all the files of the phone.
- **Fix**: with "hide the notifications of Android" on, a notification that came with the screen off or locked lost its sound (Android stops the sound of a notification the moment it is taken away). It now stays where it is until the screen is on and unlocked.
- **Widgets fit their tile**: a widget is placed in the smallest tile that holds it whole (small, medium, wide or large, by the real size of the tiles of the phone, with the size it can be shrunk to when it says one), and one that is still too big for its tile (an older widget, or a tile you made smaller) is shrunk to fit instead of being cut off.
- **The arrow under the tiles of Start** can be turned off (Settings, Start): the swipe to the next page or to the list of apps works as before.
- **The wallpaper of Android follows Start** while MetroWin is the home app (Settings, look): the photo of Start, or its colour with no photo, is also the wallpaper behind the cards of the recent apps, so the one of the original launcher is gone. Turned off, Android goes back to its default wallpaper.
- **The Action Center can be turned off** (Settings, notifications): the panel is gone in the launcher and over other apps, the gestures and the voice command that opened it do nothing, and the notifications stay in the notification shade of Android with their own sounds. Badges, tiles and the lock screen still read them.
- **News, redone**: the large title, the sources as a row of accent-coloured headings, the first headline with a large picture and a big bold title, and the rest with a small picture; the source in the accent colour and how long ago under each. Pictures come from the feeds that have them (not Google News), over https only, are kept in a small cache, and can be turned off in the sources.
- **Action Center, switches**: Wi-Fi, mobile data and flight mode open the sheet of Android with the switch, over the Action Center, instead of the whole settings (Android no longer lets an app switch radios by itself); Bluetooth asks Android's own question to turn on. New **Mobile data** button, which also shows its state, can be kept on Start and called by name.
- **Fix, crash**: the lists of places, actions, properties of the file manager and the sources of the news closed the app on opening (a list that scrolls inside a dialog that scrolls). A test now opens each of them.
- **Notifications that were lost**: every notification is written, encrypted, to a file at the moment it arrives, and leaves only when you dismiss it in MetroWin, it is replaced by a newer one of the same chat, or after 14 days (at most 300): an app taking it back, the listener starting again or the launcher crashing no longer make it disappear. The last app you used is no longer taken for the one in front (its new messages were cleared as read), and group summaries that come alone are kept. The bug report adds a diary of what was removed and why (never the text).
- **Banking apps**: a **safe mode** (Settings, Gestures), on by default, takes away every window MetroWin draws over other apps while a bank (known by its package name, with a list you can edit) is in front or about to be opened, and puts them back when you leave. It explains what cannot be hidden (the accessibility service being on, the permission to draw over apps) and has buttons to turn both off at once.
- **Opening an app**: a choice between the modern animation and the **original Windows Phone turnstile** (tiles turning away by rows, the opening window, the way back), also for the mini apps and for Back.
- **Page margins** (Settings, Screen): narrow, normal or wide, within safe limits, for Start, the app list, settings and the mini apps.
- **Lock screen**: the icons of the apps with notifications can be put in one smaller row (with a +N), or kept as they are.
- **Long names wrap** instead of ending in an ellipsis in the settings and the mini apps (names of apps, cities, layouts, backups, files, contacts, mail, notes); the buttons of the layouts wrap on a narrow screen with big text.
- **Tiles turn over inside**: a live tile with two or more things to say (an app with several chats) now turns only its inside; the name of the app stays where it is. With one thing, the whole face turns as before.
- **Photos tile, choose the source** (Settings, Live tiles): the latest photos of the phone (as before), the albums you tick, or **photos you pick** one by one (several at once, kept readable after a restart; a deleted one is skipped, and a tile with none left shows its icon). Picked photos need no permission to read the gallery.
- **Retro Action bar** (Settings, Start bars): the application bar of Windows Phone 8.1 as Microsoft describes it: round icon buttons without names (four at most), a "…" at the right that opens the bar (names under the buttons, the other actions as lowercase text items sliding up), nothing else. It works in the mini apps and in the edit bar of Start (sizes, style, group and screen become text items). The current one is now the **Super Action bar**, still the default.
- **Tiles see through the Start photo** (Settings, Start theme): with a photo behind Start, the tiles can keep 80, 60 or 40 percent of their colour, so the photo shows through them. The photo already moved slower than the tiles while scrolling (the "photo moves slower" switch, on by default, as in Windows Phone 8.1); it still can stand still.
- **Sticky headings and jump list**: one shared heading that stays at the top while its section scrolls, in the app list (letters), the contacts of the phone app (letters, with the jump list of letters when you touch one), the agenda of the calendar (days) and To do (late, today, later). The mail reader's "…" menu is now part of the shared action bar.
- **Fix, Action Center coming down by itself**: a short quick brush near the top edge, a slanted one, or scrolling Start up to the top and running past it with a fast finger opened the panel (a flick opened it whatever the distance). Now the finger has to come down on the top of Start, go clearly down, and pull at least a little before speed counts.
- **Fix, notifications that would not go away**: dismissing a notification that Android still listed (the cancel is asked, not instant) could be undone by a read of the list a moment later, which wrote it down again and left it in the Action Center; the same for "clear all". Dismissed posts are now remembered until Android lets go of them (a newer post of the same chat still comes in). A swipe cut short by a new touch no longer leaves the row half way, neither gone nor back.
- **Fix, long-press menu under the navigation bar**: the menu of an app (with the shortcuts of the app, ten or more entries) was laid out over the whole screen and its last entries ended under the navigation bar, out of reach. It now stays in the visible area: what does not fit is taken from the menu, which scrolls, never from the pressed row.
- **Search screen order**: touching the search box now shows the category chips right under it, before the searches made before (they were under them), and the headings of the results (contacts, settings, files, web…) stay at the top of the screen while their results scroll under them.
- **Letter that follows the app list**: while the list of apps scrolls, a square at the right edge shows the letter of the apps in view, at a height that follows the position in the list, and can be dragged up and down to move through the list quickly by letter (it fades after a moment; scrolling works as before, and the jump list is still on the letter headers).
- **Fix, status bar not in real time**: the bar drawn over the apps read the battery every 30 seconds, so its percentage fell long after the one of Android; it now follows Android's own battery broadcast. The Wi-Fi, flight mode, Bluetooth, sound, do not disturb, battery saver, location, alarm and network icons are read again at the moment the phone announces a change (the poll every few seconds stays as a net), and the clock also follows the minute, time and time zone broadcasts of the phone.
- **Bars away in games**: the status bar and the navigation buttons that MetroWin draws over the apps (windows of the accessibility service, over the game) slide away while a game is in front (category *Game* or the game flag of the app), so they no longer cover what the game draws. A swipe up from the bottom edge brings the buttons back, a swipe down from the top edge the status bar, for four seconds. New switch in Settings, Gestures (on by default). While a game is in front the top edge belongs to this swipe, not to the Action Center strip over other apps.
- **Fix, keyboard**: the keyboard no longer opens by itself when you move between Start screens or come back to the app list; it opens only when you ask for the search.
- **Fix, app list**: the letter heading that stays at the top no longer sits under the status bar and covers the first row.
- **Fix, lock screen**: the battery icon is filled to the real charge (it always showed half).
- **Calls, the way back**: a green bar over the launcher shows the name and the time of a call that is on, and a touch opens the call screen again, also when the screen was closed and the notification is off or gone.
- **Calls on the lock screen**: while the launcher is the phone app, the lock screen stays up during a call with a card (name, time, mute, end call); a call coming in still takes it away so it can be answered. The call screen shows over Android's lock as for any phone app; the lock of Android is never bypassed.
- **Fix, "set as default launcher" question**: coming back to Start from the call screen or the dialer could make Android ask which Home app to use (when that screen was the last of its task, Android looked for the Home app again). The launcher is now reopened by name in that case, which never asks.
- **Fix, edit bar**: Start leaves room under the last row while the edit bar is up, so the tiles at the bottom can be moved.
- **Fix, Action Center**: it no longer slips away at the end of its list; the finger has to go 32 dp past the end, and closing asks for a longer push than opening.
- **Tiles per row** (Settings, Start): automatic as before, or an even count from 4 up to what the screen holds with tiles of at least 44 dp.
- **Free mode** (Settings, Start): a tile stays on the cell you drop it on, with an outline where it lands, and the others do not move up to fill the gap. Turned off, the tiles flow in order as before; the cells you chose are kept, nothing is lost.
- **Steadier tile moves**: a tile moves another one only after resting on it for a moment, no more bouncing back and forth, and the edge scroll is gentler and stops at the edit bar.
- **Distance between the navigation buttons** (Settings, gestures): the back, home and search buttons of the MetroWin navigation bar can be brought closer, from 50 to 110 percent of the usual distance.
- **Fonts**: Settings, Accessibility has a new **font** choice: twenty fonts that come with the launcher (Comfortaa, Dosis, Exo 2, Figtree, JetBrains Mono, Josefin Sans, Jost, Karla, Lexend, Lora, Manrope, Mulish, Nunito, Oswald, Outfit, Quicksand, Raleway, Sora, Space Grotesk, Urbanist, all under the SIL Open Font License) and **your own font file** (.ttf or .otf, kept as a copy). The font of the phone, when on, still comes first. The launcher is about 3 MB bigger.
- **Ways to open an app**: besides the modern turnstile and expand, new styles that only change how the window of the app comes in (fade, slide from the right, rise from the bottom, zoom from the middle, or none), all in Settings, Screen and found by the search of the settings. The **original Windows Phone** turnstile is now the style a new installation starts with (what you chose stays). The speed of the animations of the launcher itself was already in Settings, Gestures (50 to 200 percent); the window animations of these styles have a fixed speed.
- **Favourite Start screen** (Settings, Start): with two or three Start screens, choose the one the launcher returns to when you press Windows or come back from an app.
- **Fix, bars hidden in every app** (26.6S): the status bar and the navigation buttons of MetroWin were slid away in every app, not only in games, and the thin strips that call them back took the touches at the bottom edge, so the menus of other apps could not be reached. The bars now go away only for a game, as before. They are no longer hidden for videos (the navigation buttons already follow the bar of Android in full screen).
- **Fix, updates not always found**: the app asked GitHub only for the release it marks as "latest", which follows the date and not the version number (and is nothing usable while a release has no APK yet). It now reads the last fifteen releases and takes the highest version that has an APK, and the check runs every 3 hours instead of 12.

**Italiano:**
- **Sposta più riquadri insieme**: nella barra di modifica di Start un nuovo pulsante permette di scegliere più riquadri e cartelle e spostarli insieme in un'altra schermata Start. Una pressione lunga prende un intero gruppo (dal suo titolo al successivo).
- **Più app su Start in una volta**: un pulsante accanto alla casella di ricerca dell'elenco app avvia la scelta delle app; non è più solo nel menu di un'app.
- **Start secondario**: scegliere sinistra o destra ora sposta le schermate (il tuo Start principale resta quello principale) e Start le segue subito, invece di non cambiare nulla fino al riavvio.
- **Nuove mini app**, tutte nello stile Windows Phone:
  - **Calcolatrice**: standard e scientifica (trigonometria in gradi o radianti, logaritmi, radici, potenze, fattoriale, costanti, memoria), la cronologia dei calcoli e un convertitore di 11 tipi di unità e di valute.
  - **Note**: titolo, testo con titoli, elenchi, grassetto e caselle da spuntare, **schizzi** (penna, linee, frecce, rettangoli, ellissi, etichette, gomma, annulla) per gli schemi, colori, cartelle, note fissate e ricerca. Salvate cifrate. Una nota esce come `.md`, uno schizzo come `.png`, e **tutte come backup** (`.zip` con un testo e un'immagine leggibili per ogni nota) da ripristinare in seguito.
  - **Da fare**: elenchi, una data per ogni voce (suona come promemoria), stelle, prima le voci in ritardo.
  - **Registratore vocale**: registra, ascolta, rinomina, invia, elimina; **taglia un tratto** sulla forma d'onda e salvalo come nuova registrazione (l'originale resta); **trascrivi quello che si dice** con il servizio vocale del telefono (Android 13 o successivo) e invia il testo alle Note.
  - **Calendario**: agenda, mese e settimana; crea, modifica ed elimina eventi (con i calendari e i promemoria del telefono).
  - **Data Sense e Storage Sense**: dati per giorno o ora (Wi-Fi e mobili), per app (in primo piano e in background), un limite mensile; la memoria per tipo e le app più grandi; tempo sullo schermo; dettagli di batteria e rete.
  - **Portafoglio**: carte fedeltà e biglietti come codici a barre e QR (QR, Aztec, PDF417, EAN-13, Code 128, Code 39), lettura da un'immagine, scadenza, salvate cifrate, backup e ripristino. Le carte del vecchio portafoglio vengono portate qui.
  - **Notizie**: una pagina per ogni fonte (Google News, i suoi argomenti, o un qualsiasi feed RSS/Atom); la scelta della fonte del riquadro Notizie è qui, non più nelle Impostazioni.
  - **File**: due pannelli, copia e sposta dall'uno all'altro con la scelta per i nomi già presi, un cestino (30 giorni), archivi zip (crea ed estrai), ricerca (anche nelle sottocartelle), ordinamento, luoghi preferiti, proprietà con checksum, un editor di testo. Richiede l'accesso a tutti i file del telefono.
- **Correzione**: con "nascondi le notifiche di Android" attivo, una notifica arrivata a schermo spento o bloccato perdeva il suono (Android ferma il suono di una notifica nel momento in cui viene tolta). Ora resta dov'è finché lo schermo non è acceso e sbloccato.
- **I widget entrano nel loro riquadro**: un widget va nel riquadro più piccolo che lo contiene tutto (piccolo, medio, largo o grande, secondo le dimensioni reali dei riquadri del telefono, con la misura a cui può ridursi quando la dichiara), e uno ancora troppo grande per il suo riquadro (un widget più vecchio, o un riquadro che hai rimpicciolito) viene ridotto per entrare invece di essere tagliato.
- **La freccia sotto i riquadri di Start** si può spegnere (Impostazioni, Start): scorrere verso la pagina successiva o l'elenco delle app funziona come prima.
- **Lo sfondo di Android segue Start** finché MetroWin è l'app Home (Impostazioni, aspetto): la foto di Start, o il suo colore senza foto, è anche lo sfondo dietro le schede delle app recenti, così quello del launcher originale sparisce. Spento, Android torna al suo sfondo predefinito.
- **L'Action Center si può disattivare** (Impostazioni, notifiche): il pannello sparisce nel launcher e sopra le altre app, i gesti e il comando vocale che lo aprivano non fanno nulla e le notifiche restano nella tendina di Android con i loro suoni. Contatori, riquadri e schermata di blocco continuano a leggerle.
- **Notizie, rifatte**: il grande titolo, le fonti come una riga di titoli nel colore d'accento, la prima notizia con una grande immagine e un titolo grande in grassetto, le altre con una piccola immagine; sotto ognuna la fonte nel colore d'accento e quanto tempo fa. Le immagini arrivano dai feed che le hanno (non da Google News), solo su https, restano in una piccola cache e si possono spegnere nelle fonti.
- **Action Center, interruttori**: Wi-Fi, dati mobili e modalità aereo aprono il pannello di Android con l'interruttore, sopra l'Action Center, invece delle impostazioni intere (Android non permette più a un'app di accendere le radio da sola); il Bluetooth fa la domanda di Android per accendersi. Nuovo pulsante **Dati mobili**, che mostra anche il suo stato, si può tenere su Start e chiamare a voce.
- **Correzione, crash**: gli elenchi di luoghi, azioni e proprietà della gestione file e le fonti delle notizie chiudevano l'app all'apertura (un elenco che scorre dentro una finestra che scorre). Ora un test apre ognuno di essi.
- **Notifiche perse**: ogni notifica viene scritta, cifrata, in un file nel momento in cui arriva, e ne esce solo quando la elimini in MetroWin, quando ne arriva una più recente della stessa chat o dopo 14 giorni (al massimo 300): un'app che la ritira, il listener che riparte o un crash del launcher non la fanno più sparire. L'ultima app usata non viene più scambiata per quella in primo piano (i suoi nuovi messaggi venivano cancellati come letti) e i riepiloghi di gruppo che arrivano da soli vengono tenuti. Il rapporto bug aggiunge un diario di cosa è stato tolto e perché (mai il testo).
- **App bancarie**: una **modalità sicura** (Impostazioni, Gesti), attiva di default, toglie ogni finestra che MetroWin disegna sopra le altre app mentre una banca (riconosciuta dal nome del pacchetto, con un elenco modificabile) è in primo piano o sta per essere aperta, e le rimette quando esci. Spiega cosa non si può nascondere (il servizio di accessibilità attivo, il permesso di disegnare sopra le app) e ha i pulsanti per spegnere entrambi in un colpo.
- **Apertura delle app**: una scelta tra l'animazione moderna e il **tornello originale di Windows Phone** (riquadri che girano via a righe, la finestra che si apre, il ritorno), anche per le mini app e per Indietro.
- **Margini delle pagine** (Impostazioni, Schermo): stretti, normali o ampi, entro limiti sicuri, per Start, elenco app, impostazioni e mini app.
- **Schermata di blocco**: le icone delle app con notifiche si possono mettere in una sola riga più piccola (con un +N), o lasciare come sono.
- **I nomi lunghi vanno a capo** invece di finire in un puntino di sospensione nelle impostazioni e nelle mini app (nomi di app, città, layout, backup, file, contatti, posta, note); i pulsanti dei layout vanno a capo su uno schermo stretto con testo grande.
- **Il riquadro gira solo dentro**: un riquadro live con due o più cose da dire (un'app con più chat) ora gira solo l'interno; il nome dell'app resta fermo. Con una cosa sola gira tutta la faccia, come prima.
- **Riquadro Foto, scelta della sorgente** (Impostazioni, Riquadri live): le ultime foto del telefono (come prima), gli album spuntati, oppure **foto scelte una per una** (più insieme, rimaste leggibili anche dopo un riavvio; una cancellata viene saltata e un riquadro senza foto mostra la sua icona). Le foto scelte non richiedono il permesso di leggere la galleria.
- **Retro Action bar** (Impostazioni, Barre di Start): la barra delle applicazioni di Windows Phone 8.1 come la descrive Microsoft: pulsanti tondi senza nome (al massimo quattro), un "…" a destra che apre la barra (i nomi sotto i pulsanti, le altre azioni come voci di testo in minuscolo che salgono), nient'altro. Funziona nelle mini app e nella barra di modifica di Start (dimensioni, stile, gruppo e schermata diventano voci di testo). Quella attuale ora si chiama **Super Action bar** ed è ancora la predefinita.
- **Riquadri trasparenti sulla foto di Start** (Impostazioni, Tema di Start): con una foto dietro Start, i riquadri possono tenere l'80, il 60 o il 40 per cento del loro colore, così la foto si vede attraverso. La foto già scorreva più piano dei riquadri (l'interruttore "la foto scorre più piano", acceso di default, come in Windows Phone 8.1); può ancora restare ferma.
- **Intestazioni fisse e salto**: un'unica intestazione che resta in alto mentre scorre la sua sezione, nell'elenco app (lettere), nei contatti dell'app Telefono (lettere, con la griglia di salto alle lettere toccandone una), nell'agenda del calendario (giorni) e in To do (in ritardo, oggi, più avanti). Il menu "…" del lettore di posta ora fa parte della barra azioni condivisa.
- **Correzione, Action Center che scende da solo**: uno sfioramento breve e veloce vicino al bordo alto, uno in diagonale, o far scorrere Start fino in cima e oltre con un dito veloce apriva il pannello (uno scatto lo apriva qualunque fosse la distanza). Ora il dito deve scendere dalla cima di Start, andare chiaramente in basso e tirare almeno un poco prima che conti la velocità.
- **Correzione, notifiche che non se ne vanno**: eliminare una notifica che Android elencava ancora (la cancellazione è chiesta, non istantanea) poteva essere annullata da una lettura della lista un attimo dopo, che la scriveva di nuovo e la lasciava nell'Action Center; lo stesso per "cancella tutto". Ora le notifiche eliminate sono ricordate finché Android non le lascia (un nuovo messaggio della stessa chat entra comunque). Uno scorrimento interrotto da un nuovo tocco non lascia più la riga a metà, né sparita né tornata.
- **Correzione, menu della pressione lunga sotto la barra di navigazione**: il menu di un'app (con le scorciatoie dell'app, dieci o più voci) era disposto su tutto lo schermo e le ultime voci finivano sotto la barra di navigazione, irraggiungibili. Ora resta nell'area visibile: ciò che non entra viene tolto dal menu, che scorre, mai dalla riga premuta.
- **Ordine della schermata di ricerca**: toccando la casella di ricerca le categorie stanno subito sotto di essa, prima delle ricerche recenti (prima stavano sotto), e le intestazioni dei risultati (contatti, impostazioni, file, web…) restano in cima allo schermo mentre i loro risultati scorrono sotto.
- **Lettera che segue l'elenco app**: mentre l'elenco delle app scorre, un quadrato al bordo destro mostra la lettera delle app in vista, a un'altezza che segue la posizione nell'elenco, e si può trascinare su e giù per muoversi velocemente per lettera (svanisce dopo un attimo; lo scorrimento funziona come prima e la lista di salto resta sulle intestazioni).
- **Correzione, barra di stato non in tempo reale**: la barra disegnata sopra le app leggeva la batteria ogni 30 secondi, e la sua percentuale scendeva molto dopo quella di Android; ora segue il broadcast della batteria di Android. Le icone di Wi-Fi, modalità aereo, Bluetooth, suono, non disturbare, risparmio energetico, posizione, sveglia e rete sono rilette nel momento in cui il telefono annuncia un cambio (il controllo ogni pochi secondi resta come rete di sicurezza), e l'orologio segue anche i broadcast del minuto, dell'ora e del fuso orario del telefono.
- **Barre nascoste nei giochi**: la barra di stato e i pulsanti di navigazione che MetroWin disegna sopra le app (finestre del servizio di accessibilità, sopra il gioco) scivolano via mentre un gioco è in primo piano (categoria *Giochi* o flag di gioco dell'app), così non coprono più ciò che il gioco disegna. Uno scorrimento verso l'alto dal bordo basso riporta i pulsanti, uno verso il basso dal bordo alto la barra di stato, per quattro secondi. Nuovo interruttore in Impostazioni, Gesti (attivo di default). Con un gioco in primo piano il bordo alto è di questo gesto, non della striscia dell'Action Center sopra le altre app.
- **Correzione, tastiera**: la tastiera non si apre più da sola quando passi da una schermata Start all'altra o torni all'elenco app; si apre solo quando chiedi la ricerca.
- **Correzione, elenco app**: l'intestazione con la lettera che resta in alto non sta più sotto la barra di stato e non copre la prima riga.
- **Correzione, blocco schermo**: l'icona della batteria è piena quanto la carica reale (prima era sempre a metà).
- **Chiamate, la strada del ritorno**: una barra verde sopra il launcher mostra nome e durata di una chiamata in corso, e un tocco riapre la schermata della chiamata, anche se era stata chiusa e la notifica è disattivata o sparita.
- **Chiamate sul blocco schermo**: finché il launcher è l'app telefono, il blocco schermo resta durante una chiamata con una scheda (nome, durata, muto, termina); una chiamata in arrivo lo toglie comunque per poter rispondere. La schermata della chiamata appare sopra il blocco di Android come per ogni app telefono; il blocco di Android non viene mai aggirato.
- **Correzione, domanda "imposta come launcher predefinito"**: tornando a Start dalla schermata di chiamata o dal telefono, Android poteva chiedere quale app Home usare (quando quella schermata era l'ultima del suo task, Android cercava di nuovo l'app Home). Il launcher ora viene riaperto per nome in quel caso, e così non chiede nulla.
- **Correzione, barra di modifica**: Start lascia spazio sotto l'ultima riga mentre la barra di modifica è aperta, così i riquadri in basso si possono spostare.
- **Correzione, Centro operativo**: non scappa più via alla fine della sua lista; il dito deve andare 32 dp oltre la fine, e per chiuderlo serve una spinta più lunga che per aprirlo.
- **Riquadri per riga** (Impostazioni, Start): automatico come prima, oppure un numero pari da 4 fino a quanto ne tiene lo schermo con riquadri di almeno 44 dp.
- **Modalità libero** (Impostazioni, Start): un riquadro resta sulla cella dove lo lasci, con un contorno dove atterra, e gli altri non salgono a riempire il vuoto. Spenta, i riquadri scorrono in ordine come prima; le celle scelte restano salvate, niente va perso.
- **Spostamenti dei riquadri più stabili**: un riquadro ne sposta un altro solo dopo essersi fermato un attimo sopra, niente più rimbalzi avanti e indietro, e lo scorrimento ai bordi è più dolce e si ferma alla barra di modifica.
- **Distanza tra i tasti della barra di navigazione** (Impostazioni, gesti): i tasti indietro, home e ricerca della barra di navigazione di MetroWin si possono avvicinare, dal 50 al 110 per cento della distanza abituale.
- **Caratteri**: in Impostazioni, Accessibilità c'è una nuova scelta del **carattere**: venti caratteri inclusi nel launcher (Comfortaa, Dosis, Exo 2, Figtree, JetBrains Mono, Josefin Sans, Jost, Karla, Lexend, Lora, Manrope, Mulish, Nunito, Oswald, Outfit, Quicksand, Raleway, Sora, Space Grotesk, Urbanist, tutti con licenza SIL Open Font) e **un file di carattere tuo** (.ttf o .otf, tenuto come copia). Il carattere del telefono, se attivo, ha ancora la precedenza. Il launcher pesa circa 3 MB in più.
- **Modi di aprire un'app**: oltre al tornello moderno e all'espansione, nuovi stili che cambiano solo come entra la finestra dell'app (dissolvenza, scorrimento da destra, salita dal basso, zoom dal centro, o nessuno), tutti in Impostazioni, Schermo e trovati dalla ricerca nelle impostazioni. Il tornello **originale di Windows Phone** è ora lo stile con cui parte una nuova installazione (quello che avevi scelto resta). La velocità delle animazioni del launcher c'era già in Impostazioni, Gesti (dal 50 al 200 per cento); le animazioni della finestra di questi stili hanno velocità fissa.
- **Start preferito** (Impostazioni, Start): con due o tre schermate Start, scegli quella a cui il launcher torna quando premi Windows o rientri da un'app.
- **Correzione, barre nascoste in ogni app** (26.6S): la barra di stato e i pulsanti di navigazione di MetroWin scivolavano via in ogni app, non solo nei giochi, e le strisce sottili che li richiamano prendevano i tocchi sul bordo basso, così i menu delle altre app non si raggiungevano. Ora le barre se ne vanno solo per un gioco, come prima. Non vengono più nascoste per i video (i pulsanti di navigazione seguono già la barra di Android a schermo intero).
- **Correzione, aggiornamenti non sempre trovati**: l'app chiedeva a GitHub solo la release che questo segna come "latest", che segue la data e non il numero di versione (e non serve a nulla finché una release non ha ancora l'APK). Ora legge le ultime quindici release e prende la versione più alta che ha un APK, e il controllo avviene ogni 3 ore invece che ogni 12.

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
