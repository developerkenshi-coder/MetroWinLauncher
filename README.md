# MetroWin Launcher

Un launcher Android che riproduce la schermata Start di **Windows Phone** (Metro, 8/8.1): riquadri piatti con live
tile che girano, lista app alfabetica, turnstile e tilt, più le funzioni di sistema che un launcher può davvero fare:
Action Center, cartelle, più schermate Start, widget, ricerca, voce, sincronizzazione cifrata, accessibilità. Ha **tre temi**
(*Classic*, *Modern Glass*, *Modern Droid*) con colori personalizzabili, e **non fa nulla quando non è sullo schermo**.

| Start | Cartella aperta | Action Center |
|---|---|---|
| ![Start](docs/screenshots/start.png) | ![Cartella](docs/screenshots/start_folder_open.png) | ![Action Center](docs/screenshots/action_center.png) |

| Lista app | Impostazioni | Blocco schermo Metro |
|---|---|---|
| ![App](docs/screenshots/apps.png) | ![Impostazioni](docs/screenshots/settings_home.png) | ![Blocco schermo](docs/screenshots/lock_screen.png) |

| Voce | Notifiche raggruppate | Sincronizzazione |
|---|---|---|
| ![Voce](docs/screenshots/voice_heard.png) | ![Notifiche](docs/screenshots/notification_group_open.png) | ![Sync](docs/screenshots/settings_sync.png) |

I tre temi (Classic, Modern Glass, Modern Droid) sulla stessa schermata Start, e sulle impostazioni:

| Start | Controlli | Impostazioni del tema |
|---|---|---|
| ![Temi: Start](docs/screenshots/themes_start.png) | ![Temi: controlli](docs/screenshots/themes_controls.png) | ![Temi: impostazioni](docs/screenshots/themes_settings.png) |

Start in orizzontale, con più colonne e riquadri della stessa dimensione che in verticale:

<img src="docs/screenshots/start_landscape.png" alt="Start in orizzontale" width="480">

> Le immagini sono disegnate dai test di screenshot su JVM (vedi [Test](#test)), non catturate da un telefono.

## Indice

- [Come provarlo e vederlo in Android Studio](#come-provarlo-e-vederlo-in-android-studio)
- [Il tutorial iniziale](#il-tutorial-iniziale)
- [Cosa c'è](#cosa-cè)
- [Le impostazioni, sezione per sezione](#le-impostazioni-sezione-per-sezione)
- [Cosa non si può fare da un launcher, e come l'ho aggirato](#cosa-non-si-può-fare-da-un-launcher-e-come-lho-aggirato)
- [Permessi](#permessi)
- [Per chi sviluppa un'app: un tile tuo su Start](#per-chi-sviluppa-unapp-un-tile-tuo-su-start)
- [Test](#test)
- [Struttura](#struttura)
- [Note e limiti](#note-e-limiti)

## Come provarlo e vederlo in Android Studio

Servono JDK 17+ e l'Android SDK (piattaforma 36). Android 8.0 (API 26) o successivo.

1. In Android Studio: **Git → Fetch**, poi passa al ramo **`main`** (*Git → Branches → origin/main → Checkout*), oppure
   *File → New → Project from Version Control* con l'indirizzo del repository, e scegli `main`.
2. **File → Sync Project with Gradle Files**.
3. Collega un telefono o avvia un emulatore e premi **Run** (▶).

Da terminale:

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Poi premi Home: Android chiede quale launcher usare, scegli **MetroWin Launcher** (o *Impostazioni → App → App
predefinite → App Home*). Alla prima apertura parte il [tutorial](#il-tutorial-iniziale).

## Il tutorial iniziale

Alla prima apertura il launcher mostra un giro di **11 pagine**, nello stile di Windows Phone (titolo piccolo in maiuscolo, titolo
grande in minuscolo con il colore d'accento, un quadrato con il glifo). Ogni pagina spiega una parte del launcher e, se la sua
funzione ha bisogno di un permesso, lo chiede **lì**, con un pulsante, e dice "consentito" quando c'è: niente viene chiesto in anticipo e
niente è obbligatorio. Si va avanti con il pulsante o con uno swipe, si esce con "salta", Indietro torna alla pagina prima.

| Pagina | Cosa spiega | Cosa chiede |
|---|---|---|
| benvenuto | come si usa il giro | — |
| rendilo la tua home | perché serve essere l'app Home | **app Home** (dialogo di Android 10+, altrimenti le impostazioni dell'app Home) |
| la tua schermata start | tocca, tieni premuto, cartelle, tile piccoli affiancati, più schermate, "+" | — |
| live tile | tile che girano e i loro stili | **foto**, **calendario**, **contatti**, **cronologia chiamate** (uno per uno) |
| meteo | il tile del meteo e dove si sceglie la città | **posizione** |
| tutte le tue app | jump list, ricerca, menu, voce | **microfono** |
| action center | tirata dal bordo, pulsanti, notifiche, risposta e gruppi | **accesso alle notifiche** (pagina di Android) |
| il tuo aspetto | i tre temi e i colori | — |
| gesti | bordi, doppio tocco, due dita, Indietro | **servizio di blocco** (accessibilità, facoltativo) |
| tienilo attivo | perché Android chiude le app e come evitarlo; che stare attivo non costa batteria | **nessuna ottimizzazione della batteria**; e, sui telefoni che hanno un elenco "avvio automatico", di aggiungerci il launcher |
| rendilo tuo | tutte le sezioni delle impostazioni | — |

| Benvenuto | Live tile e permessi | Tienilo attivo |
|---|---|---|
| ![Benvenuto](docs/screenshots/tutorial_welcome.png) | ![Live tile](docs/screenshots/tutorial_live.png) | ![Tienilo attivo](docs/screenshots/tutorial_alive.png) |

Quello che si concede nelle impostazioni di Android (home, notifiche, accessibilità, batteria) viene riletto quando si torna al
launcher, e il pulsante diventa "consentito". Un permesso che Android non chiede più (negato due volte) fa diventare il pulsante
"apri impostazioni", che porta alla pagina dell'app. Il giro si rivede da *start+theme → tools → about → rivedi il tutorial*.

## Cosa c'è

### Start e lista app

- **Riquadri** piccoli, medi e larghi su una griglia a blocchi 2x2 (`data/TileGrid.kt`). Il numero di colonne segue la
  larghezza dello schermo (`TileGrid.columnsFor`): 4 in verticale su un telefono, 6 con "mostra più riquadri", di più in
  orizzontale o su un tablet, così i riquadri restano grandi uguali invece di allargarsi.
- **Fino a tre schermate Start**, con i puntini della pagina, e "sposta alla schermata" dalla barra di modifica.
- **Riquadri piccoli affiancati**: quattro riquadri piccoli stanno in un blocco 2x2. Mentre ne trascini uno, le celle libere
  accanto ad altri riquadri piccoli si mostrano come quadrati vuoti: lascialo lì e si affianca agli altri, con il vicino che
  non si sposta. Così usi tutto lo spazio e fai gruppi **senza cartelle**. Sul bordo di un riquadro piccolo il trascinamento
  riordina, solo il suo centro fa una cartella (più piccolo che sui riquadri grandi). Le celle libere sono sempre quelle di un
  blocco che ha già riquadri piccoli: il layout resta un flusso in ordine, quindi non c'è un punto vuoto a piacere.
- **Lo spazio vuoto accanto ai riquadri**: quando dopo quattro riquadri piccoli (o dopo un riquadro medio) viene un riquadro largo,
  il blocco accanto resta vuoto perché il largo non ci sta. Trascinando un riquadro che sta **più in basso** nell'ordine, quel blocco
  si mostra come un'area vuota: un riquadro piccolo (in una delle quattro celle) o medio (nell'area intera) ci va, e il largo
  resta sulla sua riga. Un riquadro largo non ci sta, e per un riquadro che sta più in alto del vuoto si riordina dal bordo
  (togliendolo, quelli dopo risalirebbero e lo spazio si sposterebbe, quindi non si può mostrare con sicurezza dove finisce).
- **Stili dei riquadri**: standard (con flip), **Iconic** (grande numero e icona) e **Cycle** (foto che si alternano).
- **Live tile**: Calendario, Foto, Persone, Telefono, **Meteo** (Open-Meteo), **Batteria** (carica, tempo alla ricarica),
  **Torcia** (si accende davvero), e le **notifiche** che girano sul riquadro dell'app (*notification peek*).
- **Trascina per riordinare**, modifica con rimuovi, ridimensiona, rinomina, e la barra di modifica con il pulsante *aggiungi*.
- **Cartelle con più app**: tre modi. Un riquadro tenuto sul centro di un altro; il pulsante cartella della barra di modifica,
  che apre la **selezione multipla** (tocchi i riquadri, vedi la spunta e "N selezionati", e *crea cartella*: se tra loro c'è
  già una cartella, gli altri entrano lì); oppure **"aggiungi a cartella"** dal menu di un'app nella lista, che sceglie tra le
  cartelle che hai. Sul riquadro della cartella le prime app sono quadratini e, se sono più di quattro, l'ultimo dice **+N**.
- **Impostazioni Launcher** è un'app della lista (con il glifo dell'ingranaggio, in ordine alfabetico) e un pulsante tra i
  quattro toggle dell'Action Center (chi aveva i quattro toggle di prima ha il nuovo al posto dell'ultimo; chi li aveva scelti
  a mano non li vede cambiare).
- **Aggiungere a Start**: un **contatto** (con la foto), un **sito web**, un **widget** di Android (ospitato dal
  launcher, con la dimensione che segue il riquadro).
- **Orologio e data** sopra i riquadri; **sfondo di Start** con una tua foto (riquadri trasparenti, parallasse) e
  **colore d'accento dalla foto** (il colore dominante, tra i 20 di Windows Phone).
- **Lista app**: tutte le app di tutti i profili, ordine alfabetico, jump list con la **★ delle app recenti**, **ricerca**
  che ignora maiuscole e accenti e che trova anche **contatti**, **schermate delle impostazioni di Android** e, in
  fondo, una **ricerca sul web** (DuckDuckGo). **Nascondi app** dal menu (si riportano indietro dalle impostazioni).
- Le **36 app stock di Windows Phone 8.1** compaiono accanto all'app vera che ne fa le veci (con il glifo Metro).

### Gesti e navigazione

- **Il dito comanda, sempre.** Action Center, pagine e Indietro seguono il dito uno a uno; le regole di rilascio sono
  funzioni pure con test (`PullRules`). Tirata dal bordo alto da ogni pagina, pagine che ruotano un poco con il dito,
  **Indietro predittivo** (il gesto di sistema muove lo schermo), ritorno alla home senza sfarfallii.
- **Gesti configurabili**: doppio tocco su Start, swipe a due dita in giù e in su; ognuno può bloccare lo schermo, aprire
  le impostazioni o l'Action Center, andare alla lista app, accendere la torcia, aprire la fotocamera, **parlare**,
  cercare o **aprire le app recenti**. **Suono e vibrazione** al tocco, facoltativi.
- **App recenti subito**: l'azione *apri le app recenti* (come gesto, come pulsante tra i toggle dell'Action Center e come
  **tile delle Impostazioni rapide**, che si aggiunge dalla pagina *gesti* e funziona da qualsiasi app) usa lo stesso servizio
  di accessibilità del blocco schermo (`GLOBAL_ACTION_RECENTS`), che non legge nulla e non chiede eventi: si apre al tocco, senza
  gesto da tenere. **Lo swipe di Android** (su e tieni) **non è del launcher**: lo gestisce il sistema, e con un launcher che non
  è quello del telefono può avere un'attesa che da qui non si può togliere (vedi *Note e limiti*).
- **Animazione di apertura**: *turnstile* (i riquadri si girano, l'ultimo quello toccato) oppure **espansione** (Start
  resta fermo e l'app cresce dal riquadro toccato, o dal centro se tocchi dalla lista).

### Action Center e notifiche

Quattro pulsanti rapidi a scelta (sugli altri otto si apre il resto), notifiche che si scartano a destra o tutte insieme,
**risposta rapida** dai messaggi a cui l'app permette di rispondere, **raggruppamento per app** (una riga che si apre; scorrerla via
le scarta tutte). Wi-Fi, Bluetooth, aereo ecc. mostrano lo stato e aprono la schermata di sistema giusta (da Android 10
un'app non può più accenderli da sola); la torcia si accende davvero.

### Voce

Il microfono nella casella di ricerca (o un gesto a scelta) apre una schermata di ascolto in stile Metro. Il
riconoscimento è quello del telefono (`SpeechRecognizer`), con preferenza per il modello **sul telefono** se esiste per la
tua lingua; il launcher capisce i comandi da solo (`data/VoiceCommands.kt`, italiano e inglese, nessuna rete):
*apri Mappe*, *chiama Anna* (apre la scheda del contatto: Android non lascia chiamare a un launcher senza altri permessi),
*accendi / spegni la torcia*, *cerca ricette di pizza* (o una frase qualsiasi, che va alla ricerca web), *meteo*,
*blocca lo schermo*, *centro notifiche*, *tutte le app*, *impostazioni del launcher*, *apri Wi-Fi* (le impostazioni di Android).
Tra le alternative del riconoscitore sceglie la prima che è un comando vero.

### Profili

Profilo di lavoro e **spazio privato** (Android 15): si riconoscono per tipo, hanno ciascuno il proprio badge sull'icona, si
**mettono in pausa e si riattivano** dalle impostazioni, e si può scegliere di mostrare o nascondere le loro app nella lista.

### Blocco schermo Metro

Android non lascia a un launcher la sostituzione della schermata di blocco. Quello che c'è, e funziona senza trucchi:
- la **schermata Metro** (foto, ora grande e leggera in basso, data, prossima sveglia, numero di notifiche, batteria; swipe in
  su per uscire, con il rimbalzo se lasci prima) come **salvaschermo** di Android (*Impostazioni → Schermo → Salvaschermo*),
  che compare mentre il telefono si ricarica o sta su un dock;
- una **anteprima** nel launcher;
- la **foto** del blocco schermo, che si può impostare anche come sfondo della schermata di blocco di Android.

### Temi e colori

Tre aspetti, da *start+theme*, scuri o chiari (o a orario) tutti e tre. Lo stile cambia forme, superfici, tipo di lettere e
controlli (pulsanti, interruttori, campi, cursori), non la struttura: i gesti e la disposizione sono gli stessi.

- **Classic**: il launcher come era, riquadri piatti e quadrati, lettere sottili, interruttore con "On/Off", tilt alla pressione.
- **Modern Glass**: vetro smerigliato **disegnato da zero** su uno sfondo che brilla (macchie di luce nel colore d'accento e
  in due sue variazioni di tonalità): riquadri e pannelli arrotondati, trasparenti, con riflesso in alto a sinistra, ombra
  sotto e bordo luminoso; pulsanti a capsula, interruttore tondo bianco, campi arrotondati. Il vetro è un'illusione
  (velo trasparente, riflesso, bordo): non sfoca davvero quello che sta dietro, perché un launcher non può sfocare altre finestre.
- **Modern Droid**: Material You. I riquadri prendono a turno tre **contenitori tonali** (come le schede di Android), angoli
  grandi, pulsanti a pillola, interruttore con il cursore grande che cresce da acceso. I colori sono quelli dello **sfondo del
  telefono** da Android 12 (`system_accent1…neutral2`, mappati sui toni di Material), oppure, se lo spegni o il telefono è
  più vecchio, costruiti dal colore d'accento scelto: toni che hanno la stessa *luminosità percepita* (L\* di Lab) per ogni tinta, così il
  testo si legge per tutti i 20 colori (lo controlla un test).
- **Colori**: i **20 di Windows Phone**, oppure un **colore tuo** con tre cursori (tonalità, intensità, luminosità) e
  un'anteprima; "torna ai 20 colori" lo toglie. Vale per tutti e tre gli stili. Un colore d'accento dalla foto di Start è
  sempre quello dei 20 e il colore tuo lo spegne; il file tema porta con sé stile e colore tuo.

- **Nero OLED** (*start+theme*, interruttore): per gli schermi OLED, nel tema scuro lo sfondo è nero vero in tutti e tre gli stili, quindi
  quei pixel sono spenti (più contrasto, meno batteria). Classic era già nero: cambiano i menu e le barre, che si fanno più scuri.
  Modern Glass perde il fondale blu e tiene solo un bagliore leggero dei colori; Modern Droid passa dal grigio scuro al nero. Nel tema
  chiaro non fa nulla. Non so dire quanta batteria faccia risparmiare: dipende da quanto schermo è sfondo e non riquadri.

### Icone e temi

**Icon pack** di ADW, Nova, Apex e Go Launcher (si legge l'`appfilter.xml`): sostituiscono le icone delle app per cui
hanno un'icona; le app senza icona nel pack possono essere vestite con il suo sfondo (`iconback`, `iconupon`, `scale`). Le app
Metro del telefono tengono i loro glifi. **File tema**: colori, scuro/chiaro/a orario, orologio, dimensione dei riquadri e icon
pack in un piccolo file JSON da condividere, che applica solo quelle chiavi e non tocca altro.

### Backup e sincronizzazione

- **Backup** del layout (Start, scorciatoie, impostazioni) in un file, o in una **cartella** che scegli con il selettore di
  file di Android (anche una cartella di Google Drive), a mano o ogni 1, 7 o 30 giorni. Quello che è solo di questo telefono
  (cartella, passphrase, posizione del meteo, foto, Angolo bambini acceso) non viaggia.
- **Sincronizzazione tra telefoni** attraverso la stessa cartella: il file `metrowin-sync.bin` è **cifrato** (AES-256-GCM,
  chiave dalla tua passphrase con PBKDF2-SHA256, sale e nonce nuovi a ogni scrittura, intestazione autenticata). La passphrase
  non è mai scritta nella cartella. Un telefono che trova un file la prima volta lo prende e salva ciò che aveva come file di
  backup nella cartella; dopo passa solo ciò che è cambiato; se sono cambiati entrambi vince questo telefono e l'altro resta
  nella cartella come `metrowin-sync-other-<ora>.bin`. Si sincronizza quando il launcher si apre, quando lo lasci e a richiesta.

### Tile pubblicati dalle app

Un'app può mettere un titolo, una riga di testo e un numero sul **proprio** tile, come un live tile di Windows Phone
(vedi [più sotto](#per-chi-sviluppa-unapp-un-tile-tuo-su-start)).

### Orizzontale

Si sceglie in *schermo*: **solo verticale** (come prima), **segui la rotazione del telefono** (rispetta il blocco di Android)
o **solo orizzontale**. In orizzontale Start e le altre pagine tengono fuori dai bordi laterali il ritaglio della fotocamera.

### Accessibilità

- **TalkBack**: ogni riquadro e riga è un pulsante che si legge e si attiva (prima il launcher usava solo il tocco, che
  TalkBack non vede); un riquadro dice quante notifiche ha, una cartella quante app contiene; nel menu azioni di TalkBack
  un riquadro offre **stacca, cambia dimensione, sposta prima, sposta dopo** (cartelle: rinomina, scomponi), cioè
  tutto quello che altrimenti richiede una pressione lunga e un trascinamento. I controlli disattivati si dichiarano tali.
- **Riduci il movimento** (si attiva anche quando Android ha le animazioni a zero): le schermate sfumano invece di girare,
  i riquadri entrano tutti insieme, non si inclinano alla pressione, i live tile non girano e le foto non si alternano da sole.
- **Alto contrasto**: cornice bianca sui riquadri, pieni anche sopra la foto di Start, testo grigio a colore pieno.

### Batteria

Il launcher fa lavorare il telefono il meno possibile. Quello che segue è ciò che il codice fa e che i test controllano;
quanta batteria faccia risparmiare in ore **non è stato misurato** (servirebbe un telefono e una settimana di uso).

- **A riposo non fa nulla.** Quando un'altra app è davanti, o lo schermo è spento, il launcher non ha timer, non ascolta la batteria
  né il risparmio energetico di Android, non segue la torcia, non aggiorna l'orologio. Gli orologi, i giri dei riquadri, il
  controllo dell'ora del tema a orario e l'aggiornamento dell'Action Center sono sospesi dal ciclo di vita dello schermo
  (`LaunchedWhileStarted`, `repeatOnLifecycle(STARTED)`): partono da capo quando si torna, e quello che è cambiato nel frattempo
  si legge una volta sola.
- **Il servizio di blocco non chiede eventi** (prima chiedeva i cambi di finestra, e quindi svegliava il processo a ogni cambio di app).
- **Si legge solo quello che serve**: contatti, calendario, foto e chiamate solo per i live tile fissati su Start, e al massimo
  ogni due minuti; meteo ogni mezz'ora al massimo; la ricerca degli icon pack ogni cinque minuti. Un permesso dato o tolto, o un tile
  nuovo, fanno rileggere subito.
- **Risparmio energetico** (pagina *batteria*): *automatico* (quando c'è il risparmio di Android, o la batteria è al 20% o meno e
  non è in carica), *sempre* o *mai*. Mentre risparmia: i riquadri non girano da soli, le foto non si alternano, la richiesta dei
  120 Hz viene tolta, e tutto si legge meno spesso (meteo 2 ore, dati dei tile 15 minuti, sync quattro volte più rada).

<img src="docs/screenshots/settings_battery.png" alt="La pagina batteria" width="260">

### Angolo bambini

Start ristretto alle app che scegli, senza lista app, Action Center né impostazioni. Per uscire si conferma con il blocco
schermo. Lo stato "acceso" non viaggia nei backup, così un file non può mai accenderlo su un altro telefono.

## Le impostazioni, sezione per sezione

Si aprono dalla voce *Settings* nella lista app. Sono due pagine di un pivot (come in Windows Phone), ogni sezione ha la
sua pagina; tutto è salvato come un unico JSON (`SettingsCodec`) che legge anche le versioni vecchie.

**launcher**

| Sezione | Cosa c'è |
|---|---|
| start+theme | **tema** (Classic, Modern Glass, Modern Droid) e, per Droid, i colori dello sfondo; scuro / chiaro / come il telefono / a orario; colore d'accento (20), **colore tuo con i cursori** o dalla foto; foto di Start e parallasse; più riquadri; numero di schermate Start; orologio |
| schermo | rotazione (verticale, segui il telefono, orizzontale); animazione di apertura; massima frequenza di aggiornamento (120 Hz, tolta mentre si risparmia batteria) |
| batteria | risparmio energetico (automatico, sempre, mai) e cosa cambia; cosa il launcher non fa quando non è sullo schermo |
| accessibilità | riduci il movimento; alto contrasto; TalkBack e scorciatoia alle impostazioni di Android |
| icone e temi | icon pack; esporta e importa un file tema |
| gesti | tirata dal bordo, indietro dal bordo, doppio tocco, due dita in giù e in su (anche *app recenti*), suono e vibrazione, servizio di accessibilità (blocco schermo e app recenti) |
| notifiche+azioni | i quattro pulsanti dell'Action Center; accesso alle notifiche; mostra le notifiche sui riquadri; risposta rapida; raggruppamento |
| live tile | città del meteo (ricerca o posizione) e gradi; lascia pubblicare i tile alle app, ed elenco di chi l'ha fatto |
| app | cosa cerca la ricerca (contatti, impostazioni, web); app nascoste |

**tools**

| Sezione | Cosa c'è |
|---|---|
| blocco schermo | anteprima, foto, cosa mostrare, foto nella schermata di blocco di Android, impostazioni del salvaschermo |
| voce | microfono nella ricerca; riconoscimento sul telefono; prova; cosa si può dire |
| profili | lavoro e spazio privato: pausa e riattiva; mostra le loro app |
| backup | file o cartella, ogni quanto, ripristina; sincronizzazione con passphrase; esporta e importa |
| angolo bambini | app e avvio |
| informazioni | versione e dettagli del launcher |

## Cosa non si può fare da un launcher, e come l'ho aggirato

| Funzione di Windows Phone | Stato |
|---|---|
| Schermata di blocco al posto di quella di Android | Non si può: la sostituisce solo il sistema. C'è come **salvaschermo** e come foto del blocco (vedi sopra) |
| Accendere Wi-Fi / Bluetooth / aereo dall'Action Center | Android non lo permette più alle app: i pulsanti aprono la schermata giusta |
| Chiamare un contatto a voce | Si apre la sua scheda: chiamare direttamente servirebbe un altro permesso |
| Cortana, Word Flow (tastiera), Driving Mode, Wi-Fi Sense, task switcher | Funzioni di sistema o app a parte |
| Start e lista app affiancati in orizzontale | Non fatto: in orizzontale sono due pagine come in verticale, con più colonne |
| Ritaglio con pizzico della foto di sfondo | Non fatto: la foto si centra da sola |
| Sincronizzare la foto di Start e quella del blocco | Non fatto: le foto sono file di questo telefono |

## Permessi

Il launcher funziona senza nessun permesso facoltativo. Li chiede il [tutorial](#il-tutorial-iniziale), uno per pagina, e
si possono dare anche dopo, quando serve la funzione.

| Permesso | Serve a |
|---|---|
| `QUERY_ALL_PACKAGES` | vedere tutte le app. Google Play ne limita l'uso e chiede una giustificazione: da verificare se pubblichi l'app |
| `READ_MEDIA_IMAGES` (+ `READ_MEDIA_VISUAL_USER_SELECTED` su Android 14, `READ_EXTERNAL_STORAGE` fino ad Android 12L) | riquadro Foto |
| `READ_CALENDAR`, `READ_CONTACTS`, `READ_CALL_LOG` | riquadri Calendario, Persone (e ricerca contatti), Telefono |
| `INTERNET` | il meteo (solo Open-Meteo, solo https, risposta limitata a 256 KB) e la ricerca di una città |
| `ACCESS_COARSE_LOCATION` | "usa la mia posizione" del meteo (l'ultima nota, una volta) |
| `RECORD_AUDIO` | la voce, quando la usi la prima volta |
| `SET_WALLPAPER` | la foto nella schermata di blocco di Android, quando lo chiedi |
| `ACCESS_WIFI_STATE`, `REQUEST_DELETE_PACKAGES` | stato del Wi-Fi, *disinstalla* nel menu |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | solo per chiedere, nel tutorial, che Android non chiuda il launcher in background. **Google Play lo consente solo ad alcuni tipi di app**: per una build non pubblicata sullo store va bene, per pubblicarla va giustificato o tolto (basta rimuovere la pagina "tienilo attivo") |
| `com.metrowin.launcher.permission.PUBLISH_TILE` | **dichiarato dal launcher**, lo chiedono le app che vogliono un tile (vedi sotto) |
| Accesso alle notifiche (nelle impostazioni di Android) | Action Center, contatori, risposta rapida, raggruppamento |
| Servizio di accessibilità del launcher (facoltativo) | solo per **bloccare lo schermo** con un gesto; non legge nulla dello schermo |
| Cartella scelta con il selettore di file | backup e sincronizzazione: il launcher raggiunge solo quella cartella |

Il backup automatico di Android è disattivato (`allowBackup=false`, e regole esplicite per Android 12): le impostazioni
contengono la passphrase della sincronizzazione.

## Per chi sviluppa un'app: un tile tuo su Start

L'app **A** può scrivere sul **proprio** riquadro (mai su quello di un'altra: il launcher guarda chi chiama, qualsiasi
cosa dica la chiamata). Nel manifest di A:

```xml
<uses-permission android:name="com.metrowin.launcher.permission.PUBLISH_TILE" />
<queries><provider android:authorities="com.metrowin.launcher.tiles" /></queries>   <!-- Android 11+ -->
```

Nel codice:

```kotlin
val tiles = Uri.parse("content://com.metrowin.launcher.tiles")
val extras = Bundle().apply {
    putString("title", "Posta")               // al massimo 60 caratteri, una riga
    putString("text", "3 nuovi messaggi")     // al massimo 160
    putInt("count", 3)                        // il numero sul riquadro, 0..99999
}
val answer = context.contentResolver.call(tiles, "publish", null, extras)
// answer?.getBoolean("accepted"); se è false, answer?.getString("reason")
context.contentResolver.call(tiles, "clear", null, null)      // toglie il testo
```

Il testo compare sul riquadro (che gira per mostrarlo), il numero come contatore. L'utente può spegnere la cosa (*live tile → tile
pubblicati dalle app*) e cancellare quello di un'app. Il permesso è di livello `normal`: Android lo concede all'installazione, ma
solo se il launcher c'era già.

## Test

```sh
./gradlew testDebugUnitTest lintDebug
./gradlew assembleRelease        # con R8
```

**628 test**, nessuno saltato; il lint non ha errori.

- Logica pura: griglia e colonne dalla larghezza, operazioni su Start, regole di trascinamento e di rilascio, ricerca,
  salvataggio e migrazione delle impostazioni, comandi vocali, pianificazione della sincronizzazione, **cifratura** (anche
  file alterati, passphrase sbagliata, numero di iterazioni ostile), tema, icon pack (`appfilter.xml`), tile pubblicati,
  raggruppamento delle notifiche, piano dell'animazione di apertura, regole di accessibilità.
- Interfaccia su JVM (Robolectric con grafica nativa, `app/src/test/.../screens`): disegna le schermate vere, esegue
  **gesti veri** e controlla i pixel; salva ogni schermata in `app/build/screenshots/*.png`. Esempi: tile in orizzontale con
  le dimensioni giuste, risposta rapida che arriva all'app come risultato del `RemoteInput` (con un `PendingIntent` vero),
  swipe del blocco schermo con rimbalzo, azioni di TalkBack che fanno quello che dicono.

I test hanno trovato anche difetti veri, poi corretti: la semantica dei riquadri stava prima dello `offset`, quindi per un
servizio di accessibilità stavano tutti nell'angolo della griglia; i pulsanti usavano solo il tocco e non erano attivabili da TalkBack.

La prima esecuzione scarica da Maven le librerie di Robolectric (alcune centinaia di MB).

## Struttura

```
app/src/main/java/com/metrowin/launcher/
  MainActivity.kt, LauncherApp.kt, LauncherViewModel.kt   radice, pagine e overlay, stato, rotazione, 120 Hz
  data/           applicazioni e profili (AppRepository, Profiles), Start (StartEntries, StartPages, TileGrid, TileStore),
                  impostazioni (SettingsStore, SettingsCodec, ThemeCodec, LayoutBackup), live tile (LiveTiles, Weather*,
                  BatteryInfo), voce (VoiceCommands, VoiceRecognizer), sync (SyncCrypto, SyncEngine, SafFolder),
                  icone (IconLoader, IconPacks), tile di terzi (ThirdPartyTiles, TilePublishProvider), ricerca,
                  scorciatoie, LaunchPlan, LockInfo, Tutorial (passi, bisogni, controllo di cosa è già concesso)
  notifications/  NotificationService, NotificationHub (anche risposta), NotificationGroups
  model/          Tile, Folder, TileSize, AppItem, Glyph
  gestures/       servizio per bloccare lo schermo
  ui/             start/, apps/, actioncenter/, settings/ (una pagina per sezione), voice/, lock/ (schermata e salvaschermo),
                  weather/, kids/, tutorial/ (giro iniziale e richieste), anim/, gesture/, components/, theme/ (compreso Accessibility)
```

## Note e limiti

- **Non provato su un telefono.** La verifica è stata fatta compilando (debug e release con R8), con i test e con le schermate
  renderizzate su JVM. Vanno guardati su un dispositivo, e l'ho scritto qui perché non siano una sorpresa:
  - la **sensazione** dei gesti e dei 120 Hz (i test controllano posizioni, soglie e che nulla lotti con il dito, non la
    fluidità), e il progresso di Indietro che dà ogni produttore;
  - il **riconoscimento vocale** vero (microfono, modello offline, lingue), il selettore dei contatti e l'apertura delle schede;
  - i **widget** (aggancio, schermata di configurazione, dimensioni), i **profili** (lavoro, spazio privato: sblocco, pausa);
  - la **cartella di sincronizzazione** su un cloud vero (Drive) e la **sincronizzazione tra due telefoni**: la logica e la
    cifratura sono provate con una cartella in memoria, non con il selettore di Android;
  - il **salvaschermo** (il servizio è creato nei test, ma la finestra la crea Android) e la foto nella schermata di blocco;
  - gli **icon pack** reali (il formato è quello condiviso da ADW, Nova, Apex e Go, ma ogni pack è a sé);
  - la **risposta rapida** con le app vere (la consegna è provata con un `PendingIntent` vero, non con ogni app di messaggi);
  - **TalkBack** vero (le proprietà e le azioni sono provate, non il parlato) e l'**espansione** dell'app dal riquadro;
  - le chiamate di rete del **meteo** (Open-Meteo), il servizio di **notifiche**, lo screen pinning e il servizio per bloccare lo schermo.
- **Il tutorial su un telefono vero**: le richieste (dialoghi dei permessi, dialogo dell'app Home, pagine di Android per notifiche,
  accessibilità e batteria) sono collegate ma provate solo fin dove si arriva su JVM: i pulsanti chiamano la richiesta giusta e la
  pagina legge cosa è concesso; cosa mostra davvero Android, e se un produttore ha un'altra pagina per la batteria, va visto.
- **Lo swipe delle app recenti di Android** (su e tieni) lo gestisce il sistema, non il launcher: i gesti e la schermata delle
  Recenti stanno nel launcher di fabbrica, che con uno di terze parti ha meno privilegi. Su **Samsung** l'attesa è voluta: con un
  launcher di terze parti bisogna tenere lo swipe circa un secondo ([lo dice Niagara Launcher](https://help.niagaralauncher.app/article/114-recent-apps-screen-issues),
  che ha lo stesso problema), e altri produttori hanno ritardi simili ([Lawnchair](https://docs.lawnchair.app/troubleshooting/gesture-navigation-issues)); su un **vivo** con un launcher di terze parti c'è chi riferisce Recenti lente ([una segnalazione a Niagara](https://niagaralauncher.featurebase.app/p/recent-apps-navigation-is-slow-and-lagging)), e un riavvio la fa sparire per qualche ora. Con il launcher di fabbrica il ritardo non c'è perché è lui a gestire i gesti.
  Non è nel codice di questo launcher e **non si può togliere da qui**; non l'ho misurato su un telefono. Il launcher non fa nulla
  di suo quando parte il gesto (a quel punto è già fermo, vedi *Batteria*). Per averle subito ci sono l'azione *app recenti* (gesto,
  pulsante o tile, richiedono il servizio di accessibilità del blocco schermo) e la navigazione a 3 pulsanti di Android, dove il
  pulsante Recenti non ha attese (lo riferiscono utenti di launcher di terze parti; non l'ho provato).
- **Font**: Windows Phone usa Segoe WP, proprietario. Qui c'è Open Sans (OFL). Vedi [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Colori d'accento**: Microsoft ha pubblicato i 20 colori solo come immagine; i valori vengono da trascrizioni.
- **Marchi**: i nomi delle app stock (Internet Explorer, Xbox, Skype…) sono quelli di Windows Phone. Stanno in `MetroCatalog.kt`
  e in `strings.xml`, e si cambiano in un punto solo se vuoi pubblicare l'app.
- **Passphrase**: sta nelle preferenze private dell'app, in chiaro, e non esce dal telefono; non viene salvata nel backup di Android.
- Versioni: AGP 8.13, Kotlin 2.3, Compose BOM 2025.10. Le librerie AndroidX più recenti chiedono AGP 9 e compileSdk 37.

## Fonti

Le funzioni di Windows Phone sono state cercate qui:
[Windows Phone 8.1](https://en.wikipedia.org/wiki/Windows_Phone_8.1),
[Windows Phone 8](https://en.wikipedia.org/wiki/Windows_Phone_8) e
[Windows Phone](https://en.wikipedia.org/wiki/Windows_Phone) (Wikipedia);
[sfondi di Start](https://blogs.windows.com/devices/2014/04/29/deep-dive-windows-phone-8-1-start-screen-backgrounds/) e
[cartelle](https://blogs.windows.com/windowsexperience/2014/08/05/windows-phone-folders-a-new-way-to-organize-your-start-screen/)
(blog di Microsoft); [Action Center e Quiet hours](https://m.gsmarena.com/windows_phone_81_review-1068p2.php) (GSMArena) e
[Engadget](https://www.engadget.com/2014-04-14-windows-phone-8-1.html);
[Kid's Corner](https://www.windowscentral.com/kids-corner-windows-phone-8),
[Apps Corner](https://www.windowscentral.com/how-to-set-up-apps-corner) (Windows Central);
[temi e colori](https://learn.microsoft.com/it-it/previous-versions/windows/apps/ff402557(v=vs.105)) e
[modelli di live tile](https://learn.microsoft.com/it-it/previous-versions/windows/apps/jj662929(v=vs.105)) (Microsoft Learn);
[i 20 colori](https://marcofranssen.nl/windows-phone-theme-colors).
