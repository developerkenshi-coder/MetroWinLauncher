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

**Per usarlo tutti i giorni, meglio la build ottimizzata**: è la release (R8, nessun codice di debug, profilo per la compilazione
anticipata di tutto il codice del launcher in `app/src/main/baseline-prof.txt`) firmata con la chiave di debug, quindi si installa sopra
la debug senza perdere i dati. La debug è più lenta e consuma di più: è fatta per sviluppare.

```sh
./gradlew assembleOptimized      # app/build/outputs/apk/optimized/app-optimized.apk
adb install -r app/build/outputs/apk/optimized/app-optimized.apk
```

La `assembleRelease` è la build **ufficiale**: la firma con la chiave descritta in `keystore.properties` (nella radice del progetto, **non nel
repository**: è in `.gitignore`, come la cartella `keystore/`), con le righe `storeFile`, `storePassword`, `keyAlias` e `keyPassword`.
Senza quel file esce non firmata. **Chi perde la chiave non può più aggiornare l'app già installata**: tienine una copia, e la password
a parte. La chiave è diversa da quella di debug, quindi un'installazione di debug va disinstallata prima di mettere quella ufficiale. Il profilo di compilazione lo applica Android da solo, di solito
nella prima notte di manutenzione (a telefono in carica e spento): prima l'avvio è più lento.

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
"apri impostazioni", che porta alla pagina dell'app. Il giro si rivede da *impostazioni → strumenti → informazioni → rivedi il tutorial* (o cercando "tutorial" nella casella delle impostazioni).

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
- **Lista app**: tutte le app di tutti i profili, ordine alfabetico, jump list con la **★ delle app recenti**, **ricerca
  indicizzata** (vedi sotto) che trova app, impostazioni del launcher, ciò che c'è su Start, contatti, impostazioni di Android,
  eventi e, in fondo, una **ricerca sul web** (DuckDuckGo). **Nascondi app** dal menu (si riportano indietro dalle impostazioni).
- Le **36 app stock di Windows Phone 8.1** compaiono accanto all'app vera che ne fa le veci (con il glifo Metro).

### La ricerca

La casella in cima alla lista app non scorre più i nomi uno per uno: le app (e le impostazioni, e i tile di Start) sono in un
**indice** costruito una volta (`data/SearchIndex.kt`), un vocabolario ordinato delle parole che rimanda a chi le ha, che si
rifà solo quando cambiano le app o i conteggi di apertura. Cosa trova, dal meglio al peggio:

1. la **parola uguale**, poi una che **comincia** così, poi le **iniziali** dei nomi (`gm` trova *Google Maps*, `ytm` *YouTube Music*:
   conta anche la maiuscola in mezzo), poi una parola che ce l'ha **dentro**;
2. **più parole, in qualunque ordine**: `google ma` trova *Google Maps* e non ogni app di Google, `maps google` pure;
3. le **parole che dicono a cosa serve** un'app, in italiano e in inglese (`email` trova *Posta*, `navigatore` *Mappe*, `chiamate` *Telefono*), le
   parole del **pacchetto** (`chrome` trova l'app a cui *Internet* fa da stock; `google` le app di Google), la **categoria** e il **profilo**
   (`lavoro`). Contano meno del nome, e da quattro lettere in su (con `cal` non esce il *Telefono* per *call*);
4. a parità, le app **che apri più spesso** vengono prima (il conteggio resta sul telefono; non scavalca mai una corrispondenza migliore);
5. se **niente** corrisponde, si perdonano gli **errori di battitura** (`calnedar` trova *Calendario*, `parallx` trova *parallasse*).

Oltre alle app, la ricerca globale elenca (ognuno si spegne nella pagina *ricerca* delle impostazioni): le **impostazioni del
launcher** (si trovano per nome, per ciò a cui servono o per sezione, e un tocco apre la pagina **scorrendo fino a quella impostazione e
illuminandola**), ciò che c'è **su Start** (le parole delle note e delle liste, i conti alla rovescia, i siti; i contatori e gli orologi no: un
tocco li cambierebbe), i contatti, le impostazioni di Android (anche in italiano), gli eventi e il web. Le risposte (calcoli, conversioni, timer…) restano in cima.

### Gesti e navigazione

- **Il dito comanda, sempre.** Action Center, pagine e Indietro seguono il dito uno a uno; le regole di rilascio sono
  funzioni pure con test (`PullRules`). Tirata dal bordo alto da ogni pagina, pagine che ruotano un poco con il dito,
  **Indietro predittivo** (il gesto di sistema muove lo schermo), ritorno alla home senza sfarfallii.
- **Gesti configurabili**: doppio tocco su Start, swipe a due dita in giù e in su; ognuno può bloccare lo schermo, aprire
  le impostazioni o l'Action Center, andare alla lista app, accendere la torcia, aprire la fotocamera, **parlare**,
  cercare o **aprire le app recenti**. **Suono e vibrazione** al tocco, facoltativi (un click e una vibrazione del launcher, che non dipendono dagli interruttori di Android).
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
le scarta tutte). Il **banner Metro** compare anche **sopra le altre app** quando il launcher non è sullo schermo, se lo accendi
(*notifiche+azioni → banner sopra le altre app*, spento di base) e consenti «Mostra sopra altre app»: non sul blocco schermo, non per le notifiche
silenziose, non quando Non disturbare le blocca, e per le app che scegli con l'interruttore *banner* di ognuna. Il banner di Android compare comunque, a meno che accenda *nascondi la notifica di Android* (spento di base): quando compare il banner Metro, la notifica
di Android viene tolta e resta nell'Action Center finché non la scarti (Android la mostra per un istante e il suono è dell'app; l'app viene avvisata che
è stata scartata). Il banner si mostra a ogni nuovo messaggio di una chat, non solo al primo, e la riga di una chat è il suo ultimo messaggio.

L'**Action Center sopra le altre app** (*notifiche+azioni*, spento di base; usa il servizio di blocco schermo dell'accessibilità, perché solo le finestre di un servizio di accessibilità possono stare sopra la barra di stato, dove deve cominciare lo swipe): uno swipe verso il basso dal bordo più alto
dello schermo, su una striscia sottile di 20 dp, apre lo stesso Action Center del launcher (pulsanti, cursori, notifiche, risposta rapida,
posticipa) in una finestra sopra l'app in cui sei. Un tocco sulla striscia non fa niente, e il pannello di Android resta raggiungibile da
subito sotto. La striscia è una finestra che non disegna e non sveglia niente; non c'è sul
blocco schermo né mentre il pannello è aperto, e funziona anche dentro il launcher (è più bassa della barra di stato). Il pulsante *impostazioni del launcher* apre il launcher. Wi-Fi, Bluetooth, aereo ecc. mostrano lo stato e aprono la schermata di sistema giusta (da Android 10
un'app non può più accenderli da sola); la torcia si accende davvero.

### Voce

Il microfono nella casella di ricerca (o un gesto a scelta) apre una schermata di ascolto in stile Metro. Il
riconoscimento è quello del telefono (`SpeechRecognizer`), con preferenza per il modello **sul telefono** se esiste per la
tua lingua; il launcher capisce i comandi da solo (`data/VoiceCommands.kt`, italiano e inglese, nessuna rete):
*apri Mappe*, *chiama Anna* (trova il numero e apre il tastierino con il numero già scritto, a un tocco dalla chiamata; con l'interruttore
*chiama subito* e il permesso del telefono, che Android chiede la prima volta, la chiamata parte da sola), *timer 10 minuti*, *sveglia alle 7 e 30*,
*accendi / spegni la torcia*, *cerca ricette di pizza* (o una frase qualsiasi, che va alla ricerca web), *meteo*,
*blocca lo schermo*, *centro notifiche*, *tutte le app*, *impostazioni del launcher*, *apri Wi-Fi* (le impostazioni di Android);
e le frasi che la casella di ricerca capisce (vedi *terzo giro*): *ricordami di chiamare Anna domani alle 18*, *nota comprare il latte*,
*evento cena venerdì alle 20*, *farmacia vicino a me*, *scansiona qr*. Un verbo di ricerca (*cerca…*, *search for…*) vince sempre: *search for best pizza near me* va al web.
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
- con lo **stato rapido** e la **musica** (vedi *terzo giro*) il layout si adatta: su uno schermo alto la musica sta in alto e il resto dal basso; su uno schermo largo e basso
  (il telefono su un dock) l'ora sta a sinistra e musica e stato a destra; quando le righe non entrano (testo grande, tutte le opzioni accese) si disegnano più piccole, fino al 60%, invece di sovrapporsi;
- la **foto** del blocco schermo, che si può impostare anche come sfondo della schermata di blocco di Android.

### Temi e colori

Tre aspetti, da *start+theme*, scuri o chiari (o a orario) tutti e tre. Lo stile cambia forme, superfici, tipo di lettere e
controlli (pulsanti, interruttori, campi, cursori), non la struttura: i gesti e la disposizione sono gli stessi.

- **Classic**: il launcher come era, riquadri piatti e quadrati, lettere sottili, interruttore con "On/Off" (il cursore ha lo stesso margine su ogni lato del binario, da spento e da acceso; le due parole occupano sempre lo stesso spazio, così l'etichetta non va a capo in modo diverso quando lo giri; TalkBack lo legge come un interruttore e lo può girare senza toccare lo schermo), tilt alla pressione.
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

### Live tile delle altre app

Con l'accesso alle notifiche e l'interruttore *contenuti dal vivo delle altre app* (pagina *live tile*, spento finché non lo
accendi: metterebbe il testo dei messaggi su Start), il riquadro di **WhatsApp, Telegram, Instagram, Reddit** o di qualsiasi altra
app si gira per mostrare cosa succede, **leggendo le sue notifiche** (nessuna rete, nessun permesso in più; non uso le API delle
app, che non ci sono):

- **Conversazioni**: una faccia per ogni chat, con chi ha scritto, l'ultima riga, il nome del gruppo e **la foto di chi scrive**
  (i messaggi sono quelli che l'app scrive nella notifica a conversazione, `MessagingStyle`). Il contatore somma i messaggi uno per
  uno: una chat con cinque in attesa fa 5, non 1.
- **Altre notifiche** (un mi piace su Instagram, una risposta su Reddit, un'email): titolo e riga, con la foto di chi le manda se c'è.
- **Immagini**: una notifica con un'immagine (`BigPicture`, o una foto in chat) riempie il riquadro, con le sue parole sotto.
- **Musica che suona**: titolo, artista e copertina dalla sessione multimediale dell'app (Spotify, YouTube Music, qualsiasi
  lettore), con un triangolo se suona e due barre se è in pausa; compare anche sul riquadro **Musica**, qualunque app suoni.
- **Avanzamento**: un download, un upload, una sincronizzazione, con la percentuale e una barra.
- **In corso adesso**: una navigazione, una chiamata, un cronometro, con un puntino che dice che è dal vivo.
- Cosa mostrare e chi no, nella pagina *live tile*: conversazioni, immagini, musica, avanzamento, **nascondi cosa c'è scritto**
  (resta solo chi ha scritto), e un interruttore **per ogni app** che ha qualcosa da mostrare. Ogni riquadro ha al massimo cinque
  facce; i riquadri piccoli mostrano solo il contatore.
- Costo: il servizio che già legge le notifiche le legge in dettaglio solo quando l'interruttore è acceso, e decodifica le foto
  una volta sola (copie piccole: 96 px, 192 px, 320 px). Il resto del lavoro è sulle regole in `notifications/LiveCards.kt`, che sono
  funzioni pure con test.

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

### Altre trenta funzioni

Tutte si spengono o si scelgono nelle impostazioni (pagine *lista app*, *ricerca*, *gesti*, *notifiche+azioni*, *schermata start*, *accessibilità*); quelle che toccano
dati dell'app (conteggi, ricerche recenti) restano sul telefono e **non entrano nei backup**.

**Lista app**
1. **Scorciatoie delle app**: tenendo premuta un'app, il menu elenca quelle che l'app offre (*nuovo messaggio*, *scheda in incognito*…).
   Android le dà **solo al launcher che è l'app Home del telefono**: finché non lo è, il menu non ne ha (non si rompe niente).
2. **Più usate**: un gruppo in cima alla lista con le (al massimo quattro) app aperte dal launcher almeno tre volte.
3. **Nuove**: le app installate nell'ultima settimana (al massimo tre). Subito dopo aver configurato o ripristinato un telefono non
   se ne indica nessuna, perché sarebbero tutte.
4. **Categorie**: pastiglie sotto la casella di ricerca che mostrano solo le app di un tipo (giochi, social, musica…). Ci sono solo le
   app che **dichiarano** cosa sono: molte non lo fanno e restano fuori dai filtri.
5. **Rinomina**: dal menu di un'app; il nome nuovo vale ovunque (lista, riquadri, ricerca, voce). *Nome originale* lo toglie. Il menu ha anche
   *informazioni sull'app* (la schermata di Android).
6. **Blocco app**: un'app può chiedere il blocco schermo (PIN, sequenza, impronta) prima di aprirsi **dal launcher** (lista, riquadri,
   ricerca, voce). Non è una cassaforte: l'app si apre ancora da una notifica, dalle recenti o da un'altra app. Serve un blocco schermo
   impostato in Android.

**Ricerca**
7. **Calcolatrice**: `12*3+4`, `(2+3)^2`, `15% di 80`, `100+10%`, `radice(16)`, la virgola decimale. Tocchi la risposta e il risultato si copia.
8. **Conversioni**: `5 km in miglia`, `30 c in f`, `70 kg in lb`, `100 km/h in mph`, `2 gb in mb`: lunghezze, pesi, temperature, volumi, velocità,
   dati (a 1000, come le impostazioni di Android) e tempo, in italiano e in inglese. Le valute stanno a parte e sono spente (vedi la 43).
9. **Numeri, link, indirizzi mail**: un numero offre *chiama* e *scrivi*, un indirizzo web *apri*, una mail *scrivi a*.
10. **Motore di ricerca web**: DuckDuckGo (predefinito), Google, Bing, Brave, Startpage, Ecosia o Qwant. Si apre nel browser solo quando tocchi la riga.
11. **Ricerche recenti**: le ultime sei compaiono toccando la casella, con *cancella*.
12. **Timer e sveglie**: `timer 10 minuti`, `timer 1h30`, `sveglia alle 7 e 30`, `sveglia 7:30`, anche a voce. Parte nell'app Orologio del telefono.

**Start**
13. **Colore del riquadro**: il pulsante sul riquadro selezionato apre i 20 colori (o il colore del tema).
14. **Annulla**: dopo aver tolto, ridimensionato, colorato o spostato un riquadro, o cambiato una cartella, una barra per sei secondi offre di
    annullare l'ultima modifica (un riquadro tolto con la sua nota o il suo numero torna com'era; un widget no: libera subito il suo posto).
15. **Impostazioni di Android su Start**: dal **+** della barra di modifica, ad esempio *Wi-Fi* o *Batteria*, come riquadri.
16. **Chiamata rapida**: dal **+**, un numero scelto dai contatti (con la sua foto) che si chiama con un tocco. Senza il permesso del telefono apre il tastierino.
17. **Nota**: un riquadro su cui scrivi; il testo si vede sulla faccia, un tocco lo modifica.
18. **Conto alla rovescia**: un nome e una data; il riquadro dice i giorni che mancano (e li conta in su dopo la data).

**Live tile di sistema**
19. **Spazio**: il riquadro *Storage Sense* dice quanto è libero e quanto è pieno il telefono.
20. **Sveglia**: il riquadro *Sveglie* dice quando suona la prossima.
21. **Dettagli del meteo**: temperatura percepita, vento, umidità, probabilità di pioggia, alba, tramonto e indice UV, su una terza faccia del
    riquadro e nella pagina del meteo, con la probabilità di pioggia di ogni giorno.

**Gesti e Action Center**
22. **Altre azioni per i gesti**: screenshot, menu di spegnimento, schermo diviso, e le notifiche e le impostazioni rapide **di Android** (tramite il
    servizio di accessibilità che già blocca lo schermo). C'è anche il pulsante *Screenshot* tra quelli dell'Action Center.
23. **Un'app su un gesto**: doppio tocco e i due swipe a due dita possono aprire l'app che scegli.
24. **Volume e suoneria** nell'Action Center: cursore del volume dei contenuti e i pulsanti suoneria, vibrazione, silenzioso (il silenzioso chiede l'accesso a
    *non disturbare*, e senza porta alla schermata che lo dà).
25. **Luminosità**: un cursore, con l'accesso *modifica le impostazioni di sistema* che dai in una schermata di Android (finché non c'è, il cursore lo dice).
26. **Tocca l'orologio**: l'ora di Start apre l'orologio, la data il calendario.

**Aspetto**
27. **Dimensione del testo**: dall'85% al 130% per tutto il launcher.
28. **Orologio**: ore a 12 o 24 a scelta, prossima sveglia sotto la data, numero della settimana.
29. **Barra di stato nascosta** su Start. Con la barra nascosta lo swipe dal bordo alto è del sistema (la fa riapparire): l'Action Center si apre con
    un gesto della pagina gesti.
30. **Inquadratura della foto di Start**: zoom da 100% a 300% e due cursori per il punto che resta in vista, con anteprima.

### Altre trenta funzioni, secondo giro

Anche queste si spengono o si scelgono nelle impostazioni. Contatori, liste, note e scelte restano sul telefono; i riquadri nuovi
entrano nel backup del layout (la lista, il contatore e le città sì, i compleanni vengono letti dai contatti e non si salvano).

**Start**
31. **Nomi sotto i riquadri** a scelta: spenti, restano solo le immagini.
32. **Ripristina Start**: rimette i riquadri dell'installazione e toglie quelli che hai aggiunto (contatti, siti, note, widget…). Due tocchi, e *annulla* li riporta
    tutti tranne i widget. Il numero di schermate resta quello che hai scelto.
33. **Tieni premuto sul vuoto di Start** e si apre il menu *aggiungi*, senza dover selezionare prima un riquadro. Il menu ora scorre.
34. **Scorciatoie delle app come riquadri**: nel menu di un'app, il **+** accanto a una scorciatoia (*nuovo messaggio*…) la mette su Start con l'icona dell'app.
    Vale solo se il launcher è l'app Home (stessa regola della funzione 1) e il riquadro parte solo da lì.
35. **Contatore**: un riquadro con un numero; un tocco aggiunge uno, il **−** toglie uno, la matita cambia nome e lo azzera. Mai sotto zero.
36. **Lista**: spunte e cancellazioni dentro una finestra, il riquadro dice *2 di 5* e le prime cose ancora da fare. Fino a 40 voci.
37. **Ora in un'altra città**: 58 città, cercate in italiano o in inglese; il riquadro dice l'ora (e *domani* / *ieri* quando il giorno è diverso).
38. **Fase della luna**: un disco con la parte illuminata, il nome e la percentuale. È calcolata dal mese lunare, senza rete; sbaglia al massimo di un giorno
    attorno alle fasi.
39. **Compleanni**: dai contatti (serve `READ_CONTACTS`, chiesto una volta quando metti il riquadro): il più vicino in grande, i prossimi sull'altra faccia, con gli anni che compie quando
    il contatto ha l'anno. Il 29 febbraio si festeggia il 28 negli anni senza.
40. **Spaziatura dei riquadri**: stretta, normale o larga.

**Lista app e ricerca**
41. **Elenco compatto**: quadrati più piccoli e righe più strette.
42. **Apri nello store** e **condividi il link** dal menu di un'app (la pagina nello store del telefono, o sul web se non c'è).
43. **Valute** (spento): `100 usd in eur`, `50 dollari in euro`, `€10 in yen`. I tassi sono quelli della Banca centrale europea tramite `api.frankfurter.dev`: **una**
    richiesta ogni sei ore al massimo, solo https, che non porta con sé niente di ciò che scrivi; senza rete valgono gli ultimi tassi, con la loro data.
44. **Ora nel mondo**: `ora a Tokyo`, `che ore sono a New York`, `time in london`.
45. **Date**: `tra 30 giorni`, `in 2 weeks`, `45 giorni fa`, `giorni al 25 dicembre`, `days until christmas`, e Natale, Capodanno, Pasqua (calcolata), Ferragosto…
46. **Eventi del calendario** (spento, `READ_CALENDAR`): gli eventi dei prossimi sei mesi il cui titolo corrisponde. Si apre l'evento nel calendario.
47. **Dado e moneta**: `dado`, `2d6+3`, `d20`, `lancia una moneta`, `testa o croce`. Un tocco rilancia.

**Voce**
48. **Volume e luminosità**: `alza il volume`, `volume al 50 per cento`, `luminosità al massimo`. La luminosità chiede lo stesso accesso del cursore.
49. **Suoneria**: `modalità silenziosa`, `vibrazione`, `attiva la suoneria` (il silenzioso chiede l'accesso a *non disturbare*).
50. **Naviga** e **messaggio**: `portami a Roma` apre le indicazioni nell'app di mappe; `scrivi a Marco ci vediamo alle otto` apre l'app messaggi con il testo scritto: **non lo
    invia**, lo invii tu. Per un nome di due parole si dice *dicendo*: `scrivi a Maria Rossi dicendo ciao`.
    Funzioni di sistema a voce: `screenshot`, `menu di spegnimento`, `app recenti`, `schermo diviso` (tramite il servizio di accessibilità).

**Action Center e notifiche**
51. **Ore silenziose subito**: con l'accesso a *non disturbare* il pulsante le accende e spegne sul posto (prima apriva solo la schermata). Senza accesso apre la schermata che lo dà.
52. **Posticipa una notifica**: tenendola premuta compaiono 15 minuti, 1 ora, 3 ore, le 8:00; Android la riporta da solo.
53. **Impostazioni di notifica dell'app**: dalla stessa pressione lunga.
54. **Intensità della torcia**: un cursore sotto i pulsanti, solo con la torcia accesa e solo sui telefoni che lo permettono (Android 13 e successivi, se l'hardware ha più livelli).

**Aspetto e sistema**
55. **Testo marcato**: tutti i pesi di tre passi più pesanti (come il testo in grassetto di Android, che lo accende da solo).
56. **Carattere del telefono** al posto di quello del tema.
57. **Scuro dal tramonto all'alba**: segue alba e tramonto del meteo (la città del riquadro; il meteo deve essersi caricato una volta, altrimenti valgono le ore).
58. **Lingua del launcher** (Android 13 e successivi): inglese o italiano a prescindere dal telefono. Compare anche nelle impostazioni di Android, per app.
59. **Schermata di blocco** con il meteo e il prossimo evento (nelle prossime 24 ore). Il meteo è quello già salvato, il blocco non scarica nulla. L'evento è **spento** di
    base: chiunque veda lo schermo lo legge.
60. **Home premuto due volte** fa quello che scegli nei gesti (come gli altri gesti, anche un'app).

### Altre trenta funzioni, terzo giro

Il terzo elenco di funzioni di Windows Phone (i numeri sono quelli dell'elenco; mancano le voci 10 e 13–17, che non ho fatto). Tutto si
spegne o si sceglie nelle impostazioni, e **ognuna è pensata per non consumare**: vedi *Batteria*, in fondo a questa sezione.
Le app di Google si usano dove ci sono (Maps, Calendar, Keep, Lens, News, Finance); **niente Cortana**, che non esiste più.

**Riquadri nuovi** (menu *aggiungi*, tenendo premuto sul vuoto di Start)
1. **Luogo**: *Casa*, *Lavoro* o un indirizzo; un tocco avvia la navigazione (`google.navigation:` su Google Maps, poi `geo:` per qualunque
   altra app di mappe, poi una pagina web). Il riquadro non legge la posizione: l'indirizzo lo dà l'utente.
2. **Album di foto**: un album della galleria come riquadro *a rotazione* (come quello delle foto, ma di un album solo).
3. **Gruppo di contatti**: i visi di un gruppo che si alternano; un tocco apre i contatti (Android non permette a un launcher di aprire direttamente un gruppo).
4. **Timer**: un riquadro con il conto alla rovescia, sul riquadro e in una pagina con *+1 min* e *+5 min*. **Non pesa sulla batteria**: il
   timer è **una sola sveglia di Android** (`setAlarmClock`, che funziona anche a telefono in riposo profondo), il launcher non ha nulla
   in esecuzione mentre aspetta, e quello che si vede si calcola dall'ora di fine salvata. A fine tempo suona una notifica; dopo un riavvio
   la sveglia si riarma da sola (`AlertReceiver`, che si sveglia solo a quell'ora e al riavvio). Il timer compare come "prossima sveglia" di Android: è lui a garantirlo.
5. **Cronometro**: giri e tempo, con i decimi solo nella sua pagina. Conta sull'orologio che non si ferma (`elapsedRealtime`); dopo un riavvio
   usa l'ora di sistema, perché quell'orologio riparte da zero (si riconosce dal contatore degli avvii). Mentre non lo guardi non gira nulla.
6. **Transizione dei riquadri vivi**: oltre al *giro* (flip) c'è *scorri* (la faccia sale e quella nuova entra dal basso) e *misto* (metà dei riquadri gira, l'altra metà scorre); si sceglie in *live tile*. Come il giro, si ferma col movimento ridotto e col risparmio.
7. **Notizie e Finanza**: *Notizie* legge di base **Google News** (i titoli principali del giorno, nella lingua e nel paese del telefono), con altre
   sezioni (Mondo, Economia, Tecnologia, Sport…) o **un feed RSS/Atom a scelta**; si cambia in *live tile → notizie*. *Finanza* dà le valute della
   Banca centrale europea (`api.frankfurter.dev`) e alcune criptovalute (CoinGecko), fino a quattro, con la variazione; un tocco apre la pagina di
   Google Finance. **Niente prezzi di azioni**: non esiste una fonte gratuita e senza account che li dia, e non metto un servizio che chiede una chiave.
   Si legge solo se il riquadro è su Start, e solo quando il launcher è davanti.

**Blocco schermo** (si vede sul salvaschermo Metro e sull'anteprima)
8. **Stato rapido**: fino a **cinque app con il numero delle loro notifiche** sotto l'ora (a scelta, o le cinque che ne hanno di più), più **uno stato
   dettagliato** con l'ultimo messaggio di *una* app a scelta (spento di base: chi vede lo schermo lo legge; se hai scelto di non mostrare il testo dei messaggi sui riquadri, qui compare solo chi scrive). Rispetta anche le scelte *notifiche app per app* (n. 30).
9. **Musica**: copertina, titolo e i tasti precedente / pausa / successivo; le informazioni arrivano dalla sessione multimediale a eventi, senza leggere in continuazione.

**Volume e pulsanti**
11. **Volume Metro**: i tasti del volume mostrano un pannello Metro (nei tre temi: Classic, Glass, Droid), solo mentre il launcher è davanti. Si comporta come Android:
    durante una chiamata muove la chiamata, se suona un lettore con il proprio volume (Cast e simili) muove **quel** volume e dice il nome dell'app, altrimenti la musica o la suoneria.
    *Abbassare il volume di una sola app locale non si può* (vedi *Cosa non si può fare*): il pannello dice quale app suona e muove tutto il volume multimediale.
12. **Timeout dello schermo e luminosità automatica** come pulsanti dell'Action Center: il timeout passa per 15 s, 30 s, 1, 2, 5, 10 minuti (serve l'accesso *Modifica impostazioni di sistema*, lo stesso del cursore della luminosità).

**La ricerca come un assistente** (con Google dove si può; si spegne in *ricerca*)
18. **Promemoria**: `ricordami di chiamare Anna domani alle 18`, `remind me in 20 minutes to ...`. Sono del launcher (una sveglia di Android per volta, la più vicina; si riarmano al riavvio), compaiono sul riquadro *Promemoria* e nella scheda *Oggi*, e dalla notifica si può dire *fatto* o *tra 10 minuti*.
    Con Android 12 e successivi, senza il permesso di sveglie esatte (`SCHEDULE_EXACT_ALARM`) la sveglia può ritardare di qualche minuto: il launcher lo sa e usa quella meno precisa.
19. **Evento**: `evento cena venerdì alle 20` apre **Google Calendar** con titolo e ora già scritti (un altro calendario se non c'è Google).
20. **Nota**: `nota: comprare il latte` va a **Google Keep** (o diventa un riquadro nota se Keep non c'è).
21. **Scansiona**: `scansiona qr` (o `scan text`, `lens`) apre **Google Lens** (la fotocamera se non c'è): QR, testo, documenti.
    Promemoria, evento, nota, *vicino a me* e scansione si possono dire anche **a voce** (il microfono accanto alla casella: `ricordami di chiamare Anna domani alle 18`): è la stessa frase, letta dallo stesso interprete (`Assistant`).
22. **Chiamata intelligente T9**: nella casella, le cifre trovano i contatti per le lettere dei tasti (`2662` = *Anna*) e per il numero; un tocco chiama. Il lavoro è fatto una volta, sui nomi in memoria.
23. **Vicino a me**: `farmacia vicino a me`, `nearest petrol station` aprono **Google Maps** con la ricerca intorno a dove sei (il launcher non legge la posizione).
24. **Scheda *Oggi*** sopra la casella di ricerca: la data, il meteo già salvato, il prossimo evento, i prossimi due promemoria (e quanti sono scaduti), la prossima sveglia e quattro
    ricerche *vicino a me* (farmacia, ristoranti, benzina, bancomat). Si legge solo quando si apre la lista app (al massimo ogni due minuti, 20 col risparmio): niente gira in sottofondo.

**Interfaccia**
25. **Barra con "…"** nella modifica dei riquadri: il pulsante a tre puntini scrive il nome sotto ogni pulsante (*aggiungi, cartella, piccolo, medio, largo*), come la barra delle app di Windows Phone.
26. **Titoli panorama**, **spenti di base** (*aspetto → titoli panorama*): un grande titolo (*start*, *app*) dietro le pagine, che si muove a metà velocità e resta indietro mentre le pagine scorrono. Si disegna solo mentre scorri
    (nessuna ricomposizione), con il movimento ridotto segue le pagine, e con una foto su Start si disegna sopra, molto chiaro, perché la foto lo coprirebbe.
27. **Pagina chiamata rapida** (menu *aggiungi*): un riquadro che apre una pagina con la griglia dei preferiti; un tocco apre il tastierino con il numero, o chiama subito se hai acceso *chiama subito* e dato il permesso del telefono.
28. **Hub Persone** (menu *aggiungi*): i preferiti, le ultime chiamate e le notifiche dei messaggi in una pagina.
29. **Banner delle notifiche** mentre il launcher è sullo schermo, in stile Metro (*azioni*); resta 4,5 secondi, si scarta con uno swipe in su, un tocco apre la notifica. Per un secondo e mezzo dopo che il launcher torna davanti non annuncia nulla (il servizio sta rileggendo ciò che c'è), e annuncia solo notifiche di pochi secondi fa.
30. **Notifiche app per app** (*azioni → notifiche per app*, con un interruttore generale **per disattivare tutto**): per ogni app si sceglie se compare nell'Action Center, sul blocco schermo, come banner, e un tasto apre la pagina di Android dove si cambia il **suono**.
    Le scelte restano salvate anche con l'interruttore spento.

**Suono e vibrazione al tocco** (corretti): prima il click era quello di sistema, che dipende dall'interruttore *suoni al tocco* di Android, e la vibrazione da *vibrazione al tocco*; e partiva solo all'apertura di un'app. Ora il launcher ha un suo
click sintetizzato (`ClickSound`, nel canale dei suoni di interfaccia: segue il volume e il silenzioso del telefono) e una sua vibrazione breve, e parte da **ogni** tocco (riquadri, righe, pulsanti, interruttori, i tasti del contatore, la barra di modifica). Il suono resta caricato solo mentre il launcher è davanti.

**Batteria, per ognuna delle novità** (in più delle regole generali qui sotto)
- **Nessun lavoro a launcher chiuso**: i conti alla rovescia e il cronometro si aggiornano **solo mentre sono sullo schermo** (una volta al secondo; il cronometro a 100 ms solo nella sua pagina), allineati al
  secondo, e si fermano con il risparmio energetico dove non serve (`TimerFormat.untilNext…Tick`).
- **Timer e promemoria sono sveglie di Android** (una sveglia alla volta, la più vicina), non un servizio in esecuzione; i ricevitori si svegliano all'ora e al riavvio.
- **La rete solo quando serve e solo per i riquadri messi su Start** (Notizie, Finanza), con un passo suo (`PowerPolicy.feedMaxAgeMs`: 30 minuti, 3 ore col risparmio; dopo un tentativo, riuscito o no, si aspettano 10 / 30 minuti), solo `https`,
  solo gli host previsti (`news.google.com`, il feed scelto, `api.frankfurter.dev`, `api.coingecko.com`), risposta limitata a 256 KB (512 KB per i feed), al massimo tre reindirizzamenti verso lo stesso host (`SafeHttp`).
- **La musica del blocco schermo e il pannello del volume** si aggiornano con i callback della sessione multimediale: nessun polling, e i callback si registrano **solo mentre lo schermo che li mostra è avviato** (`LifecycleStartEffect`): con un'altra app davanti, un brano che cambia non sveglia il launcher. Il pannello del volume esiste solo mentre il launcher è davanti.
- **Il click** usa un `SoundPool` caricato solo mentre il launcher è davanti; la vibrazione è un unico `Vibrator` breve.
- **Scheda *Oggi***: calendario e sveglia si interrogano alla comparsa della lista app, mai più spesso di `PowerPolicy.todayMaxAgeMs`; il resto è già in memoria.

### Altre funzioni, quarto giro

**Animazioni nello stile di Windows Phone**
- **Continuum**: toccando una sezione delle impostazioni il titolo vola su dal punto toccato e le impostazioni scivolano dentro; le liste (impostazioni, menu *aggiungi*, Action Center) entrano a piume, una riga dopo l'altra (`featherIn`); gli interruttori dell'Action Center aperti si ribaltano dall'alto (`flipIn`). Tutto si ferma con *movimento ridotto*.
- **Selettori a ruota** (giorno, mese, anno; ore e minuti) per il conto alla rovescia e per i promemoria, al posto dei pulsanti più e meno (`LoopingSelector`).
- **Suoni di sistema Metro**, spenti di base (*azioni → suoni Metro*): un due note per la notifica e un glissando in su e in giù per l'Action Center. Sono generati dal codice (come il click), nessun file.

**Menu *aggiungi* in ordine**: un tocco lungo su uno spazio vuoto apre un menu per tipo di utilità (persone, luoghi, tempo, appunti, informazioni, media, sistema) con una **ricerca rapida** in alto: scrivendo restano solo le voci che corrispondono, anche per parole in italiano o inglese (`AddMenuIndex`).

**Aspetto**: *dove usare il colore d'accento* (riquadri di Start, Action Center e banner, elenco app e impostazioni: dove è spento diventa grigio) e *adattamento della foto di Start* (riempi, adatta, allunga, affianca, centra).

**Notifiche**: pulsanti d'azione dell'app (senza testo da scrivere), testo lungo con *leggi tutto*, immagine della notifica, cronologia delle ultime 30 scartate (solo in memoria), cursori separati di suoneria e sveglia nell'Action Center.

**Nuovi riquadri** (menu *aggiungi*)
- **Passi**: i passi di oggi dal sensore del telefono (`ACTIVITY_RECOGNITION`). Il contatore del sensore cresce dall'avvio: si parte dal valore che aveva alla prima lettura del giorno, quindi i passi fatti prima della *prima* lettura non si contano.
- **Registratore**: registra in `m4a` nella memoria dell'app e riascolta; niente esce dal telefono.
- **Fotocamera**: apre la fotocamera del telefono e mostra l'ultima foto (serve il permesso foto).
- **Portafoglio**: carte fedeltà e biglietti con il numero del codice, mostrati come **Code 128** grande su bianco. *Non legge ancora il codice con la fotocamera e non disegna QR*: il numero si scrive a mano, e il Code 128 è letto dalla maggior parte dei lettori dei negozi, non da tutti (un lettore che vuole EAN-13 o QR non lo legge).
- **Dove sono**: via e coordinate (`ACCESS_FINE_LOCATION`), aprire sulla mappa o inviare; la via sul riquadro è l'ultima trovata.
- **Persona viva**: il riquadro di un contatto mostra, girando, l'ultimo messaggio ricevuto da quella persona (dalle notifiche, per nome: funziona con le app che mettono il nome come titolo).
- **Sito vivo**: il riquadro di un sito mostra il titolo più recente del suo feed RSS/Atom (cercato nella pagina), solo https e solo sullo stesso host. Funziona con **qualunque browser**: *Condividi* → *Fissa su Start* (il launcher compare tra le app di condivisione) fissa la pagina; il tocco la apre nel browser predefinito.

**Meteo**: previsione **ora per ora** (24 ore) nella pagina e su una faccia del riquadro; **più città** (*impostazioni → riquadri live → segui anche questa città*, al massimo 5, lette solo quando apri la pagina); il riquadro Meteo mostra la previsione anche per **qualunque app di meteo** messa su Start (riconosciuta dal nome del pacchetto: `…weather…`, `…meteo…` e le più note). **Secondo fuso orario** sotto la data dell'orologio di Start (*impostazioni → start*).

**Ricerca di file per tipo**: *impostazioni → ricerca → file nella ricerca*: si scelgono i tipi (immagini, video, musica, documenti) e per ognuno si dà il permesso; la ricerca cerca per nome nell'indice media del telefono. *Da Android 13 i documenti non sono visibili alle app senza accesso a tutti i file*, quindi **Documenti** trova poco o nulla su un telefono recente: lo dice il testo dell'impostazione.

**Elenco delle app installate** (*impostazioni → backup*): salva in un file i nomi delle app che hai installato; su un telefono nuovo *Configura da un elenco di app* mostra quelle che mancano e ognuna apre la sua pagina nello store. Il launcher non installa nulla da solo.

### Altre funzioni, quinto giro

Ognuna ha il suo interruttore (o la sua scelta) nelle impostazioni, e si trova con la ricerca delle impostazioni.

**Start e riquadri**
- **Dimensione grande** (4x4, come Windows 10 Mobile) nella barra di modifica e nella freccia di ridimensionamento (*start screen → extra dei riquadri*). Sul riquadro Calendario mostra il mese con i prossimi eventi.
- **Titoli di gruppi di riquadri**: il pulsante *titolo* della barra di modifica scrive un titolo sopra il riquadro scelto, che apre un gruppo su una riga di blocchi a sé. Spento, Start torna una griglia semplice (i titoli restano salvati).
- **Layout di Start con nome** (Casa, Lavoro, Viaggio): si salvano con un tocco e si rimettono con un tocco; prima di mettere un layout, quello che c'è su Start torna nel layout che era attivo. Con *cambia layout a orario* ognuno può prendere il posto di Start a un'ora, una volta al giorno. I riquadri di contatti, note e simili tornano dal layout; **i widget no** (l'id di un widget è di un solo telefono).
- **Stile collage** (mosaico di foto) per i riquadri Foto e Album.
- **Calendario**: il riquadro largo elenca i prossimi eventi, il grande mostra il mese.
- **Interruttori su Start** (Wi-Fi, Bluetooth, aereo, torcia, silenzioso, silenzioso a tempo, rotazione, posizione, luminosità automatica): un tocco fa quello che fa il pulsante dell'Action Center. *Android non lascia accendere Wi-Fi e Bluetooth a un'app*: il riquadro mostra lo stato e apre il pannello di Android; la torcia e il silenzioso cambiano sul posto.
- **Batteria**: dice quanto dovrebbe durare la carica (previsione di Android, da Android 12). **Musica**: pulsanti precedente, play o pausa, successivo sul riquadro. **Sole**: alba, tramonto e durata del giorno, dai dati del meteo.
- **Una foto per ogni schermata di Start** (*start+theme*); quella senza foto mostra la foto di tutti.

**Notifiche**
- **Nascondi la notifica di Android** ora toglie dalla tendina *ogni* notifica che l'Action Center può mostrare, non solo quelle che hanno avuto il banner. Prima restavano le silenziose, i riepiloghi di gruppo e quelle arrivate a schermo spento o bloccato (si vedeva nei log: arrivavano e nessuno le cancellava). Anche quelle già presenti passano all'Action Center quando accendi l'opzione o il servizio si collega. Restano dove sono chiamate, sveglie, navigazione, la musica, ciò che è «in corso» o non cancellabile, le notifiche a schermo intero e le app che tieni fuori dall'Action Center.
- **Silenzia un'app dal pannello** (pressione lunga: 1 ora, 8 ore, fino a mattina); le copie restano e tornano alla scadenza. Solo nel launcher, non nel pannello sopra le altre app.
- **Parole da escludere**: una notifica con una di queste parole è tolta da Android e non si vede da nessuna parte (mai chiamate, sveglie, musica).
- **Prima messaggi e chiamate** nell'Action Center. **Silenzioso a tempo** (15 minuti – 4 ore) come pulsante dell'Action Center: si spegne da solo con una sveglia di Android (un po' in ritardo, se Android la sposta).
- **Cerca nelle notifiche** in attesa, dalla ricerca delle app.
- **Animazioni**: le notifiche raggruppate si aprono con una discesa e le righe entrano a piume; la freccia gira; il testo lungo, le opzioni e la risposta si aprono con un movimento (prima comparivano di colpo).

**Come si sente**: *velocità delle animazioni* (50%–200%; le molle, come il rimbalzo di un riquadro, non cambiano passo), *inclinazione alla pressione* (0%–150%) e *forza della vibrazione* (0%–200%) in *gesti*. **Operatori nella ricerca**: `app:`, `contatto:`, `imp:`, `file:`, `evento:`, `notifica:`, `riquadro:`, `web:` (e le forme brevi `a: c: s: f: e: n: t: w:`).

**Informazioni** sul telefono nella pagina *informazioni* (modello, Android, patch, memoria, spazio, schermo, da quanto è acceso): nulla esce dal telefono.

**Backup**: più copie nella cartella (le ultime 2, 3, 5 o 10, con data e ora) e *ripristino parziale* (solo Start, solo impostazioni, solo aspetto) per ogni ripristino; *Invia il layout* e *Invia l'aspetto* con il menu Condividi (un file senza passphrase né cartella).

**Tastiera**: non resta più aperta quando si torna al launcher da un'altra app, né quando la pagina con la casella di testo cambia (il campo, andando via, porta con sé la tastiera).

### Altre funzioni, sesto giro

Anche qui ogni cosa ha il suo interruttore (o la sua scelta) nelle impostazioni, e si trova con la ricerca delle impostazioni.

**Meteo, molto più completo** (*riquadri dinamici → Cosa mostrano le previsioni*, un interruttore per parte; la pagina e il riquadro li seguono):
- **Pioggia in arrivo**: una riga in cima dice «pioggia prevista tra 3 h», «dovrebbe smettere di piovere tra 2 h» o «nessuna pioggia nelle prossime 24 ore»; il riquadro prende una faccia con la stessa frase, ma solo se piove o sta per piovere.
- **Ora per ora**, **grafico della temperatura** (linea e barre della probabilità di pioggia), **dettagli** (percepita, vento, umidità, probabilità di pioggia, alba, tramonto, UV), **raffiche e direzione del vento**, **pressione**, **nuvole**, **visibilità**, **punto di rugiada**, **pioggia prevista oggi** (mm), **ore di sole**, **durata del giorno**, **fase lunare**, **pioggia e vento nei prossimi giorni**.
- **Qualità dell'aria** (indice europeo con la parola, PM2.5, PM10, ozono, NO₂; anche come faccia del riquadro) e **pollini** (ontano, betulla, graminacee, artemisia, olivo, ambrosia; *solo in Europa*) sono **spenti di base**: accesi, il launcher fa una seconda richiesta a Open-Meteo (`air-quality-api.open-meteo.com`, solo https, stessa regola delle altre). Le altre parti arrivano con la richiesta che c'era già, con qualche campo in più.

**Start e riquadri**
- **Passi**: obiettivo a scelta (da 5 000 a 20 000, o nessuno), storico di 60 giorni tenuto sul telefono, ultimi 7 giorni a barre con la riga dell'obiettivo, media, giorno migliore, **giorni di fila** con l'obiettivo raggiunto; il riquadro ha la barra dell'obiettivo. Il contapassi di Android conta da quando il riquadro lo legge la prima volta ogni giorno: un telefono riavviato a metà giornata non cancella i passi già contati.
- **Auto parcheggiata** (*aggiungi → luoghi*): un pulsante salva la posizione del telefono (una volta, solo quando lo premi); la pagina mostra la via, da quanto tempo, **Vai all'auto a piedi** (la mappa di Android con la strada a piedi, o la posizione se non c'è) e **Dimentica**. La posizione resta sul telefono.
- **Orologio analogico**, che si aggiorna ogni minuto finché il launcher è sullo schermo.
- **Colore del riquadro dall'icona** (*icone*): un riquadro di un'app prende il colore principale della sua icona, scurito quanto basta per il testo bianco; le icone grigie tengono l'accento.
- **Forma delle icone** (*icone*): come l'aspetto, quadrata, cerchio o quadrato arrotondato.
- **Come si comporta Start** (*start*): **torna in cima** dopo 1–60 minuti lontano dal launcher (come dopo Home), **blocca la disposizione** (nessun riquadro si sposta, ridimensiona, toglie o aggiunge per sbaglio; si sblocca da qui), **Start più in basso** (15–45% dello schermo, per usarlo con una mano), **annulla a più passi** (1, 3, 5 o 10 modifiche, una dopo l'altra; la voce nelle impostazioni annulla l'ultima anche dopo che il messaggio è sparito). Togliere un widget azzera la cronologia dell'annulla: il suo posto non può tornare.
- **Un altro accento di notte** (*start + tema*): vale mentre il tema scuro è acceso, a orario, col sole o sempre scuro.

**Action Center**
- **Trascina per riordinare** i quattro pulsanti in alto (tieni premuto un pulsante nelle impostazioni dell'Action Center, poi trascinalo).
- **La torcia si spegne da sola** dopo 1–30 minuti (una sveglia di Android: se Android la sposta, arriva un po' in ritardo).
- **Volumi preimpostati**: metti suoneria, contenuti multimediali e sveglie come vuoi, dai un nome e salva (fino a 6); nell'Action Center (anche quello sopra le altre app) compaiono come pulsanti sotto i modi del suono.

**Ricerca**
- **Interruttori e modi del suono per nome**: «torcia», «spegni la torcia», «silenzioso», «vibra», «wifi», «bluetooth», «non disturbare»… il primo risultato lo fa. *Android non lascia accendere Wi-Fi, Bluetooth e simili a un'app*: per quelli il risultato apre la schermata di Android, come il pulsante. Conta solo il testo intero, quindi un'app con una di queste parole nel nome si trova lo stesso.
- **Suggerimento del giorno** nella scheda Oggi (24 consigli sul launcher, uno al giorno).

**Impostazioni**: **riporta una pagina all'inizio**. La voce compare sotto il titolo solo se la pagina è stata cambiata; il primo tocco chiede, il secondo lo fa. Non tocca ciò che è tuo e non sta nella pagina: app nascoste e bloccate, volumi preimpostati, numero di schermate di Start. Le pagine Backup, Profili, Bambini, Informazioni e Debug non si riportano.

**Registratore**: ogni registrazione si **rinomina** (il nome sta dopo la data, quindi l'ordine resta) e si **invia** con il menu Condividi; il file sta nella memoria dell'app e solo l'app che scegli lo legge, una volta.

### Orizzontale

Si sceglie in *schermo+lingua*: **solo verticale** (come prima), **segui la rotazione del telefono** (rispetta il blocco di Android)
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
quanta batteria faccia risparmiare in ore **non è stato misurato** (servirebbe un telefono e una settimana di uso). Una prima misura
di cosa consuma il launcher su un telefono vero è in fondo a questa sezione.

- **A riposo non fa nulla.** Quando un'altra app è davanti, o lo schermo è spento, il launcher non ha timer, non ascolta la batteria
  né il risparmio energetico di Android, non segue la torcia, non aggiorna l'orologio. Gli orologi, i giri dei riquadri, il
  controllo dell'ora del tema a orario e l'aggiornamento dell'Action Center sono sospesi dal ciclo di vita dello schermo
  (`LaunchedWhileStarted`, `repeatOnLifecycle(STARTED)`): partono da capo quando si torna, e quello che è cambiato nel frattempo
  si legge una volta sola.
- **Il servizio di blocco non chiede eventi** (prima chiedeva i cambi di finestra, e quindi svegliava il processo a ogni cambio di app).
- **Le notifiche non si leggono a launcher chiuso.** Il servizio delle notifiche Android lo tiene vivo tutto il giorno, e prima a
  ogni notifica, anche con un'altra app davanti, chiedeva ad Android *tutte* le notifiche attive, le ricopiava, decodificava le
  immagini e interrogava la sessione multimediale: un telefono che ne riceve di continuo (un download, la musica, un contapassi)
  teneva il processo sveglio per ore. Era la causa più probabile del tempo "in background" segnalato da Android. Ora (`NotificationPacer`,
  `NotificationHub.startWatching`): con il launcher fuori dallo schermo una notifica che arriva è solo ricordata, senza nessuna
  lettura; si legge **una volta** quando lo schermo torna; a launcher aperto una raffica si legge al massimo due volte al secondo; e
  la musica non viene riletta a ogni notifica delle altre app. Android continua a consegnare le notifiche al servizio (non si può
  evitare senza perderle): quello è lavoro del sistema, non più nostro. Lo stesso per il salvaschermo (conta come schermo acceso finché
  è visibile).
- **La lista delle app non si ricarica a launcher chiuso.** Gli aggiornamenti delle app arrivano tutto il giorno, ognuno con più
  eventi, e a ognuno il launcher rileggeva *tutte* le app e svuotava la cache delle icone. Ora (`DeferWhileAway`) si ricorda che qualcosa
  è cambiato e si rilegge una volta al ritorno. Anche la sincronizzazione all'uscita, se accesa, non parte più di una volta ogni 15
  minuti (prima ogni minuto).
- **Si legge solo quello che serve**: contatti, calendario, foto e chiamate solo per i live tile fissati su Start, e **ognuno col suo
  passo** (`PowerPolicy.liveMaxAgeMs`): prossimo evento, chiamate perse e sveglia ogni due minuti al massimo, spazio ogni dieci,
  foto ogni quarto d'ora, volti ogni mezz'ora, compleanni (che scorrono tutti i contatti) ogni dodici ore; con il risparmio sei volte
  più rado, mai oltre un giorno. Le foto e i volti già decodificati si riusano: una lettura che trova le stesse non ne decodifica
  nessuna. Meteo ogni mezz'ora al massimo, e dopo un tentativo, riuscito o no, si aspettano dieci minuti (senza rete prima si riprovava
  a ogni ritorno su Start); la ricerca degli icon pack ogni cinque minuti; la sincronizzazione all'avvio ogni quindici. Un permesso dato
  o tolto, o un tile nuovo, fanno rileggere subito.
- **I 120 Hz solo mentre usi lo schermo.** Prima la richiesta restava fissa, e Start è la schermata che resta accesa più a lungo: a
  120 Hz anche quando nessuno la tocca. Ora (`RefreshRate.wantsHigh`) la frequenza alta si chiede al tocco e quando il launcher torna
  sullo schermo, e tre secondi dopo l'ultimo tocco si chiede la modalità più lenta che sia ancora a 60 Hz o più (`RefreshRate.atRest`,
  alla stessa risoluzione; sull'Android 15 e oltre anche la categoria di frequenza *normale*). Clock e giri dei riquadri sono basati
  sul tempo: a 60 Hz si vedono uguali. Sotto i 60 Hz non si scende: Android elenca 45, 30 o 20 Hz solo come sottomultipli di una modalità
  fisica. Si cambia solo se lo schermo dice di poter passare da una frequenza all'altra senza lampeggi (`alternativeRefreshRates`,
  Android 12 e oltre); altrimenti resta come prima.

  **Misurato su un telefono vero** (vivo V2514, Android 17, 25 ottobre 2026; build `optimized` in una copia affiancata, con un altro
  nome di pacchetto): lasciare andare la richiesta, come faceva la versione precedente, **non abbassa nulla**. Con il launcher fermo
  davanti per 14 secondi lo schermo restava a 120 Hz, perché il tetto scelto dal sistema era 120 e nessuno chiedeva di meno. Chiedere
  la modalità a 60 Hz arriva al sistema (`PRIORITY_APP_REQUEST_BASE_MODE_REFRESH_RATE = 60`), ma è solo un suggerimento sulla modalità
  di base: lo schermo è rimasto a 120 Hz, anche aggiungendo la categoria *normale*. Quel telefono non ha frequenza variabile per Android
  (`mVrrSupported=false`, tre sole modalità fisiche: 120, 90, 60 Hz) e ha un suo meccanismo (`dvrrWindowFrameRateHint`,
  `frameRateBoostOnTouch`): la frequenza che Android riporta potrebbe non essere quella vera del pannello. **Non so quindi se su quel
  telefono la richiesta faccia risparmiare batteria**; su altri telefoni dipende da come il produttore tratta il voto. A riposo il
  launcher non chiede alcun fotogramma (nessuna richiesta di vsync): a 120 Hz paga il pannello, non la CPU o la GPU del launcher. Se su
  un telefono la frequenza non risale al tocco, l'impostazione *massima frequenza di aggiornamento* si può spegnere.
- **Schermata di blocco e salvaschermo**: l'orologio si aggiorna ogni minuto, ma il calendario (una query) si interroga ogni cinque
  minuti, o subito quando l'evento è iniziato. Un'app che pubblica sul proprio tile le stesse parole non fa scrivere nulla su disco né
  ridisegnare il tile, e il provider non decodifica più tutte le impostazioni a ogni chiamata.
- **Risparmio energetico** (pagina *batteria*): *automatico* (quando c'è il risparmio di Android, o la batteria è al 20% o meno e
  non è in carica), *sempre* o *mai*. Mentre risparmia: i riquadri non girano da soli, le foto non si alternano, la richiesta dei
  120 Hz viene tolta, e tutto si legge meno spesso (meteo 2 ore, dati dei tile 15 minuti, sync quattro volte più rada).

**Misurare su un telefono vero.** Tutto quello che sopra è controllato dai test è logica; i consumi reali si misurano sul telefono.
`tools/battery-report.sh collect` (si esegue sul computer a cui il telefono è collegato con il debug USB, serve adb; solo lettura:
non installa e non cambia nulla, e non serve azzerare niente, perché Android tiene le statistiche dall'ultima carica) raccoglie in un archivio: tempo del processo per stato
(in primo piano, servizio, in cache), tempo di CPU, wakelock, sveglie, lavori, quante notifiche arrivano e da quali app, la fascia di
standby, la frequenza dello schermo e le righe di log del launcher. Legge soltanto; non raccoglie il contenuto delle notifiche. È stato
provato su un vivo V2514 con Android 17 (tutte le sezioni sono uscite piene); i comandi `dumpsys` cambiano un poco da una versione di
Android all'altra.

**Una prima misura** (vivo V2514, Android 17, 25 ottobre 2026; 4 h 56 min dalla nuova installazione, 9 min 20 s di launcher davanti in
63 ritorni; il telefono aveva speso 4.864 mAh dall'ultima carica): 22,0 mAh in tutto, lo 0,45%, di cui 20,4 mAh di **schermo** mentre il
launcher era davanti (dipende dalla luminosità, non dal lavoro del launcher) e 1,57 mAh di CPU (41,7 s di CPU). A schermo spento la CPU è
costata 0,058 mAh. Nessun wakelock né lavoro pianificato proprio, un solo avvio del processo, fascia di standby *esente*. Il servizio
notifiche tiene il processo vivo (lo lega il sistema, non si può evitare): 131 MB di memoria sua, 229-366 MB residenti nelle ultime
24 ore. Fotogrammi: 16.793, il 0,74% scattosi, mediana 7 ms, 95° percentile 12 ms, 99° 32 ms; una ventina oltre i 100 ms, 7 oltre i 200.
Nella stessa giornata la versione precedente dello stesso launcher (installazione sullo stesso telefono, uso diverso: **non è un
confronto controllato**) aveva speso nel servizio notifiche 10,3 mAh di CPU in 6 h 35 min contro 0,76 mAh in 4 h 47 min, e in primo
piano 9,78 mAh in 43 min contro 0,72 mAh in 9 min. Non si sa quanto di questo venga dalle ottimizzazioni e quanto dalla build `optimized` o
dall'uso: per dirlo serve un confronto sulla stessa giornata di uso, con la stessa build.

<img src="docs/screenshots/settings_battery.png" alt="La pagina batteria" width="260">

### Angolo bambini

Start ristretto alle app che scegli, senza lista app, Action Center né impostazioni. Per uscire si conferma con il blocco
schermo. Lo stato "acceso" non viaggia nei backup, così un file non può mai accenderlo su un altro telefono.

## Le impostazioni, sezione per sezione

Si aprono dalla voce *Settings* nella lista app. Sono **quattro pagine di un pivot** (come in Windows Phone) raggruppate per ciò che stai
cercando: **come appare**, **come si comporta**, **cosa fa con le app** e **gli strumenti**; ogni sezione ha la sua pagina. Tutto è
salvato come un unico JSON (`SettingsCodec`) che legge anche le versioni vecchie.

In cima c'è una **casella di ricerca**: scrivi cosa cerchi (*nero*, *dimensione testo*, `orologio`, `backup`, anche in inglese o con un
errore) e vedi le impostazioni, e le sezioni, che corrispondono; un tocco apre la pagina e **ti porta all'impostazione**. Tutte le
impostazioni sono in un solo elenco, l'**indice** (`ui/settings/SettingsIndex.kt`): ognuna ha un id, la sezione, il nome e le parole che
la trovano. Un test (`SettingsIndexTest`) disegna ogni pagina e controlla che le impostazioni che ha siano **esattamente** quelle
dell'indice: una impostazione nuova non indicizzata, o spostata senza aggiornarlo, fa fallire i test.

**aspetto**

| Sezione | Cosa c'è |
|---|---|
| start+theme | **tema** (Classic, Modern Glass, Modern Droid) e, per Droid, i colori dello sfondo; scuro / chiaro / come il telefono / a orario / **dal tramonto all'alba**; nero OLED; colore d'accento (20), **colore tuo con i cursori** o dalla foto; foto di Start, **inquadratura** e parallasse |
| schermata start | **riquadri** (più riquadri, nomi, spaziatura, numero di schermate Start); **orologio e data** (ore, sveglia, settimana, tocchi); barra di stato nascosta; **ripristina Start** |
| icone e temi | icon pack; esporta e importa un file tema |
| accessibilità | **dimensione, peso e carattere del testo**; riduci il movimento; alto contrasto; TalkBack e scorciatoia alle impostazioni di Android |
| blocco schermo | anteprima, foto (anche nel blocco schermo di Android), cosa mostrare (anche **meteo** e **prossimo evento**), impostazioni del salvaschermo |

**uso**

| Sezione | Cosa c'è |
|---|---|
| schermo+lingua | rotazione (verticale, segui il telefono, orizzontale); animazione di apertura; massima frequenza di aggiornamento (120 Hz, tolta mentre si risparmia batteria); **lingua del launcher** |
| gesti | tirata dal bordo, indietro dal bordo, doppio tocco, **Home premuto due volte**, due dita in giù e in su (*app recenti*, screenshot, menu di spegnimento, schermo diviso, pannelli di Android, **un'app a scelta**), app recenti, servizio di accessibilità, suono e vibrazione al tocco |
| notifiche+azioni | **Action Center**: i quattro pulsanti, **volume, luminosità, suoneria e torcia** nel pannello; **notifiche**: accesso, mostrale sui riquadri, risposta rapida, raggruppamento |
| voce | microfono nella ricerca; riconoscimento sul telefono; *chiama subito*; prova; cosa si può dire |
| batteria | risparmio energetico (automatico, sempre, mai) e cosa cambia; cosa il launcher non fa quando non è sullo schermo |

**app**

| Sezione | Cosa c'è |
|---|---|
| lista app | gruppi in cima alla lista (più usate, nuove), filtro per categoria, **elenco compatto**; app nascoste, rinominate e bloccate |
| ricerca | **dove cerca** (contatti, impostazioni di Android, **impostazioni del launcher**, **ciò che c'è su Start**, **calendario**); **web** e motore; **risposte** e **valute**; **cronologia** delle ricerche |
| live tile | città del meteo (ricerca o posizione) e gradi; **contenuti dal vivo delle altre app** (interruttore, accesso alle notifiche, cosa mostrare, app per app) e **cosa legge ora il launcher**; lascia pubblicare i tile alle app, ed elenco di chi l'ha fatto |
| profili | lavoro e spazio privato: pausa e riattiva; mostra le loro app |

**strumenti**

| Sezione | Cosa c'è |
|---|---|
| backup+sync | file o cartella, ogni quanto, ripristina; sincronizzazione con passphrase; esporta e importa |
| angolo bambini | app e avvio |
| informazioni | versione e dettagli del launcher; rivedi il tutorial; crediti |
| Debug developer | quattro report in file di testo, ognuno a parte e salvato dove scegli: **stato batteria dell'app**, **stato batteria di tutto il telefono**, **log di sistema di tutto il telefono**, **log dell'app** (dall'avvio del processo, finché i buffer di Android lo hanno ancora). I dump di sistema e il log degli altri richiedono `adb shell pm grant com.metrowin.launcher android.permission.DUMP` e `…READ_LOGS` più l'accesso all'uso nelle impostazioni di Android; senza, il file lo dice e contiene il resto. I file possono contenere dati privati |

## Cosa non si può fare da un launcher, e come l'ho aggirato

| Funzione di Windows Phone | Stato |
|---|---|
| Schermata di blocco al posto di quella di Android | Non si può: la sostituisce solo il sistema. C'è come **salvaschermo** e come foto del blocco (vedi sopra) |
| Accendere Wi-Fi / Bluetooth / aereo dall'Action Center | Android non lo permette più alle app: i pulsanti aprono la schermata giusta |
| Chiamare un contatto a voce | Fatto: tastierino con il numero (un tocco), oppure chiamata diretta se la attivi e dai il permesso del telefono |
| Cortana, Word Flow (tastiera), Driving Mode, Wi-Fi Sense, task switcher | Funzioni di sistema o app a parte |
| Volume separato **per app** (come il mixer di Windows Phone 8.1) | Non si può: Android muove insieme il volume multimediale di tutte le app e non offre a un'app il modo di abbassare quello di un'altra. Il pannello del volume dice **quale app suona** e, per i lettori con volume proprio (Cast, sessioni remote), muove quello |
| Suono diverso **per app** | Non si può: i canali di notifica sono dell'app che li crea; il launcher apre la pagina di Android di quell'app, dove si cambia |
| Prezzi delle azioni nel riquadro Finanza | Non fatto: nessuna fonte gratuita e senza account; ci sono le valute della BCE e alcune criptovalute |
| Cortana per promemoria ed eventi | Sostituita da Google: promemoria del launcher, eventi in Calendar, note in Keep, luoghi in Maps, scansione in Lens (**se sono installati**; altrimenti un'app equivalente o un riquadro) |
| Start e lista app affiancati in orizzontale | Non fatto: in orizzontale sono due pagine come in verticale, con più colonne |
| Ritaglio con pizzico della foto di sfondo | Fatto in altro modo: **zoom e due cursori di posizione** con anteprima (non il pizzico) |
| Sincronizzare la foto di Start e quella del blocco | Non fatto: le foto sono file di questo telefono |

## Permessi

Il launcher funziona senza nessun permesso facoltativo. Li chiede il [tutorial](#il-tutorial-iniziale), uno per pagina, e
si possono dare anche dopo, quando serve la funzione.

| Permesso | Serve a |
|---|---|
| `QUERY_ALL_PACKAGES` | vedere tutte le app. Google Play ne limita l'uso e chiede una giustificazione: da verificare se pubblichi l'app |
| `READ_MEDIA_IMAGES` (+ `READ_MEDIA_VISUAL_USER_SELECTED` su Android 14, `READ_EXTERNAL_STORAGE` fino ad Android 12L) | riquadro Foto |
| `READ_CALENDAR`, `READ_CONTACTS`, `READ_CALL_LOG` | riquadri Calendario, Persone (e ricerca contatti), Telefono; la ricerca negli eventi e il prossimo evento sul blocco (`READ_CALENDAR`, spenti); i **compleanni** (`READ_CONTACTS`) |
| `INTERNET` | il meteo (solo Open-Meteo, solo https, risposta limitata a 256 KB), **qualità dell'aria e pollini** (`air-quality-api.open-meteo.com`, solo se li accendi) e la ricerca di una città; i tassi di cambio (solo `api.frankfurter.dev`, https, spenti di base); **solo se hai messo il riquadro su Start**: i titoli di Notizie (Google News, o il feed che scegli) e le quotazioni di Finanza (`api.frankfurter.dev`, `api.coingecko.com`) |
| `ACCESS_COARSE_LOCATION` | "usa la mia posizione" del meteo (l'ultima nota, una volta) |
| `ACTIVITY_RECOGNITION` | il riquadro **Passi** (il contapassi del telefono); si chiede quando lo metti |
| `ACCESS_FINE_LOCATION` | il riquadro **Dove sono**, una lettura sola quando apri la pagina, e al massimo ogni 15 minuti con il riquadro su Start e il launcher davanti: mai in continuo |
| `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` | la ricerca di file video e musica, solo per i tipi che accendi |
| *(nessun permesso)* `FileProvider` | *Invia il layout* e *Invia* una registrazione: il file sta nella cache o nella memoria dell'app e solo l'app che scegli lo legge, una volta |
| `RECORD_AUDIO` | la voce, quando la usi la prima volta; il riquadro **Registratore** |
| `CALL_PHONE` | chiamare **subito** (riquadro chiamata rapida, voce con *chiama subito*). Si chiede quando metti un riquadro di chiamata rapida o accendi l'interruttore; senza, si apre il tastierino con il numero |
| `com.android.alarm.permission.SET_ALARM` | timer e sveglie dalla ricerca e dalla voce (permesso normale, concesso all'installazione) |
| `POST_NOTIFICATIONS` (Android 13 e successivi) | l'avviso quando un **timer** finisce o un **promemoria** scatta; si chiede la prima volta che ne avvii uno |
| `SCHEDULE_EXACT_ALARM` | far suonare timer e promemoria **all'ora esatta**; senza (Android lo può togliere) suonano con un piccolo ritardo, e il launcher se ne accorge e usa la sveglia meno precisa |
| `RECEIVE_BOOT_COMPLETED` | riarmare la sveglia di timer e promemoria dopo un riavvio o un aggiornamento: il ricevitore si sveglia una volta e finisce |
| `VIBRATE` | la breve vibrazione al tocco, se la accendi |
| `DUMP`, `READ_LOGS`, `PACKAGE_USAGE_STATS` | solo la sezione **Debug developer**: Android le dà solo tramite adb e le impostazioni, quindi non si chiedono mai e l'app funziona senza |
| `SYSTEM_ALERT_WINDOW` (accesso speciale) | solo il **banner sopra le altre app**: spento di base, lo dai tu in una schermata di Android; senza, il banner resta quello del launcher |
| `WRITE_SETTINGS` (accesso speciale) | solo il cursore della **luminosità** dell'Action Center: lo dai tu, in una schermata di Android, e il cursore lo dice finché non c'è |
| `SET_WALLPAPER` | la foto nella schermata di blocco di Android, quando lo chiedi |
| `ACCESS_WIFI_STATE`, `REQUEST_DELETE_PACKAGES` | stato del Wi-Fi, *disinstalla* nel menu |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | solo per chiedere, nel tutorial, che Android non chiuda il launcher in background. **Google Play lo consente solo ad alcuni tipi di app**: per una build non pubblicata sullo store va bene, per pubblicarla va giustificato o tolto (basta rimuovere la pagina "tienilo attivo") |
| `com.metrowin.launcher.permission.PUBLISH_TILE` | **dichiarato dal launcher**, lo chiedono le app che vogliono un tile (vedi sotto) |
| Accesso alle notifiche (nelle impostazioni di Android) | Action Center, contatori, risposta rapida, raggruppamento, **posticipa** |
| Accesso a *non disturbare* (nelle impostazioni di Android) | il pulsante *ore silenziose* e il silenzioso: li accendono sul posto |
| Servizio di accessibilità del launcher (facoltativo) | **blocco schermo, app recenti, screenshot, menu di spegnimento, schermo diviso**, pannelli di Android: azioni che solo un servizio può fare; non legge nulla dello schermo |
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

**2000 test**, nessuno saltato; il lint non ha errori (restano gli avvisi sulle versioni più recenti delle librerie, che chiedono AGP 9).

- Logica pura: griglia e colonne dalla larghezza, operazioni su Start, regole di trascinamento e di rilascio, ricerca,
  salvataggio e migrazione delle impostazioni, comandi vocali, pianificazione della sincronizzazione, **cifratura** (anche
  file alterati, passphrase sbagliata, numero di iterazioni ostile), tema, icon pack (`appfilter.xml`), tile pubblicati,
  raggruppamento delle notifiche, piano dell'animazione di apertura, regole di accessibilità.
- Interfaccia su JVM (Robolectric con grafica nativa, `app/src/test/.../screens`): disegna le schermate vere, esegue
  **gesti veri** e controlla i pixel; salva ogni schermata in `app/build/screenshots/*.png`. Esempi: tile in orizzontale con
  le dimensioni giuste, risposta rapida che arriva all'app come risultato del `RemoteInput` (con un `PendingIntent` vero),
  swipe del blocco schermo con rimbalzo, azioni di TalkBack che fanno quello che dicono.

- **Il terzo giro** ha i suoi test: la logica pura (`WhenParserTest`: «domani alle 18»; `AssistantTest`: promemoria, eventi, note, «vicino a me», scansione, anche a voce;
  `NewsFinanceTest` con un feed di Google News e le risposte di Frankfurter e CoinGecko come sono; `TilesStateTest`: timer e cronometro; `TodayTest`; `MoreFeaturesTest`: volume, stato del blocco,
  scelte per app), le facce dei riquadri nuovi (`NewTilesUiTest`: anche la transizione *scorri*), le pagine e le scelte (`NewPagesUiTest`), il pannello del volume nei tre aspetti, il banner e
  il blocco schermo con stato e musica (`NewOverlaysUiTest`), la scheda *Oggi*, il «…» della barra e i titoli panorama con la loro velocità (`TodayUiTest`), le impostazioni nuove (`NewSettingsUiTest`)
  e il click e la vibrazione al tocco (`TapFeedbackTest`).

- **Testi che entrano sempre** (`TextFit*`, `screens/TextFitTest.kt`): ogni schermata, menu, finestra, barra e riquadro viene disegnato
  nei casi peggiori (italiano, che ha le parole lunghe; testo al 130% e al 170%; schermo largo 320 dp; schermo in orizzontale;
  i tre aspetti Classic, Glass e Droid; inglese) e a ogni testo si chiede com'è stato disposto: un testo tagliato ("…"), una
  parola spezzata a metà, un testo fuori dallo schermo o sopra un altro è un errore del test. `MetroText` va a capo, e se lo spazio è
  troppo stretto per la parola più lunga, o troppo basso per le righe, disegna il testo più piccolo (fino a metà) invece di tagliarlo;
  i riquadri e i pulsanti dell'Action Center non crescono oltre il 130% del testo; le finestre e i pannelli scorrono invece di
  uscire dallo schermo. Quello che scrive l'utente (il nome di un'app, una nota) può finire con "…" in una lista, e lì i test usano nomi brevi.
  Provato su JVM con font di prova: non l'ho visto su un telefono.

Anche questo giro ha trovato difetti veri, corretti: nella scheda *Oggi* la parola «Oggi» finiva spezzata in una colonna accanto al meteo; il riquadro Timer e quello dei promemoria
sovrapponevano le righe al proprio nome; sul blocco schermo la musica si sovrapponeva all'ora (con testo grande) e in orizzontale l'ora usciva dallo schermo (ora c'è il layout affiancato e il ridimensionamento);
il pannello del volume tagliava il nome di un'app lunga; un riquadro Album non poteva tornare allo stile *a rotazione* dopo averlo cambiato.

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
                  scorciatoie, LaunchPlan, LockInfo, Tutorial (passi, bisogni, controllo di cosa è già concesso),
                  risposte della ricerca (Calculator, UnitConverter, QuickAnswers, Currencies, DateAnswers, Rolls, WorldCities), AppShortcuts, AppUsage, SoundAndBrightness,
                  riquadri nuovi (Checklist, MoonPhase, Birthdays), CalendarSearch, SafeHttp (l'unica via verso la rete),
                  terzo giro: timer e cronometro (Timers), promemoria (Reminders, WhenParser: «domani alle 18»), l'interprete della ricerca e della voce (Assistant),
                  chiamata T9 (SmartDial), Notizie e Finanza (NewsFeed, Finance), Luogo/Album/Gruppo (Places, Collections), Persone (People),
                  il volume (VolumeControl), lo stato del blocco (LockStatus), la scheda Oggi (Today), il click (ClickSound)
                  ricerca (SearchIndex: l'indice, AppSearch: app e alias, TileSearch: note e liste su Start, GlobalSearch: impostazioni di Android)
  notifications/  NotificationService, NotificationHub (anche risposta), NotificationGroups, Alerts (timer e promemoria: una sveglia di Android),
                  NotificationControl (le scelte per app), MediaSessions (la musica, a callback)
  model/          Tile, Folder, TileSize, AppItem, Glyph
  gestures/       servizio di accessibilità (blocco schermo, recenti, screenshot…), tile delle Impostazioni rapide
  ui/             start/, apps/, actioncenter/, settings/ (una pagina per sezione, SettingsIndex: l'elenco di tutte le impostazioni, SettingsFocus: scorri fino a una impostazione), voice/, lock/ (schermata e salvaschermo),
                  weather/, kids/, tutorial/ (giro iniziale e richieste), anim/, gesture/ (anche i titoli panorama), components/, theme/ (compreso Accessibility),
                  pages/ (le pagine di timer, cronometro, notizie, finanza, promemoria, chiamata rapida e Persone), actioncenter/ (anche il banner e il pannello del volume)
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
  - le chiamate di rete del **meteo** (Open-Meteo), il servizio di **notifiche**, lo screen pinning e il servizio per bloccare lo schermo;
  - le **scorciatoie delle app** (Android le dà solo all'app Home), la **chiamata diretta**, **screenshot, menu di spegnimento e schermo
    diviso** (dipendono dal servizio di accessibilità e dalla versione di Android), il **cursore della luminosità** (accesso speciale),
    la **barra di stato nascosta** (cosa fa lo swipe dal bordo alto dipende dal produttore), la **dimensione del testo** (su Android 14 il
    sistema scala il testo con una tabella che i test non hanno: i test provano che il tema la imposta, non la misura sullo schermo);
  - le **valute** (la risposta del server è provata con un testo di esempio uguale a quello vero, non con la rete), il **posticipa** (serve il servizio di notifiche vero), l'**intensità della torcia** (dipende dall'hardware), la
    **lingua del launcher** (la lista di Android per app e il riavvio dell'attività), **Home premuto due volte** (il sistema o il produttore possono già usarlo), l'**invio** dei messaggi a voce
    (apre l'app messaggi: l'app che scegli decide come si comporta con il testo), le **indicazioni** a voce (dipende dall'app di mappe) e la **luna** (calcolata: un giorno di scarto vicino alle fasi);
  - i **live tile delle altre app**: foto, copertine e testi veri dipendono da cosa scrive ogni app nelle sue notifiche. La pagina *live tile*
    elenca cosa il launcher legge da ognuna: se un riquadro non mostra niente, si guarda lì.
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
