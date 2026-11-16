# Mappa delle impostazioni del launcher

Dove sta ogni impostazione: pagina vecchia (gruppo › pagina, fino alla 26.10B3) e pagina nuova (categoria › pagina). Una impostazione sta in una sola pagina; quando un'altra pagina ne ha bisogno mette un collegamento, non una copia. Le chiavi salvate (`SettingsCodec`, `ThemeCodec`, backup) non cambiano: cambia solo dove si mostrano.

Questo file è prodotto dal codice (`SettingsIndex`, `SettingsEnv`, `SectionReset`) e controllato dai test `SettingsIndexTest` e `SettingsMapTest`: se una impostazione cambia posto, aggiorna anche questa mappa.

## Struttura

Due livelli: la **radice** elenca le categorie (icona, nome, una riga, la "i" con la spiegazione); una **categoria** è un pivot delle sue pagine. Con una sola pagina la categoria non ha pivot.

| categoria | pagine (pivot) | a cosa serve |
|---|---|---|
| aspetto e temi | tema e colori, schermo+lingua | Tema, colori, la foto dietro Start, rotazione dello schermo e lingua |
| start e tile | schermata start, orologio e barre, comportamento di start, live tile | Tile, orologio, schermate Start, live tile e come si comporta Start |
| schermata di blocco | blocco schermo | Foto, orologio, notifiche, impronta e salvaschermo della schermata di blocco Metro |
| barre e action center | Barra di stato, action center, barra di navigazione | Barra di stato MetroWin, pulsanti e cursori dell’Action Center, barra di navigazione |
| gesti e scorciatoie | gesti, tocco e sensazione, voce | Doppio tocco, due dita, tirate dal bordo, feedback del tocco e voce |
| app e icone | lista app, icone, ricerca | La lista delle app, gli icon pack e l’icona di ogni app, e la casella di ricerca |
| mini app | mini app | Gli interruttori di Messaggi, Mail, Calendario e delle altre app integrate nel launcher |
| notifiche | notifiche | Chi le legge, banner, risposte, gruppi, filtri e suoni |
| privacy e sicurezza | protezione, angolo bambini | App delle banche, app bloccate e angolo bambini |
| batteria | batteria | Risparmio energetico e cosa fa il launcher quando non è sullo schermo |
| accessibilità | accessibilità | Dimensione e spessore del testo, carattere, meno movimento, più contrasto, TalkBack |
| backup e profili | backup+sync, profili | Salva e ripristina la disposizione, sincronizza i telefoni, profilo di lavoro e spazio privato |
| informazioni | informazioni, segnalazione bug, altri progetti | Versione e aggiornamenti, il tutorial, segnalazione di problemi, crediti e altri progetti |

## Impostazioni, una per una

### aspetto e temi

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| tema | `theme.style` | aspetto › aspetto | aspetto e temi › tema e colori |
| look Windows Phone | `theme.wplook` | (nuova) | aspetto e temi › tema e colori |
| chiaro o scuro | `theme.mode` | aspetto › aspetto | aspetto e temi › tema e colori |
| scuro dalle | `theme.from` | aspetto › aspetto | aspetto e temi › tema e colori |
| fino alle | `theme.to` | aspetto › aspetto | aspetto e temi › tema e colori |
| Nero OLED | `theme.oled` | aspetto › aspetto | aspetto e temi › tema e colori |
| usa i colori dello sfondo | `theme.dynamic` | aspetto › aspetto | aspetto e temi › tema e colori |
| colore d'accento | `theme.accent` | aspetto › aspetto | aspetto e temi › tema e colori |
| Un altro accento di notte | `theme.nightaccent` | aspetto › aspetto | aspetto e temi › tema e colori |
| accento dalla foto di Start | `theme.autoaccent` | aspetto › aspetto | aspetto e temi › tema e colori |
| sfondo di start | `theme.background` | aspetto › aspetto | aspetto e temi › tema e colori |
| Stesso sfondo per Android | `theme.syswallpaper` | aspetto › aspetto | aspetto e temi › tema e colori |
| Una foto per ogni schermata di Start | `theme.pagebackgrounds` | aspetto › aspetto | aspetto e temi › tema e colori |
| Come la foto riempie lo schermo | `theme.photofit` | aspetto › aspetto | aspetto e temi › tema e colori |
| Dove si usa il colore d'accento | `theme.accentuse` | aspetto › aspetto | aspetto e temi › tema e colori |
| Colore d'accento di una mini app | `theme.miniaccent` | aspetto › aspetto | aspetto e temi › tema e colori |
| inquadratura della foto | `theme.framing` | aspetto › aspetto | aspetto e temi › tema e colori |
| i riquadri lasciano vedere la foto | `theme.tileopacity` | aspetto › aspetto | aspetto e temi › tema e colori |
| Sfondo dentro ai riquadri | `theme.photoinside` | aspetto › aspetto | aspetto e temi › tema e colori |
| la foto scorre più piano dei riquadri | `theme.parallax` | aspetto › aspetto | aspetto e temi › tema e colori |
| Titoli panorama | `theme.panorama` | aspetto › aspetto | aspetto e temi › tema e colori |
| esporta il tema | `theme.export` | aspetto › aspetto | aspetto e temi › tema e colori |
| importa un tema | `theme.import` | aspetto › aspetto | aspetto e temi › tema e colori |
| rotazione | `screen.orientation` | uso › schermo+lingua | aspetto e temi › schermo+lingua |
| apertura di un'app | `screen.launch` | uso › schermo+lingua | aspetto e temi › schermo+lingua |
| margini delle pagine | `screen.padding` | uso › schermo+lingua | aspetto e temi › schermo+lingua |
| lingua del launcher | `screen.language` | uso › schermo+lingua | aspetto e temi › schermo+lingua |
| massima frequenza di aggiornamento | `screen.refresh` | uso › schermo+lingua | aspetto e temi › schermo+lingua |

### start e tile

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| mostra più riquadri | `start.moretiles` | aspetto › schermata start | start e tile › schermata start |
| Riquadri per riga | `start.columns` | aspetto › schermata start | start e tile › schermata start |
| Modalità libero | `start.free` | aspetto › schermata start | start e tile › schermata start |
| nomi sui riquadri | `start.tilenames` | aspetto › schermata start | start e tile › schermata start |
| spazio tra i riquadri | `start.spacing` | aspetto › schermata start | start e tile › schermata start |
| schermate start | `start.pages` | aspetto › schermata start | start e tile › schermata start |
| Start preferito | `start.favorite` | aspetto › schermata start | start e tile › schermata start |
| Start secondario | `start.home` | aspetto › schermata start | start e tile › schermata start |
| Elenco app a sinistra | `start.appsleft` | aspetto › schermata start | start e tile › schermata start |
| Indicatore sotto i riquadri | `start.appsarrow` | aspetto › schermata start | start e tile › schermata start |
| Proteggi lo schermo dal burn-in | `start.antiburnin` | uso › comportamento di start | start e tile › comportamento di start |
| ripristina Start | `start.reset` | aspetto › schermata start | start e tile › schermata start |
| orologio e data su Start | `start.clock` | aspetto › barre e orologio | start e tile › orologio e barre |
| ore dell'orologio | `start.hours` | aspetto › barre e orologio | start e tile › orologio e barre |
| prossima sveglia sotto la data | `start.alarm` | aspetto › barre e orologio | start e tile › orologio e barre |
| Secondo fuso orario sotto la data | `start.zone` | aspetto › barre e orologio | start e tile › orologio e barre |
| Obiettivo giornaliero dei passi | `start.stepgoal` | uso › comportamento di start | start e tile › comportamento di start |
| Torna in cima dopo | `start.topafter` | uso › comportamento di start | start e tile › comportamento di start |
| Blocca la disposizione | `start.lock` | uso › comportamento di start | start e tile › comportamento di start |
| Start più in basso (una mano) | `start.lower` | uso › comportamento di start | start e tile › comportamento di start |
| Dimensione grande | `start.large` | aspetto › schermata start | start e tile › schermata start |
| Start e app affiancati | `start.panels` | aspetto › schermata start | start e tile › schermata start |
| Titoli dei gruppi di riquadri | `start.groups` | aspetto › schermata start | start e tile › schermata start |
| Stile collage | `start.collection` | aspetto › schermata start | start e tile › schermata start |
| Agenda sul riquadro Calendario | `start.agenda` | aspetto › schermata start | start e tile › schermata start |
| Durata sul riquadro Batteria | `start.batteryleft` | aspetto › schermata start | start e tile › schermata start |
| Pulsanti sul riquadro Musica | `start.musiccontrols` | aspetto › schermata start | start e tile › schermata start |
| Layout di Start | `start.layouts` | aspetto › schermata start | start e tile › schermata start |
| Cambia layout a orario | `start.layoutschedule` | aspetto › schermata start | start e tile › schermata start |
| numero della settimana accanto alla data | `start.week` | aspetto › barre e orologio | start e tile › orologio e barre |
| tocca l'orologio | `start.clocktaps` | aspetto › barre e orologio | start e tile › orologio e barre |
| Barra con … | `start.appbar` | aspetto › barre e orologio | start e tile › orologio e barre |
| Barra delle azioni | `start.actionbar` | aspetto › barre e orologio | start e tile › orologio e barre |
| Modifiche di Start che puoi annullare | `start.undosteps` | uso › comportamento di start | start e tile › comportamento di start |
| Annulla l'ultima modifica di Start | `start.undonow` | uso › comportamento di start | start e tile › comportamento di start |
| città del meteo | `live.city` | app › live tile | start e tile › live tile |
| Riquadro foto | `live.gallery` | app › live tile | start e tile › live tile |
| temperature in Fahrenheit | `live.fahrenheit` | app › live tile | start e tile › live tile |
| contenuti dal vivo delle altre app | `live.master` | app › live tile | start e tile › live tile |
| Consenti l'accesso alle notifiche | `live.access` | app › live tile | start e tile › live tile |
| conversazioni e notifiche | `live.messages` | app › live tile | start e tile › live tile |
| nascondi cosa c'è scritto | `live.hidetext` | app › live tile | start e tile › live tile |
| immagini | `live.pictures` | app › live tile | start e tile › live tile |
| la musica che suona | `live.playing` | app › live tile | start e tile › live tile |
| download e avanzamento | `live.progress` | app › live tile | start e tile › live tile |
| app | `live.apps` | app › live tile | start e tile › live tile |
| cosa legge ora il launcher | `live.report` | app › live tile | start e tile › live tile |
| Come cambia faccia un tile | `live.transition` | app › live tile | start e tile › live tile |
| Mostra i valori in | `live.financebase` | app › live tile | start e tile › live tile |
| Cosa seguire (fino a 4) | `live.financeitems` | app › live tile | start e tile › live tile |
| lascia che le app pubblichino il testo del loro tile | `live.published` | app › live tile | start e tile › live tile |
| Blocchi del tile Meteo (uno per blocco, vedi `WeatherBlock`) | `live.wx.<blocco>` | app › live tile | start e tile › live tile |

### schermata di blocco

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| Blocco schermo Metro all'accensione | `lock.overlay` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| anteprima | `lock.preview` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| foto del blocco schermo | `lock.photo` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| mostra la prossima sveglia | `lock.alarm` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| mostra il numero di notifiche | `lock.notifications` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| mostra la batteria | `lock.battery` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| mostra il meteo | `lock.weather` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| mostra il prossimo evento | `lock.event` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| usa la foto nel blocco schermo del telefono | `lock.apply` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| salvaschermo | `lock.dream` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| Stato rapido (contatori delle app) | `lock.quick` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| App dello stato rapido | `lock.quickapps` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| Stato dettagliato | `lock.detail` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| Sensore sotto lo schermo | `lock.sensor` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| Altezza del sensore | `lock.sensorY` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| Usa fingerprint MetroWin | `lock.sensorOwn` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| icone delle notifiche in una sola riga, più piccole | `lock.oneRow` | aspetto › blocco schermo | schermata di blocco › blocco schermo |
| Controlli musicali e copertina | `lock.music` | aspetto › blocco schermo | schermata di blocco › blocco schermo |

### barre e action center

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| nascondi la barra di stato | `statusbar.hide` | aspetto › barre e orologio | barre e action center › Barra di stato |
| Barra di stato Windows Phone | `statusbar.metro` | aspetto › Barra di stato | barre e action center › Barra di stato |
| Animazione di ricarica | `statusbar.charge` | aspetto › Barra di stato | barre e action center › Barra di stato |
| Barra di stato Windows Phone nelle altre app | `statusbar.apps` | aspetto › Barra di stato | barre e action center › Barra di stato |
| Barra di stato Windows Phone nelle mini app | `statusbar.mini` | aspetto › Barra di stato | barre e action center › Barra di stato |
| notifiche | `statusbar.notifications` | aspetto › Barra di stato | barre e action center › Barra di stato |
| modalità aereo | `statusbar.airplane` | aspetto › Barra di stato | barre e action center › Barra di stato |
| hotspot | `statusbar.hotspot` | aspetto › Barra di stato | barre e action center › Barra di stato |
| VPN | `statusbar.vpn` | aspetto › Barra di stato | barre e action center › Barra di stato |
| posizione | `statusbar.location` | aspetto › Barra di stato | barre e action center › Barra di stato |
| Bluetooth | `statusbar.bluetooth` | aspetto › Barra di stato | barre e action center › Barra di stato |
| sveglia | `statusbar.alarm` | aspetto › Barra di stato | barre e action center › Barra di stato |
| silenzioso e vibrazione | `statusbar.sound` | aspetto › Barra di stato | barre e action center › Barra di stato |
| non disturbare | `statusbar.dnd` | aspetto › Barra di stato | barre e action center › Barra di stato |
| risparmio energetico | `statusbar.saver` | aspetto › Barra di stato | barre e action center › Barra di stato |
| segnale della rete mobile | `statusbar.signal` | aspetto › Barra di stato | barre e action center › Barra di stato |
| tipo di rete (5G, 4G, 3G, E) | `statusbar.network` | aspetto › Barra di stato | barre e action center › Barra di stato |
| Wi-Fi | `statusbar.wifi` | aspetto › Barra di stato | barre e action center › Barra di stato |
| percentuale della batteria | `statusbar.batteryPercent` | aspetto › Barra di stato | barre e action center › Barra di stato |
| batteria | `statusbar.battery` | aspetto › Barra di stato | barre e action center › Barra di stato |
| ora | `statusbar.time` | aspetto › Barra di stato | barre e action center › Barra di stato |
| Giochi per cui le barre se ne vanno | `gest.gameapps` | uso › gesti | barre e action center › barra di navigazione |
| Nascondi le barre nei giochi | `gest.gamebars` | uso › gesti | barre e action center › barra di navigazione |
| Distanza tra i tasti | `gest.navspacing` | uso › gesti | barre e action center › barra di navigazione |
| Tempo per le app recenti | `gest.navbarhold` | uso › gesti | barre e action center › barra di navigazione |
| Pulsanti di navigazione Windows Phone | `gest.navbar` | uso › gesti | barre e action center › barra di navigazione |
| azioni rapide | `ac.buttons` | uso › action center | barre e action center › action center |
| Pulsanti con Shizuku | `ac.shizuku` | uso › action center | barre e action center › action center |
| Spegni la torcia dopo | `ac.torchoff` | uso › action center | barre e action center › action center |
| Volumi preimpostati nell'Action Center | `ac.profiles` | uso › action center | barre e action center › action center |
| volume e luminosità nell'Action Center | `ac.sliders` | uso › action center | barre e action center › action center |
| Usa l’Action Center | `ac.enabled` | uso › notifiche | barre e action center › action center |
| Action Center sopra le altre app | `ac.edge` | uso › notifiche | barre e action center › action center |
| Pannello del volume Metro | `ac.volume` | uso › action center | barre e action center › action center |

### gesti e scorciatoie

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| doppio tocco su Start | `gest.doubletap` | uso › gesti | gesti e scorciatoie › gesti |
| Home premuto due volte | `gest.homedouble` | uso › gesti | gesti e scorciatoie › gesti |
| swipe a due dita verso il basso | `gest.twodown` | uso › gesti | gesti e scorciatoie › gesti |
| swipe a due dita verso l'alto | `gest.twoup` | uso › gesti | gesti e scorciatoie › gesti |
| app recenti | `gest.recents` | uso › gesti | gesti e scorciatoie › gesti |
| servizio per bloccare lo schermo | `gest.lockservice` | uso › gesti | gesti e scorciatoie › gesti |
| tira giù dal bordo alto | `gest.edgepull` | uso › gesti | gesti e scorciatoie › gesti |
| due dita in una mini app: torna a Start | `gest.minihome` | uso › gesti | gesti e scorciatoie › gesti |
| gira pagina dal bordo dello schermo | `gest.edgeback` | uso › gesti | gesti e scorciatoie › gesti |
| suono al tocco | `gest.tapsound` | uso › gesti | gesti e scorciatoie › tocco e sensazione |
| Velocità delle animazioni | `gest.animspeed` | uso › gesti | gesti e scorciatoie › tocco e sensazione |
| Inclinazione alla pressione | `gest.tilt` | uso › gesti | gesti e scorciatoie › tocco e sensazione |
| Forza della vibrazione di un tocco | `gest.vibration` | uso › gesti | gesti e scorciatoie › tocco e sensazione |
| vibrazione al tocco | `gest.taphaptics` | uso › gesti | gesti e scorciatoie › tocco e sensazione |
| Icona assistente su Start | `voice.icon` | uso › voce | gesti e scorciatoie › voce |
| microfono nella casella di ricerca | `voice.button` | uso › voce | gesti e scorciatoie › voce |
| preferisci il riconoscimento sul telefono | `voice.offline` | uso › voce | gesti e scorciatoie › voce |
| chiama subito | `voice.call` | uso › voce | gesti e scorciatoie › voce |
| provalo ora | `voice.try` | uso › voce | gesti e scorciatoie › voce |

### app e icone

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| Forma delle icone | `icons.shape` | aspetto › aspetto | app e icone › icone |
| Colore del riquadro dall'icona | `icons.tilecolors` | aspetto › aspetto | app e icone › icone |
| Icone vetro | `icons.glass` | aspetto › aspetto | app e icone › icone |
| Icone grandi sui riquadri | `icons.largetiles` | aspetto › aspetto | app e icone › icone |
| Cerca icon pack sul Play Store | `icons.find` | aspetto › aspetto | app e icone › icone |
| icone per app | `icons.perapp` | aspetto › aspetto | app e icone › icone |
| icon pack | `icons.pack` | aspetto › aspetto | app e icone › icone |
| app più usate | `apps.frequent` | app › lista app | app e icone › lista app |
| app nuove | `apps.new` | app › lista app | app e icone › lista app |
| elenco compatto | `apps.compact` | app › lista app | app e icone › lista app |
| filtro per categoria | `apps.categories` | app › lista app | app e icone › lista app |
| app nascoste | `apps.hidden` | app › lista app | app e icone › lista app |
| app rinominate | `apps.renamed` | app › lista app | app e icone › lista app |
| cerca tra i contatti | `search.contacts` | app › ricerca | app e icone › ricerca |
| cerca tra le impostazioni di Android | `search.settings` | app › ricerca | app e icone › ricerca |
| cerca in queste impostazioni | `search.launcher` | app › ricerca | app e icone › ricerca |
| Cerca nelle note e nelle cose da fare | `search.mini` | app › ricerca | app e icone › ricerca |
| cerca ciò che c'è su Start | `search.tiles` | app › ricerca | app e icone › ricerca |
| proponi la ricerca sul web | `search.web` | app › ricerca | app e icone › ricerca |
| ricerca web con | `search.engine` | app › ricerca | app e icone › ricerca |
| risposte nella casella di ricerca | `search.answers` | app › ricerca | app e icone › ricerca |
| Interruttori e modi del suono per nome | `search.commands` | app › ricerca | app e icone › ricerca |
| Suggerimento del giorno | `search.tip` | app › ricerca | app e icone › ricerca |
| Azioni nella casella di ricerca | `search.assistant` | app › ricerca | app e icone › ricerca |
| Chiamata intelligente | `search.dial` | app › ricerca | app e icone › ricerca |
| Scheda Oggi | `search.today` | app › ricerca | app e icone › ricerca |
| Meteo sulla scheda | `search.todayweather` | app › ricerca | app e icone › ricerca |
| Posti vicino a me | `search.todaynear` | app › ricerca | app e icone › ricerca |
| Data | `search.todaydate` | app › ricerca | app e icone › ricerca |
| Prossima sveglia | `search.todayalarm` | app › ricerca | app e icone › ricerca |
| conversioni di valuta | `search.currency` | app › ricerca | app e icone › ricerca |
| Cerca nelle notifiche | `search.notifications` | app › ricerca | app e icone › ricerca |
| Operatori nella ricerca | `search.operators` | app › ricerca | app e icone › ricerca |
| File nella ricerca | `search.files` | app › ricerca | app e icone › ricerca |
| cerca nel calendario | `search.calendar` | app › ricerca | app e icone › ricerca |
| ricorda le ricerche recenti | `search.remember` | app › ricerca | app e icone › ricerca |
| dimentica le ricerche recenti | `search.forget` | app › ricerca | app e icone › ricerca |

### mini app

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| Interruttori delle mini app (uno per opzione, vedi `MiniOptions`) | `mini.<app>.<opzione>` | app › mini app | mini app › mini app |

### notifiche

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| contenuti dal vivo delle altre app sui riquadri | `ac.peek` | uso › notifiche | notifiche › notifiche |
| rispondi dall'Action Center | `ac.reply` | uso › notifiche | notifiche › notifiche |
| raggruppa le notifiche per app | `ac.group` | uso › notifiche | notifiche › notifiche |
| accesso alle notifiche | `ac.access` | uso › notifiche | notifiche › notifiche |
| Banner per le nuove notifiche | `ac.banner` | uso › notifiche | notifiche › notifiche |
| Banner sopra le altre app | `ac.overlay` | uso › notifiche | notifiche › notifiche |
| Silenzia un'app dal pannello | `ac.mute` | uso › notifiche | notifiche › notifiche |
| Togli le notifiche quando apro l'app | `ac.clearonopen` | uso › notifiche | notifiche › notifiche |
| Prima messaggi e chiamate | `ac.priority` | uso › notifiche | notifiche › notifiche |
| Escludi notifiche con certe parole | `ac.filter` | uso › notifiche | notifiche › notifiche |
| Parole da escludere | `ac.filterwords` | uso › notifiche | notifiche › notifiche |
| Durata del silenzioso a tempo | `ac.dndminutes` | uso › notifiche | notifiche › notifiche |
| Suono del pannello | `ac.panelsounds` | uso › notifiche | notifiche › notifiche |
| Suoni Metro | `ac.sounds` | uso › notifiche | notifiche › notifiche |
| Nascondi la notifica di Android | `ac.hide` | uso › notifiche | notifiche › notifiche |
| Notifiche app per app | `ac.notifcontrol` | uso › notifiche | notifiche › notifiche |
| App | `ac.notifapps` | uso › notifiche | notifiche › notifiche |

### privacy e sicurezza

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| app bancarie | `gest.banksafe` | uso › gesti | privacy e sicurezza › protezione |
| app dietro il blocco schermo | `apps.locked` | app › lista app | privacy e sicurezza › protezione |
| avvia angolo bambini | `kids.start` | strumenti › angolo bambini | privacy e sicurezza › angolo bambini |
| app | `kids.apps` | strumenti › angolo bambini | privacy e sicurezza › angolo bambini |

### batteria

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| risparmio energetico | `battery.saving` | uso › batteria | batteria › batteria |

### accessibilità

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| dimensione del testo | `access.textsize` | aspetto › accessibilità | accessibilità › accessibilità |
| testo marcato | `access.bold` | aspetto › accessibilità | accessibilità › accessibilità |
| carattere del telefono | `access.systemfont` | aspetto › accessibilità | accessibilità › accessibilità |
| Carattere | `access.font` | aspetto › accessibilità | accessibilità › accessibilità |
| riduci il movimento | `access.motion` | aspetto › accessibilità | accessibilità › accessibilità |
| contrasto elevato | `access.contrast` | aspetto › accessibilità | accessibilità › accessibilità |
| TalkBack | `access.talkback` | aspetto › accessibilità | accessibilità › accessibilità |

### backup e profili

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| metti in pausa | `profile.pause` | app › profili | backup e profili › profili |
| mostra le sue app nella lista | `profile.showapps` | app › profili | backup e profili › profili |
| cartella di backup | `backup.folder` | strumenti › backup+sync | backup e profili › backup+sync |
| backup automatico | `backup.every` | strumenti › backup+sync | backup e profili › backup+sync |
| App installate | `backup.apps` | strumenti › backup+sync | backup e profili › backup+sync |
| Backup conservati | `backup.copies` | strumenti › backup+sync | backup e profili › backup+sync |
| Invia il layout | `backup.share` | strumenti › backup+sync | backup e profili › backup+sync |
| salva il backup ora | `backup.now` | strumenti › backup+sync | backup e profili › backup+sync |
| ripristina dal backup | `backup.restore` | strumenti › backup+sync | backup e profili › backup+sync |
| non usare più questa cartella | `backup.forget` | strumenti › backup+sync | backup e profili › backup+sync |
| mantieni Start sincronizzato | `sync.enable` | strumenti › backup+sync | backup e profili › backup+sync |
| passphrase | `sync.passphrase` | strumenti › backup+sync | backup e profili › backup+sync |
| sincronizza ora | `sync.now` | strumenti › backup+sync | backup e profili › backup+sync |
| esporta il layout | `backup.export` | strumenti › backup+sync | backup e profili › backup+sync |
| importa un layout | `backup.import` | strumenti › backup+sync | backup e profili › backup+sync |

### informazioni

| impostazione | id | pagina vecchia | pagina nuova |
|---|---|---|---|
| descrivi qui il problema | `bugreport.describe` | strumenti › segnalazione bug | informazioni › segnalazione bug |
| prepara la mail | `bugreport.send` | strumenti › segnalazione bug | informazioni › segnalazione bug |
| versione | `about.version` | strumenti › informazioni | informazioni › informazioni |
| Aggiornamenti | `about.update` | strumenti › informazioni | informazioni › informazioni |
| Cerca aggiornamenti adesso | `about.updatecheck` | strumenti › informazioni | informazioni › informazioni |
| Cerca aggiornamenti ogni 12 ore | `about.updateauto` | strumenti › informazioni | informazioni › informazioni |
| Racconta del telefono | `about.phoneinfo` | strumenti › informazioni | informazioni › informazioni |
| Questo telefono | `about.phone` | strumenti › informazioni | informazioni › informazioni |
| rivedi il tutorial | `about.tutorial` | strumenti › informazioni | informazioni › informazioni |
| Software libero (FOSS) | `projects.foss` | strumenti › altri progetti | informazioni › altri progetti |
| Non FOSS | `projects.nonfoss` | strumenti › altri progetti | informazioni › altri progetti |
| crediti | `about.credits` | strumenti › informazioni | informazioni › informazioni |

## Spostamenti

Le impostazioni che hanno cambiato categoria o pagina, e perché:

| impostazione | da | a | perché |
|---|---|---|---|
| nascondi la barra di stato (`statusbar.hide`) | aspetto › barre e orologio | barre e action center › Barra di stato | è la barra di stato: sta con le altre impostazioni della barra |
| Forma delle icone (`icons.shape`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| Colore del riquadro dall'icona (`icons.tilecolors`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| Giochi per cui le barre se ne vanno (`gest.gameapps`) | uso › gesti | barre e action center › barra di navigazione | sono i pulsanti MetroWin, non un gesto: stanno con le barre |
| Nascondi le barre nei giochi (`gest.gamebars`) | uso › gesti | barre e action center › barra di navigazione | sono i pulsanti MetroWin, non un gesto: stanno con le barre |
| Distanza tra i tasti (`gest.navspacing`) | uso › gesti | barre e action center › barra di navigazione | sono i pulsanti MetroWin, non un gesto: stanno con le barre |
| Tempo per le app recenti (`gest.navbarhold`) | uso › gesti | barre e action center › barra di navigazione | sono i pulsanti MetroWin, non un gesto: stanno con le barre |
| Icone vetro (`icons.glass`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| Icone grandi sui riquadri (`icons.largetiles`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| Cerca icon pack sul Play Store (`icons.find`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| icone per app (`icons.perapp`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| icon pack (`icons.pack`) | aspetto › aspetto | app e icone › icone | le icone sono delle app: stanno con la lista delle app, non con i colori |
| app bancarie (`gest.banksafe`) | uso › gesti | privacy e sicurezza › protezione | tutela dei dati: stanno con la protezione, non con i gesti o la lista delle app |
| Pulsanti di navigazione Windows Phone (`gest.navbar`) | uso › gesti | barre e action center › barra di navigazione | sono i pulsanti MetroWin, non un gesto: stanno con le barre |
| suono al tocco (`gest.tapsound`) | uso › gesti | gesti e scorciatoie › tocco e sensazione | tocco, vibrazione e velocità riguardano come si sente il telefono, non un gesto |
| Velocità delle animazioni (`gest.animspeed`) | uso › gesti | gesti e scorciatoie › tocco e sensazione | tocco, vibrazione e velocità riguardano come si sente il telefono, non un gesto |
| Inclinazione alla pressione (`gest.tilt`) | uso › gesti | gesti e scorciatoie › tocco e sensazione | tocco, vibrazione e velocità riguardano come si sente il telefono, non un gesto |
| Forza della vibrazione di un tocco (`gest.vibration`) | uso › gesti | gesti e scorciatoie › tocco e sensazione | tocco, vibrazione e velocità riguardano come si sente il telefono, non un gesto |
| vibrazione al tocco (`gest.taphaptics`) | uso › gesti | gesti e scorciatoie › tocco e sensazione | tocco, vibrazione e velocità riguardano come si sente il telefono, non un gesto |
| Usa l’Action Center (`ac.enabled`) | uso › notifiche | barre e action center › action center | accendere o spegnere l'Action Center è una scelta dell'Action Center |
| Action Center sopra le altre app (`ac.edge`) | uso › notifiche | barre e action center › action center | accendere o spegnere l'Action Center è una scelta dell'Action Center |
| app dietro il blocco schermo (`apps.locked`) | app › lista app | privacy e sicurezza › protezione | tutela dei dati: stanno con la protezione, non con i gesti o la lista delle app |

## Campi di `LauncherSettings` e ripristino di pagina

Il pulsante "ripristina questa pagina" (`SectionReset`) rimette ai valori di partenza solo i campi della pagina aperta. Ogni campo è in una sola pagina. Chiavi per pagina:

- **aspetto e temi › tema e colori** (`StartTheme`): `accentApps`, `accentIndex`, `accentNightIndex`, `accentPanel`, `accentStart`, `autoAccent`, `customAccent`, `darkFromHour`, `darkToHour`, `dynamicColor`, `miniAccents`, `oledMode`, `pageBackgrounds`, `panoramaTitles`, `parallax`, `startPhotoFit`, `startPhotoInside`, `startPhotoX`, `startPhotoY`, `startPhotoZoom`, `systemWallpaperFollows`, `themeMode`, `themeStyle`, `tileOpacity`
- **app e icone › icone** (`Icons`): `glassIcons`, `iconPack`, `iconPicks`, `iconShape`, `iconTileColors`, `largeTileIcons`
- **start e tile › schermata start** (`Start`): `appsHint`, `batteryEstimate`, `calendarAgenda`, `collectionTiles`, `favoritePage`, `freeLayout`, `groupTitles`, `largeTiles`, `layoutSchedule`, `moreTiles`, `showTileNames`, `tileColumns`, `tileMusicControls`, `tileSpacing`, `twoPanels`
- **start e tile › orologio e barre** (`StartBars`): `actionBar`, `appBar`, `clockAlarm`, `clockHours`, `clockTaps`, `clockWeek`, `clockZone`, `showClock`
- **barre e action center › Barra di stato** (`StatusBar`): `chargeAnimation`, `hideStatusBar`, `metroStatusBar`, `metroStatusBarOverApps`, `statusBarHidden`
- **start e tile › comportamento di start** (`StartBehaviour`): `antiBurnIn`, `lockStart`, `startLowerPercent`, `startTopAfterMin`, `stepGoal`, `undoSteps`
- **app e icone › ricerca** (`Search`): `rememberSearches`, `searchAnswers`, `searchAssistant`, `searchCalendar`, `searchCommands`, `searchContacts`, `searchCurrency`, `searchEngine`, `searchFileTypes`, `searchLauncherSettings`, `searchMiniApps`, `searchNotifications`, `searchOperators`, `searchSettings`, `searchSmartDial`, `searchTiles`, `searchToday`, `searchWeb`, `tipOfDay`, `todayAlarm`, `todayDate`, `todayNear`, `todayWeather`
- **gesti e scorciatoie › gesti** (`Gestures`): `doubleTapAction`, `doubleTapApp`, `edgeBackPan`, `edgePull`, `homeDoubleAction`, `homeDoubleApp`, `miniHomeSwipe`, `twoFingerDownAction`, `twoFingerDownApp`, `twoFingerUpAction`, `twoFingerUpApp`
- **gesti e scorciatoie › tocco e sensazione** (`Feel`): `animationSpeed`, `tapHaptics`, `tapSound`, `tiltStrength`, `vibrationStrength`
- **barre e action center › barra di navigazione** (`NavBar`): `gameAdd`, `gameSkip`, `hideBarsInGames`, `metroNavBar`, `navHoldMs`, `navSpacing`
- **privacy e sicurezza › protezione** (`Privacy`): `bankSafe`, `bankSafeAdd`, `bankSafeSkip`
- **barre e action center › action center** (`Actions`): `actionCenterEnabled`, `actionSliders`, `edgeActionCenter`, `quickActions`, `torchAutoOffMin`, `torchStrength`, `useShizuku`, `volumePanel`, `volumeProfilesShow`, `vpnApp`
- **notifiche › notifiche** (`Notifications`): `clearOnOpen`, `dndMinutes`, `groupConversations`, `hideSystemNotifications`, `inlineReply`, `metroSounds`, `muteFromPanel`, `notifFilterOn`, `notifFilterWords`, `notifPriority`, `notificationPeek`, `overlayBanner`, `panelSounds`, `toastBanner`
- **start e tile › live tile** (`LiveTiles`): `galleryAlbums`, `galleryApp`, `galleryPhotos`, `gallerySource`, `liveHideText`, `liveMessages`, `liveNowPlaying`, `liveOffApps`, `livePictures`, `liveProgress`, `thirdPartyTiles`, `weatherBlocks`, `weatherFahrenheit`
- **app e icone › lista app** (`Apps`): `appLabels`, `compactList`, `showCategories`, `showFrequentApps`, `showNewApps`
- **aspetto e temi › schermo+lingua** (`Screen`): `highRefreshRate`, `launchAnimation`, `orientation`, `pagePadding`
- **batteria › batteria** (`Battery`): `batterySaving`
- **accessibilità › accessibilità** (`Accessibility`): `appFont`, `boldText`, `fontRevision`, `highContrast`, `reduceMotion`, `systemFont`, `textScale`
- **schermata di blocco › blocco schermo** (`Lock`): `lockIconsOneRow`, `lockOverlay`, `lockSensorOwnIcon`, `lockSensorUnderScreen`, `lockSensorY`, `lockShowAlarm`, `lockShowBattery`, `lockShowEvent`, `lockShowNotifications`, `lockShowWeather`
- **gesti e scorciatoie › voce** (`Voice`): `assistantIcon`, `voiceButton`, `voiceCallDirect`, `voiceOffline`

I campi che nessuna pagina ripristina sono dati dell'utente (liste, app nascoste e bloccate, città del meteo, disposizioni salvate, stato di backup e sync) e impostazioni senza un pulsante di ripristino: l'elenco è `KEPT` in `SettingsCoverageTest`.
