# Super tile (InfoTile): 50 migliorie e 50 nuove funzioni

**Stato (dopo il lavoro che ha applicato le proposte)**: fatte tutte tranne tre, vedi `docs/supertile-editor.md`.
- A35 (miniature della misura del tile): non fatta, resta 720 px per tutte.
- B15: il tocco su Persone apre Persone, non chiama.
- B39: «ospite» non esiste: vale solo l'angolo bambini.
- A16 (osservatore del calendario), A17 (apri il giorno), A19 (apri le previsioni), A21 (meteo vecchio): fatte ma solo provate nei test e non su telefono.

Solo ricerca: nessuna modifica al codice. Letti: `model/InfoConfig.kt`, `ui/start/InfoTile.kt`, `data/InfoPriority.kt`, `data/LiveTiles.kt`, `data/PowerPolicy.kt` (`LiveSources`), `ui/anim/Motion.kt` (`FlipContainer`), `ui/start/TileFaces.kt`, `TimeFaces.kt`, `WeatherFaces.kt`, `PhotoFaces.kt`, `DeviceFaces.kt`, `ExtraFaces.kt`, `data/Shortcuts.kt`, `data/Weather.kt`, `WeatherController.kt`, `data/CalendarSearch.kt`, `data/PhotoMemories.kt`, `LauncherViewModel.kt` (`refreshLive`, `addInfo`, `saveInfo`), `LauncherApp.kt` (tocco su `ROUTE_INFO`), `StartScreen.kt` (semantica dei tile), i tre test `InfoTileTest`, `InfoTileMoreTest`, `screens/InfoTileUiTest`, il manifest.

Percorsi `…/` = `app/src/main/java/com/metrowin/launcher/`.
Peso: **L** meno di 1 h · **M** mezza giornata · **H** più giorni.
Segni nella colonna «come»: **[difetto]** = visto leggendo il codice (riga indicata); **[perm. nuovo]**, **[rete]**, **[libreria]** = richiede qualcosa che oggi non c'è. Se non c'è segno, la proposta usa solo ciò che c'è già (permessi già nel manifest: calendario, contatti, registro chiamate, posizione, riconoscimento attività, accesso alle notifiche, accesso all'uso, Bluetooth, `INTERNET`).
Colonna «opz.»: sì = interruttore spento di default (tocca privacy, rete, batteria o permessi).

Non c'è nessun «verificato a schermo»: lettura del codice soltanto, niente emulatore né test eseguiti. Ciò che riguarda tempi e aspetto è dedotto dal codice (vedi «non verificato» in fondo).

---

## Tabella A — 50 migliorie di ciò che esiste

| n. | Proposta | Perché serve | Come (file/classi) | Peso | Rischio / costo | Opz. |
|---|---|---|---|---|---|---|
| A1 | Con «riduci movimento» o in risparmio il tile non resta fermo sulla prima faccia: cambia faccia con dissolvenza (o di colpo) allo stesso ritmo, e in risparmio a ritmo lento | Oggi chi ha «riduci movimento» vede solo l'ora per sempre: le altre facce non esistono per lui | **[difetto]** `ui/anim/Motion.kt:341` (`if (faces <= 1 \|\| reduced \|\| saving) return`) lascia `index` a 0; in `FlipContainer` un ramo «senza rotazione» che cambia `index` con `Crossfade`; `InfoFace` lo usa (`InfoTile.kt:94`) | M | Un timer ogni 5–10 s mentre Start è a schermo; in risparmio ritmo ×4 | no |
| A2 | Le facce Calendario, Meteo e Batteria dentro l'InfoTile non hanno un secondo giro proprio: la faccia interna non gira da sola | Oggi due giri indipendenti: l'esterno cambia ogni 5–8 s, l'interno (data→evento, ora→giorni…) parte dopo 1,5–5,5 s e non riceve il ritmo scelto: con «veloce» l'evento spesso non si vede mai | **[difetto]** `CalendarFace`/`WeatherFace`/`BatteryFace` hanno ciascuna un `FlipContainer` (`TimeFaces.kt` ~249, `WeatherFaces.kt` ~80, `DeviceFaces.kt` ~47) senza `pace`; aggiungere un parametro «nested/fermo» e far elencare a `InfoFace` le sotto-facce (evento 1, evento 2, ora/giorni…) in un solo giro | M | Nessuno (meno animazioni parallele = meno lavoro) | no |
| A3 | Smart «evento entro l'ora resta da solo»: mostra davvero l'evento, non l'alternanza data/evento | Se `smart` lascia solo Calendario, la faccia interna continua a girare tra il numero del giorno e l'evento: metà del tempo non si vede ciò che era «importante» | **[difetto]** `InfoPriority.order` → una sola faccia → `CalendarFace` con 2 sotto-facce; parametro `eventOnly` per `CalendarFace` (o risolto da A2) | L | Nessuno | no |
| A4 | Tempo di sosta in base a ciò che c'è da leggere (evento con titolo lungo più a lungo, ora più corta, foto 4,5 s) | Ritmo unico 5–8 s per tutte: il titolo di 3 righe non si fa leggere, l'ora è già letta in 1 s | `FlipContainer`: parametro `dwell: (face) -> Long` (oggi `delay` fisso `Motion.kt:345,359`); `InfoFace` lo calcola dalla lunghezza del testo e dal tipo | M | Nessuno | no |
| A5 | In risparmio mostra la faccia più utile ora (evento imminente, sveglia) invece di sempre la prima; rendere chiaro cosa fa l'interruttore «solo ora» | In risparmio il tile si ferma a `shown[0]`, di norma l'ora: l'interruttore `saver` ha quasi lo stesso effetto di non averlo; con `saver` spento nessuno sceglie cosa vedere | **[difetto]** `Motion.kt:341` + `InfoTile.kt:76-77`: in risparmio passare a `InfoPriority.order(...smart=true)` e fermarsi sulla prima; testo del dialogo `info_saver` | L | Nessuno | no |
| A6 | Il «minuto che passa» dell'InfoTile si ferma quando il launcher non è a schermo | Con la schermata spenta o un'altra app sopra, il tile continua a svegliare Compose ogni 60 s | **[difetto]** `InfoTile.kt:71` usa `LaunchedEffect(Unit)` con `delay(60_000)`: sostituire con `LaunchedWhileStarted` (`ui/anim/Active.kt`) come fanno `WorldClockFace` e `rememberToday` | L | Batteria: meno risvegli | no |
| A7 | Il tocco apre ciò che si vede davvero, anche con la transizione «Scorrimento» o a metà giro | Con `TileTransition.Slide` le due facce sono composte insieme e `SideEffect { InfoTileState.show(...) }` lascia vincere l'ultima composta (quella che entra): il tocco apre l'app sbagliata | **[difetto]** `InfoTile.kt:83`, `InfoPriority.kt:29-35`: scrivere lo stato solo con la faccia ferma (angolo 0) o con l'indice di `FlipContainer`; pulire la mappa quando il tile sparisce | L | Nessuno | no |
| A8 | Foto e Ricordi non ripartono sempre dalla prima foto | Ogni volta che la faccia compare, `CycleFace` ricrea `index = 0`: l'utente rivede sempre `photos[0]` e il giro interno è inutile dentro un giro esterno | **[difetto]** `PhotoFaces.kt:49-65`: nell'InfoTile mostrare una foto per apparizione, presa da un contatore (seed + giri), senza il ciclo interno | L | Nessuno | no |
| A9 | Batteria nell'ordine intelligente solo quando conta (≤ 20 %, in carica, piena) | Con smart attivo la faccia batteria gira anche all'87 %, rumore costante | `InfoPriority.order` riceve `BatteryInfo?`; soglia = `PowerPolicy.LOW_BATTERY_PERCENT`; test in `InfoTileMoreTest` | M | Nessuno | no |
| A10 | Pioggia in arrivo mette il Meteo in testa (con smart) | Il dato c'è già (`OpenMeteo.rainOutlook`) ma l'ordine non lo usa: l'ombrello arriva dopo la foto | `InfoPriority.order` + `live.weather.hours`; ordine: evento/sveglia/pioggia entro l'ora | M | Nessuno | no |
| A11 | «Notte» scelta dall'utente (due orari) e che finisce con la sveglia | 23:00–06:00 è fisso (`InfoPriority.isNight`): chi lavora di notte o si sveglia alle 5 sbaglia | Campi in `InfoConfig` (`;n0=`,`;n1=` nella stringa), righe nel dialogo, `isNight(hour, from, to)`; test | M | Nessuno | no |
| A12 | «Entro l'ora» (`SOON_MS`) a scelta: 15 / 30 / 60 / 120 min | Per un ufficio 1 h è tanto, per un viaggio poco | `InfoPriority.SOON_MS` → parametro; `InfoConfig` + riga nel dialogo | L | Nessuno | no |
| A13 | Senza eventi la faccia Calendario non compare | `hasData(Calendar)` è sempre `true`: senza eventi mostra solo il numero del giorno, già scritto nell'etichetta della faccia Ora → due facce quasi uguali di fila | **[difetto]** `InfoTile.kt:54` → `live.nextEvent != null` (oppure data solo se Ora è spenta); aggiornare `InfoTileMoreTest` | L | Nessuno | no |
| A14 | L'evento ha un'ora di fine: «in corso, finisce alle 15:30» e gli eventi finiti spariscono dalla faccia | `LiveEvent` ha solo l'inizio: un evento già finito resta su fino alla prossima lettura (fino a 2 min dopo il rientro, mai mentre Start resta acceso) e l'evento «in corso» non si distingue | **[difetto]** `data/LiveTiles.kt:59` (`LiveEvent`) + `CalendarSearch.upcoming` (la colonna `END` è già letta: `getLong(2)`); filtro con il minuto di `InfoFace`; `describeWhen` | M | Nessuno | no |
| A15 | Dentro l'ora: «tra 25 min» al posto di «oggi 15:30» | Più veloce da leggere a colpo d'occhio | `describeWhen` in `TimeFaces.kt` (stringa con plurale in 6 lingue) | L | Nessuno | no |
| A16 | Il calendario si aggiorna da solo quando cambia un evento | Oggi la lettura parte solo al rientro su Start (`refreshLive`, soglia 2 min): un evento aggiunto mentre si guarda Start non appare. Il `ContentObserver` esiste solo in `calendar/CalendarScreen.kt:125` | Observer su `CalendarContract.CONTENT_URI` registrato nel `ViewModel` solo con Start a schermo e almeno un tile con `LiveKind.Calendar`; poi `refreshLive(force)` limitato | M | Batteria: sveglia solo a cambio; spento senza tile calendario | no |
| A17 | Toccare l'evento apre quel giorno (o quell'evento) nel Calendario, non solo l'app | Il tocco su Calendario apre la home dell'app, l'utente deve cercare l'evento | `openInfoPart` con extra data; `CalendarActivity` legge un extra; serve l'id in `LiveEvent` | M | Nessuno | no |
| A18 | Il titolo dell'evento si adatta al tile (riduce, 2–3 righe, puntini) senza uscire | 22 sp fissi, 3 righe: in un tile Medio con testo grande il titolo copre il nome | `ui/components/FitHeight.kt` già esiste; usarlo in `CalendarFace` (ramo «else» finale) | L | Nessuno | no |
| A19 | Il tocco sul Meteo apre le previsioni, non la scelta del tile | `openInfoPart` dice «il meteo non ha una pagina» ma `WeatherScreen` esiste (la scheda Oggi la apre): chi tocca il meteo si trova il dialogo di configurazione | **[difetto]** `InfoTile.kt:107`, `LauncherApp.kt:800-803`; passare un callback `onOpenWeather` al posto del solo `Context` | M | Nessuno | no |
| A20 | La faccia Meteo dice la città | Il meteo è di una sola città globale; sul tile c'è scritto «Info» e non si sa di dove sia | `WeatherFace` mostra `settings.weatherCity` al posto di `item.label` quando in un InfoTile (`LocalTileFeatures`) | L | Nessuno | no |
| A21 | Meteo vecchio segnalato («dato di 5 h fa», testo attenuato) | Senza rete il meteo resta com'era, anche vecchio di giorni, e sembra attuale | `WeatherData.fetchedAt` già c'è; soglia in `WeatherFace` | L | Nessuno | no |
| A22 | Ora in un altro fuso: scrivi la differenza («+6 h») e l'ora di casa piccola | Nella faccia Ora con fuso scelto non si capisce quanto sia lontano | `WorldCities.timeIn` dà già `dayOffset`; aggiungere differenza oraria in `WorldClockFace` | L | Nessuno | no |
| A23 | Fuso non valido o assente: l'ora ricade sul fuso del telefono, non sull'icona | `WorldClockFace` con `ZoneId.of` che fallisce disegna `StaticFace`: la faccia Ora diventa un glifo senza ora | **[difetto]** `TimeFaces.kt:~109`; `InfoFace` passa `zone.ifEmpty { default }` ma uno ID malato (backup importato) non è validato | L | Nessuno | no |
| A24 | La faccia Sveglia segue l'impostazione «ore 24 / 12» dello Start | `TileFeatures.clockHours` dice che «la sveglia segue l'ora di Start» ma `AlarmFace` usa solo `is24HourFormat(context)` (la faccia Ora la usa): stesso tile, due formati | **[difetto]** `TimeFaces.kt` (`AlarmFace`, riga `val is24 = …`) → `LocalTileFeatures.current.clockHours.is24(...)` | L | Nessuno | no |
| A25 | Faccia Ora su Wide/Large usa lo spazio: ora a sinistra, giorno e data a destra | Oggi stesso layout al centro per Wide e Large (cifre al 30 % dell'altezza) con metà tile vuota | `WorldClockFace`: ramo per `TileSize.Wide/Large` | M | Nessuno | no |
| A26 | La cache delle miniature regge le foto che servono | `MAX_PHOTOS = 8` per Foto + Album + Ricordi (6+6+6 = 18 foto): ogni lettura decodifica di nuovo e scarta le altre | **[difetto]** `data/LiveTiles.kt:188-190,397`: ≥ 24 oppure una cache per tipo; memoria ≈ 24 × 1,5 MB | L | Memoria +~12 MB; meno CPU | no |
| A27 | I Ricordi non si rileggono ogni 2 minuti e non si rimescolano a ogni lettura | `LiveKind.Memories` non è in `PowerPolicy.liveMaxAgeMs`: vale il default di 2 min → scansione di 3000 righe + decodifica di 6 foto a ogni rientro, e foto diverse ogni volta (cambia solo una volta al giorno) | **[difetto]** `data/PowerPolicy.kt:42-50` → `Memories -> 6 * HOUR`; scelta delle foto con seme = data (`LiveTiles.kt:250`) | L | Meno batteria e CPU | no |
| A28 | I Ricordi cercano in tutto l'archivio, non nelle ultime 3000 foto | `MEMORY_SCAN = 3000` ordinate per data scatto: chi ha tante foto non vede mai i ricordi più vecchi (proprio i più belli) | **[difetto]** `LiveTiles.kt:234-252`: una query per anno con `DATE_TAKEN BETWEEN` l'inizio e la fine di oggi (fino a 20 query indicizzate, `LIMIT` per anno) al posto della scansione + `PhotoMemories.isMemory` | M | Nessuno (query leggere) | no |
| A29 | Didascalia «3 anni fa» sul ricordo | Il ricordo non dice che è un ricordo né di quando | `LiveTileData.memories` diventa `List<MemoryPhoto(bitmap, year, uri)>`; `CycleFace` disegna la didascalia (anche per A30) | M | Nessuno | no |
| A30 | Toccando la foto si apre quella foto | Oggi si apre l'hub foto generico (`photohub`): non si ritrova la foto vista | Serve l'URI (da A29); `openInfoPart` → `ACTION_VIEW` sull'URI o `PhotosActivity` con extra | M | Nessuno | no |
| A31 | Escludere screenshot e cartelle scelte da Foto e Ricordi | «Ultime foto» include screenshot, immagini ricevute da chat, scansioni: sul tile di Start le vedono tutti | Colonna `BUCKET_DISPLAY_NAME`/`RELATIVE_PATH` in `recentPhotos`/`memoryPhotos`; elenco `;x=` nella config; riga nel dialogo | M | Privacy (migliora) | sì |
| A32 | I Ricordi rispettano l'album scelto | Il dialogo dice «album» ma solo Foto lo usa: i Ricordi pescano da tutta la libreria | `memoryPhotos(bucket)` come `recentPhotos`; `liveKinds` | L | Nessuno | no |
| A33 | 29 febbraio: ricordo mostrato il 28 febbraio negli anni non bisestili | `PhotoMemories.isMemory` confronta mese e giorno: le foto del 29/2 non compaiono mai 3 anni su 4 | `data/PhotoMemories.kt` + test (come `Birthdays.nextDate`) | L | Nessuno | no |
| A34 | Etichetta delle foto: niente nome «Info» sulle foto, nulla su Small | `CycleFace` disegna sempre `PhotoLabel(item.label)` (le altre facce lo nascondono su Small): su un tile piccolo il nome copre la foto | `PhotoFaces.kt:62` + parametro `size`; didascalia da A29 | L | Nessuno | no |
| A35 | Miniature della dimensione che serve al tile (Small 240 px, Medium 480, Wide/Large 720) | `PHOTO_PX = 720` per tutti: su 1×1 si decodifica 9× di pixel inutili, e ×18 foto | `LiveTiles.kt` (`thumbnail(uri, size)`) con il tile più grande che usa quella lista | M | Memoria e CPU in meno | no |
| A36 | TalkBack legge un riepilogo dell'InfoTile (ora, prossimo evento, sveglia, meteo, batteria) | Il tile ha `live = null`: nessuna `stateDescription` (si ha solo quella del Calendario o Todo). TalkBack legge solo la faccia che capita | **[difetto]** `ui/start/StartScreen.kt:1100-1117` (`next` solo per `LiveKind.Calendar/Todo`); costruire il riepilogo da `InfoConfig` + `LiveTileData` | M | Nessuno | no |
| A37 | Le foto dicono cosa sono a TalkBack («Ricordo di 3 anni fa», «Foto del 12 marzo») | `contentDescription = null` su ogni `Image` delle facce foto | `PhotoFaces.kt`, uso della data di A29 | L | Nessuno | no |
| A38 | Con TalkBack acceso il tile non gira da solo; azione «Faccia successiva» nel menu delle azioni | Un contenuto che cambia da solo è difficile da seguire; le azioni personalizzate del tile ci sono già (sposta, ridimensiona) | `AccessibilityManager.isTouchExplorationEnabled` → `FlipContainer` fermo; `CustomAccessibilityAction` in `StartScreen.kt:1123` | M | Nessuno | no |
| A39 | Il dialogo dice lo stato di ogni faccia («manca il permesso», «nessuna città», «nessuna sveglia», «nessun ricordo oggi») | Una faccia accesa che non ha dati sparisce in silenzio dal giro (`hasData`): l'utente pensa che sia rotta | `InfoTileDialog` riceve `LiveTileData` + permessi; sottotitolo sotto ogni interruttore | M | Nessuno | no |
| A40 | Il dialogo chiede il permesso quando si accende Calendario, Foto o Ricordi | Oggi si accende l'interruttore e non succede nulla finché non si va nelle impostazioni | `LivePermissions` + `rememberLauncherForActivityResult` nel dialogo (come in `LiveTilesPage.kt:130`) | M | Permessi già dichiarati (nessuno nuovo) | no |
| A41 | Ordine delle facce scelto dall'utente | L'ordine è fisso (Ora, Calendario, Sveglia, Meteo, Batteria, Foto, Ricordi; test `theFacesTurnInAFixedOrder`) | `InfoConfig.parts` segue l'ordine delle lettere della stringa (oggi `InfoPart.entries.filter`); frecce su/giù nel dialogo | M | Nessuno | no |
| A42 | Anteprima viva nel dialogo | Si cambia e si chiude per vedere l'effetto | `InfoFace` con dati finti dentro `InfoTileDialog` | M | Nessuno | no |
| A43 | Nascondere i titoli degli eventi (e, a scelta, le foto) quando altri guardano lo schermo | Il titolo «Visita oncologica» sta in chiaro sulla Start; l'interruttore «nascondi il testo» esiste solo per le notifiche (`live_hide_text`) | Campo `;h=` in `InfoConfig`; `CalendarFace` mostra solo l'ora; riusare la stringa | M | Privacy (migliora) | sì |
| A44 | Se manca l'app Orologio, il tocco su Ora non apre il dialogo di configurazione ma un avviso | `openInfoPart` false → `adding.infoEditingId = item.id`: tocchi un orologio e si apre un modulo | `InfoTile.kt:101-109`, `LauncherApp.kt:800-803`: avviso con `MetroToast` per i casi non meteo | L | Nessuno | no |
| A45 | Test Compose di `InfoFace`: facce senza dati escluse, `saver`, movimento ridotto, ordine smart a schermo | I tre test esistenti coprono stringa, priorità e dialogo: niente copre la faccia vera (A1, A2, A13 sarebbero stati visti) | `screens/InfoTileUiTest`: `InfoFace` con `LiveTileData` finto e `LocalReduceMotion`/`LocalPowerSaving` | M | Nessuno | no |
| A46 | Test dei casi limite di `PhotoMemories` e `InfoPriority` (29/2, capodanno, 22:59/23:00, evento di 15 min fa) | I confini sono dove nascono gli errori | `InfoTileMoreTest` | L | Nessuno | no |
| A47 | Test di `openInfoPart` e `InfoTileState` (ogni faccia → l'intent giusto, nessuna app → `false`) | Nessun test sul tocco | `InfoTileTest` con `Context` finto/Robolectric se già usato nel progetto | M | Nessuno | no |
| A48 | `InfoConfig.decode` conserva le chiavi sconosciute e valida i valori | Un backup di una versione più nuova perde `;chiavi` al primo salvataggio; `zone` o `album` illeggibili passano così come sono | `InfoConfig.decode/encode`: mappa extra non letta riscritta; prefisso di versione; test round-trip con `;`, `=` e unicode nei nomi | M | Nessuno | no |
| A49 | Test di `LiveTileRepository.memoryPhotos` e `recentPhotos` con un `ContentProvider` finto | Oggi non c'è: né il limite né l'ordine né il filtro sono provati | `test/` con `MockContentProvider`/`MatrixCursor` (vedi `PhotoReader.parse`) | M | Nessuno | no |
| A50 | La città della faccia Ora nella lingua dell'app | Si salva solo la zona e l'etichetta è l'ID inglese («Rome», «Sao Paulo»); `City` ha già nome inglese e italiano | `InfoTile.kt:80` (`substringAfter('/')`) → salvare il nome scelto (`;zn=`) o cercarlo con `WorldCities` | L | Nessuno | no |

---

## Tabella B — 50 nuove funzioni

Molte facce nuove riusano composable e dati dei tile che esistono già (separati dal super tile). Per le facce che leggono `TileExtras` (passi, app, dati, batteria a grafico) `refreshExtras` guarda i `TileData` pinnati (`LauncherViewModel.kt` ~2029-2060) e va esteso agli `InfoConfig`; per le altre basta `InfoConfig.liveKinds()` (come per Calendario/Foto).

| n. | Proposta | Perché serve | Come (file/classi) | Peso | Rischio / costo | Opz. |
|---|---|---|---|---|---|---|
| B1 | Faccia «Da fare»: quante cose aperte e le prime | Il Todo c'è come tile ma non nel super tile: chi ne ha uno solo lo vuole dentro | `InfoPart.Todo('d')`, `TodoFace` (già c'è), `liveKinds → LiveKind.Todo`, riga nel dialogo, `hasData = todo.open > 0` | L | Nessuno (dati locali) | no |
| B2 | Faccia «Compleanni» (prossimo e quanti giorni) | Dato già letto per il tile Compleanni | `InfoPart.Birthdays`, `BirthdaysFace`, `LiveKind.Birthdays` (letto ogni 12 h) | L | Legge i contatti (permesso già nel manifest) | sì |
| B3 | Faccia «Promemoria» (prossimo, ritardi) | `live.reminders` è già in `LiveTileData` | `InfoPart.Reminders`, `RemindersFace` | L | Nessuno | no |
| B4 | Faccia «Timer/cronometro in corso» con il conto vivo | Il timer sta in un tile a parte e non si vede se non c'è | `data/Timers.kt` (`countdown`, `untilNextCountdownTick`) + faccia che compare solo se un timer gira; tick ogni secondo solo se visibile | M | Un aggiornamento al secondo mentre la faccia è su | no |
| B5 | Faccia «In riproduzione» (copertina, titolo, play/pausa) solo quando c'è musica | `live.nowPlaying` è già nel dato; mostra cosa ascolti senza aprire l'app | `InfoPart.Music`, riuso di `CardsFace`/`MusicControls`; `hasData = nowPlaying != null`; con `smart` in testa | M | Serve l'accesso alle notifiche (già c'è) | sì |
| B6 | Faccia «In attesa»: chiamate perse + mail non lette | Un colpo d'occhio su cosa si è perso, senza due tile | `InfoPart.Waiting`, dati `missedCalls`, `unreadMail`; tocco apre Telefono o Mail | M | Legge il registro chiamate (permesso già nel manifest, facoltativo) | sì |
| B7 | Faccia «Passi» con anello dell'obiettivo | Il contapassi è già un tile | `StepsFace`, `extras.steps`, `TileFeatures.stepGoal`; estendere `refreshExtras` | M | Sensore passi (permesso `ACTIVITY_RECOGNITION` già dichiarato) | sì |
| B8 | Faccia «Sole e luna»: alba, tramonto, ora d'oro, fase | Dati già in `WeatherData` e `MoonPhase`; utile a chi fotografa o corre | `SunFace` + `MoonFace` unite in `InfoPart.Sky` | L | Nessuno | no |
| B9 | Faccia «Conto alla rovescia» scelto per questo tile (data e nome) | Il conto alla rovescia esiste solo come tile a parte | `InfoConfig` (`;d=<giorno>;dn=<nome>`), `CountdownFace`, selettore data in `ui/mini/DateTimeDialog.kt` | M | Nessuno | no |
| B10 | Faccia «Parcheggio»: dove hai lasciato l'auto e da quanto | `ParkedCarStore` e `parkedLine` già ci sono | `InfoPart.Parking`, `extras.parked`; compare solo se c'è un'auto salvata; tocco apre la mappa | L | Posizione (già usata dal tile Parcheggio) | sì |
| B11 | Faccia «Dati mobili» di oggi e del mese | Chi ha un piano a GB lo controlla spesso | `DataUseFace`, `extras.dataUsage`; richiede l'accesso all'uso | M | Accesso all'uso (già gestito: `UsageAccess`) | sì |
| B12 | Faccia «App più usate oggi» | `AppsStats` già calcolato | `AppsFace`, `extras.apps` | M | Privacy (mostra le app usate), accesso all'uso | sì |
| B13 | Faccia «Appunto fissato»: la prima riga della nota scelta | Una frase che serve (parcheggio, codice, lista) sempre in vista | `InfoConfig` con l'id della nota; `NoteStore` + `MiniSearch.recentNote`; **solo note non cifrate** (`NoteCrypt`) | M | Privacy: testo in chiaro su Start | sì |
| B14 | Faccia «Batteria nelle ultime 24 h» (grafico) | Capire se la batteria scende troppo; i dati ci sono (`BatteryHistory`) | `BatteryGraphFace`, `extras.battery` | M | Nessuno | no |
| B15 | Faccia «Persone»: i volti dei preferiti, tocco = chiama | Un contatto a un tocco senza aprire la rubrica | `PeopleFace`, `live.people`, `LiveKind.People` | M | Contatti (già nel manifest) | sì |
| B16 | Faccia «Chat»: chi ha scritto per ultimo (da notifiche) | Il tile delle app lo fa già, non il super tile | `LiveCard` di `notifications/`, rispetta «nascondi il testo» (`live_hide_text`) | M | Privacy forte: testo di messaggi su Start | sì |
| B17 | Faccia «Dalle app» con ciò che un'app terza pubblica | `TilePublishProvider` esiste (`publish`/`clear`): un'app può scrivere «3 consegne in arrivo» senza avere un tile suo | `InfoConfig` con il pacchetto scelto; lettura da `ThirdPartyTiles` | M | Testo di app esterne su Start; spegnibile come `thirdPartyTiles` | sì |
| B18 | Faccia «Settimana»: sette giorni con un punto dove c'è un evento | Si vede com'è la settimana d'un colpo | `CalendarSearch.upcoming` già legge 7 giorni; nuovo composable su `TileSize.Wide/Large` | M | Nessuno | no |
| B19 | Faccia «Giornata»: linea di 24 h con i blocchi degli eventi e l'ora ora | Si vede quanto è libero il pomeriggio | Eventi con inizio e fine (richiede A14); `Canvas` | M | Nessuno | no |
| B20 | Faccia «Grafico del tempo»: temperatura e pioggia nelle prossime 24 h | La pagina meteo ha il grafico (`WeatherBlock.Chart`), il tile no | `WeatherData.hours` già c'è; `Canvas` in `WeatherFaces.kt` | M | Nessuno | no |
| B21 | Faccia «Fusi orari»: 2–3 città insieme (Roma · New York · Tokyo) | Chi ha team o famiglia lontani: oggi è un fuso solo per tile | `InfoConfig.zone` diventa lista (`;z=a,b,c`), `WorldCities.timeIn` per ognuno | M | Nessuno | no |
| B22 | Faccia «Consiglio»: ombrello, giacca, crema solare, in una riga | Traduce i numeri del meteo in un'azione | Regole su `WeatherData` (pioggia, temperatura, indice se aggiunto) in `data/`, stringhe in 6 lingue | M | Nessuno | no |
| B23 | Faccia «Notizie»: il titolo più recente | Il tile Notizie esiste, qui servirebbe in una faccia | `NewsData` già letto (`feedsWanted`); estendere `LiveSources.feedsWanted` agli `InfoConfig` | M | **[rete]** (feed già usato, soglie in `PowerPolicy.feedMaxAgeMs`) | sì |
| B24 | Faccia «Quotazioni» | Idem per Finanza | `FinanceData`, estendere `feedsWanted` | M | **[rete]** | sì |
| B25 | Puntini d'indicatore (quante facce, quale è accesa); toccarli = faccia successiva | Non si sa quante facce ci siano né come fermare quella che interessa. Visivo e a tocco, a differenza di A38 (TalkBack) | `InfoFace`: `Row` di 4–6 punti in basso a destra; area di tocco ≥ 48 dp; `FlipContainer` con `index` pilotabile | M | Nessuno | sì |
| B26 | «Entra nella riunione»: il link (Meet, Zoom, Teams) nell'evento | Nessuno cerca il link nella descrizione cinque minuti prima | Leggere `EVENT_LOCATION` e `DESCRIPTION` (già letti da `calendar/CalendarRepository.kt:55-56`), cercare `https://…`, pulsante sulla faccia Calendario | M | Tocca testo privato dell'evento; nessun permesso nuovo | sì |
| B27 | «Portami lì»: il luogo dell'evento in un tocco | Naviga verso l'appuntamento senza ricopiare l'indirizzo | `EVENT_LOCATION` + `Intent(ACTION_VIEW, geo:0,0?q=…)` | L | Nessuno (apre un'app di mappe qualsiasi) | no |
| B28 | Timer rapidi dalla faccia Ora (5 · 10 · 15 min) | Il timer della pasta senza aprire l'orologio | Piccolo menu; `AlarmClock.ACTION_SET_TIMER` (permesso `SET_ALARM` già dichiarato) | M | Nessuno | no |
| B29 | Riga di interruttori rapidi su Large (torcia, Wi-Fi, Non disturbare, rotazione) | Il tile Large ha spazio e non lo usa | `QuickActions` (già c'è, ha anche `ToggleFace`) con pulsanti propri come il «−» di `CounterFace` | M | Alcune azioni usano Shizuku/impostazioni (già gestito) | sì |
| B30 | «Leggimi la giornata» a voce | Per chi guida, cammina o non vede bene | `android.speech.tts.TextToSpeech` (nessun permesso) + riepilogo di A36; avviabile da azione TalkBack o da `VoiceActions` | M | Nessuno (nessun uso di TTS oggi nel progetto) | sì |
| B31 | Un widget Android come faccia del giro | Il widget del meteo o dello sport che si ama, dentro lo stesso tile | `WidgetFace` (esiste per il tile widget) in `InfoPart.Widget`, `host.createView` | H | Memoria e batteria del widget; tocchi del widget in conflitto con il tocco del tile | sì |
| B32 | Layout composito su Wide e Large: più facce affiancate senza girare (ora + evento + meteo + batteria) | Su un tile grande il giro spreca spazio e tempo | `InfoFace` con `Row/Column` che compongono facce; modalità «cruscotto» | H | Nessuno | no |
| B33 | Programma per fascia oraria: quali facce e in che ordine, per ore (mattina, giorno, sera) | Mattina calendario+meteo, sera foto+batteria. Come `Layouts.due(hour)` ma per un solo tile | `InfoConfig` con fino a 3 fasce `;p=6:tc;12:p;20:bp`; `InfoFace` sceglie per `hour` | M | Nessuno | no |
| B34 | Facce per luogo o dispositivo: Wi-Fi di casa o auto Bluetooth collegata | In auto Parcheggio+Meteo, a casa Foto | `BLUETOOTH_CONNECT` già dichiarato; il nome della rete Wi-Fi richiede la posizione (permesso a runtime): meglio `ACTION_ACL_CONNECTED` | M | **[perm. nuovo a runtime]** per il Wi-Fi; Bluetooth già dichiarato | sì |
| B35 | Non disturbare / Sonno → solo ora e sveglia | Di notte o in riunione il tile deve tacere. Diverso da A11 (orari fissi) | `NotificationManager.currentInterruptionFilter` + `ACTION_INTERRUPTION_FILTER_CHANGED` (già ascoltato in `ui/components/MetroStatusBar.kt:143`) | M | Nessuno (non serve alcun permesso) | sì |
| B36 | In viaggio: se il fuso del telefono cambia, mostra anche l'ora di casa | Chi viaggia vuole entrambe. `ACTION_TIMEZONE_CHANGED` è già ascoltato altrove | `InfoConfig.home` + confronto con `ZoneId.systemDefault()`; faccia Ora con due righe | M | Nessuno | sì |
| B37 | Regole per faccia: «solo quando…» (batteria < x %, evento entro x min, piove, in carica) o «mai tra le x e le y» | Generalizza gli ordini automatici (A9, A10) all'uso che ognuno sa fare | `InfoConfig` con regole per `InfoPart`; `InfoPriority` le valuta | H | Nessuno | no |
| B38 | Notifica «riepilogo del mattino» alla stessa ora | Le facce del tile, in una notifica prima di alzarsi | `AlarmManager` (già `SCHEDULE_EXACT_ALARM`), `POST_NOTIFICATIONS` già dichiarato, testo da A36 | M | Una notifica al giorno (nessuna rete) | sì |
| B39 | Profili e modalità bambini/ospite: il tile mostra solo ora e meteo | Chi presta il telefono o lo dà a un bambino non vuole che veda eventi e foto | `LauncherViewModel` ha profili e modalità bambini (non letti): ridurre `InfoConfig.parts` in base al modo | M | Privacy (migliora) | sì |
| B40 | Scegliere i calendari da mostrare, per tile | Lavoro e personale mischiati: un tile solo lavoro, uno solo famiglia. `CalendarSearch` legge tutto | `InfoConfig` `;k=id1,id2`, filtro su `Instances.CALENDAR_ID`; elenco da `calendar/CalendarRepository.kt:42` | M | Nessuno | no |
| B41 | Meteo di una città scelta per questo tile | Il meteo di dove andrai o dove vive la famiglia; oggi la città è una sola per tutto | `SavedCity`/`weatherCities` e `cityWeather` esistono (fino a 5); `;w=<chiave>` in `InfoConfig` | M | **[rete]**: una richiesta in più per città (soglie in `PowerPolicy`) | sì |
| B42 | Foto scelte a mano per questo tile (selettore) | `gallerySource = Chosen` vale per tutta l'app, non per tile | `galleryPhotos` per tile in `InfoConfig` (URI), `chosenPhotos` esiste; `ACTION_PICK_IMAGES` (API 33+) o `ACTION_OPEN_DOCUMENT` prima | M | Nessun permesso: dà l'accesso solo alle foto scelte | no |
| B43 | Solo foto preferite | Il tile mostra le foto che ami, non le ultime qualsiasi | `MediaColumns.IS_FAVORITE` (Android 11+, già letto in `photos/PhotoReader.kt:34`) come filtro | L | Non tutte le gallerie lo riempiono | no |
| B44 | Ricordi «in questo periodo» (± 3 giorni) quando oggi non c'è nulla | Il giorno preciso è spesso vuoto: la faccia Ricordi non compare quasi mai | `PhotoMemories` con finestra; didascalia da A29 | M | Nessuno | no |
| B45 | Orologio analogico come stile della faccia Ora | `AnalogClockFace` c'è già come tile: lo stile digitale/analogico si sceglie nel tile | `InfoConfig` `;s=a`, `AnalogClockFace` dentro `WorldClockFace`; niente lancetta dei secondi (batteria) | L | Nessuno | no |
| B46 | Modelli pronti alla creazione («Mattina», «Viaggio», «Notte», «Essenziale») | Il dialogo ha 9 scelte: serve un punto di partenza | `LauncherViewModel.addInfo` (oggi `InfoConfig().encode()`) con modelli e `InfoTileDialog` | M | Nessuno | no |
| B47 | Condividere la configurazione del tile come testo (o QR) | La stringa `encode()` è già breve: incollarla in un altro telefono (o in un backup nella chat) | `Intent.ACTION_SEND` con `encode()`, incolla in «aggiungi tile»; `LayoutBackup` copre solo i backup | L | Nessuno (niente nomi di eventi, solo scelte) | no |
| B48 | Condividere il ricordo con didascalia («5 anni fa oggi») | I Ricordi si mandano a chi era lì | Richiede A29/A30; `ACTION_SEND` con l'URI della foto e testo; permesso di lettura temporaneo | M | Condivide una foto: avviso e conferma | sì |
| B49 | Il colore del tile segue la condizione (batteria bassa rossa, notte scura, pioggia blu) | Il colore dice prima di leggere | `AppItem` ha già il colore del tile; `StartScreen` sostituisce l'accento in base a `InfoTileState` + dato | M | Nessuno | sì |
| B50 | Foto di sfondo sotto l'ora (la faccia Ora sopra un ricordo con un velo) | Il tile bello, non solo funzionale | `WorldClockFace` dentro un `Box` con `CycleFace` e gradiente come `PhotoLabel`; testo su sfondo con contrasto verificato | M | Memoria foto; testo su foto: attenzione al contrasto | sì |

---

## Le 10 più utili in assoluto

Criterio: quanto cambia l'uso di ogni giorno, rapportato al lavoro. Le prime sei sono correzioni di difetti.

1. **A1** — movimento ridotto e risparmio: oggi il tile mostra per sempre solo l'ora.
2. **A2** — giri annidati: la faccia Calendario/Meteo non rispetta il ritmo scelto e l'evento spesso non si vede.
3. **A3** — con smart, l'evento imminente resta mezzo tempo coperto dal numero del giorno.
4. **A14** — l'evento con fine: «in corso» e niente eventi già finiti (base di B19, B26).
5. **A19** — il tocco sul meteo apre le previsioni.
6. **A36** — TalkBack legge un riepilogo del tile.
7. **A27** — Ricordi riletti ogni 2 minuti e rimescolati (con A28, A26 nello stesso intervento).
8. **B1** — faccia «Da fare».
9. **B6** — faccia «In attesa» (chiamate perse e mail).
10. **B40** — calendari scelti per tile (con **B26** «Entra nella riunione» come seguito).

## Ordine di lavoro suggerito

Una cosa alla volta.

1. **Difetti piccoli e sicuri (L)**: A6, A7, A13, A24, A23, A3, A33, A8, A27, A26. Test: A46.
2. **Movimento (M)**: A1 → A2 → A4 → A5. Test: A45. Da qui in poi `InfoFace` ha una base solida su cui aggiungere facce.
3. **Calendario (M)**: A14 → A15 → A18 → A16 → A17. Poi B26, B27, B19, B40.
4. **Meteo (L/M)**: A19, A20, A21, A9, A10. Poi B20, B22, B41.
5. **Foto e Ricordi (M)**: A28, A29, A30, A31, A32, A34, A35, A37. Test: A49. Poi B43, B44, B42, B48.
6. **Accessibilità e dialogo (M)**: A36, A37, A38, A39, A40, A41, A42. Test: A47, A48. Poi B30.
7. **Privacy e automatismi leggeri**: A43, A11, A12; poi B35, B39, B33.
8. **Nuove facce da dati esistenti (L/M, una per volta)**: B1, B3, B2, B6, B5, B14, B8, B9, B10, B15; B7, B11, B12 per ultime (accesso all'uso).
9. **Interazioni**: B25, B28, B29, B45, B46, B47.
10. **Pesanti, solo se servono**: B32, B37, B31, B34, B38; facce di rete B23, B24 come ultime (rete).

Dipendenze: A2 → tutte le nuove facce; A14 → B19, B26; A29 → A30, A37, B44, B48; A41 → B33; A36 → B30, B38; A31 → B43.

## Non verificato

- Nessuna esecuzione: né app, né emulatore, né test (solo lettura del codice). Il comportamento a schermo (i due giri annidati di A2, l'etichetta su Small di A34, l'effetto del `Crossfade` di A8) è dedotto dal codice, non visto.
- Se `InfoTile` esista davvero in formato Small: non controllato; A34 e A25 valgono per i formati in cui esiste.
- `LocalReduceMotion` (A1): letta solo la provenienza in `Theme.kt:200`, non se si accende anche con l'impostazione di sistema «rimuovi animazioni».
- Se un secondo ricevitore aggiorna `nextAlarm` in tempo reale: `LauncherViewModel.kt:840` registra `ACTION_NEXT_ALARM_CLOCK_CHANGED`, ma non ho letto cosa fa; per questo non c'è una voce sulla sveglia.
- `TileTransition.Slide` e `Mixed` (A7): letti nel codice di `FlipContainer`, non provati.
- Profili e modalità bambini (B39): il nome è nel codice (`LauncherViewModel`, `LauncherApp`), il meccanismo non letto.
- Che `BatteryHistory`/`extras.battery` si possano usare in una faccia del super tile (B14): visto il nome e il tile `BatteryGraphFace`, non il costo di caricarli.
- Campi di Open-Meteo oltre a quelli già usati (UV, minutely): non verificati e non proposti.
- Compatibilità di `ACTION_PICK_IMAGES` (B42) con il minimo SDK del progetto: non controllata.
- `FileProvider` per condividere una foto (B48): non cercato.
- `DialogFrame` scorre già (letto): non è un difetto, non è nella tabella.
