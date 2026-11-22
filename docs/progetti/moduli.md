# Dividere `:app` in moduli Gradle

Stato: **fattibilità valutata; due moduli foglia estratti** (`:parsers`, `:logic`). Le fasi `:model`, `:store`, `:feature-*`, `:ui-kit` restano documentate: non sono fattibili senza rompere prima i cicli (vedi sotto), e non sono state fatte in 26.10.5S.

## Misura (strumento: `tools/package-deps.py`, legge gli `import` fra pacchetti)
- 393 file Kotlin, circa 62 000 righe, tutto in `:app`. Pacchetti: `data` (142 file), `ui` (119), 20 di funzione (mail, notes, wallet, recorder, files...).
- `tools/package-deps.py --cycles`: **tutti i 23 pacchetti formano un solo ciclo** (componente fortemente connessa): `data` importa 14 pacchetti di schermate e quasi tutti importano `data` e la radice (`R`, `LauncherViewModel`).
- `tools/package-deps.py --check` elenca le 14 violazioni «`data` importa una schermata»: `MiniApps` (catalogo, tutte le mini app), `SettingsStore`→`gestures` (`GestureAction`),
  `BackupExtras`/`LayoutBackup`→`todo`,`wallet`, `AppRepository`→`calls`,`dialer`, `Connections`/`LiveTiles`/`LockStatus`/`ThirdPartyTiles`→`notifications`, `RecentTasks`→`recents`,
  `ShizukuWatch`→`shizukuterminal`, `Tutorial`→`gestures`, `VoiceRecorder`→`recorder`, `AtomicEncryptedFile`→`notes`.
- Gli `import` sono un'approssimazione per difetto: due file dello stesso pacchetto non hanno `import` fra loro, quindi i veri legami dentro `data` (142 file, un solo pacchetto) non si vedono; uno spostamento va confermato compilando.

## Fatto: `:parsers`
Modulo Kotlin puro (`parsers/`, nessun Android) con `BatteryDumps`, `WifiShell`, `DeviceMonitor` (`dumpsys`, `iw`, `/proc`: testo dentro, valori fuori) e `WifiShellTest`.
Il pacchetto resta `com.metrowin.launcher.data`, quindi nessun altro file è cambiato. `:app` lo importa con `implementation(project(":parsers"))`; in CI: `:parsers:test`.
Costo reale: il plugin `org.jetbrains.kotlin.jvm` ha richiesto 37 righe nuove in `gradle/verification-metadata.xml` (marker del plugin e `kotlin-scripting-*`, generate da Gradle:
vanno confrontate con Maven Central prima del rilascio). `DeviceMonitorTest` e i test di `BatteryDumps` restano in `:app` perché usano `DeviceReaders`/`ShizukuTerminal`.

## Fatto: `:logic` (26.10.5S)
Secondo modulo Kotlin puro (`logic/`, nessun Android, nessun altro file di `:app`): 26 file di `data` (2 225 righe) trovati con uno script che parte da ogni file di `data` e lascia solo quelli che usano
unicamente `java.*`/`kotlin.*` e nomi di altri file dello stesso insieme: `CalcEngine`, `CalcSession`, `CalcTools`, `Calculator`, `WhenParser`, `SyncCrypto`, `Checklist`, `Countdown`, `ClockFormat`, `Places`, `SearchEngine`,
`SiteInput`, `BoundedRead`, `BackupSchedule`, `BackupCopies`, `RefreshRate`, `PhotoFraming`, `PhotoMemories`, `NavButtons`, `HomePresses`, `AppLinks`, `DeferWhileAway`, `GlassIcon`, `LockIconRow`, `ClickSound`, `MetroSounds`.
Il pacchetto resta `com.metrowin.launcher.data`; `:app` lo importa con `implementation(project(":logic"))` e i test restano in `:app`. Il compilatore di Compose non vede dentro un altro modulo: le classi senza `var` sono elencate in
`app/compose_stability.conf` (come erano viste dentro `:app`). Costo sul checksum: nessuna riga nuova (il plugin `kotlin.jvm` c'era già).

## Altri candidati foglia (stima, da confermare compilando)
Circa 28 file di `data` senza `import` del progetto né di Android e senza riferimenti agli altri file di `data` fuori dall'insieme: `CalcEngine`, `CalcSession`, `Calculator`, `Checklist`, `Countdown`,
`WhenParser`, `SearchEngine`, `SiteInput`, `ClockFormat`, `Places`, `SyncCrypto` (usa solo `javax.crypto`), `BoundedRead`, `SafeHttp`, `BackupSchedule`, `RefreshRate`, `PhotoFraming`, `NavButtons`... Un secondo modulo
`:logic` per loro è il passo successivo realistico (stesso trucco: stesso pacchetto, nessun cambio di sorgenti).

## Confini proposti (per fasi)
1. `:parsers` (fatto) e `:logic` (puro).
2. `:model` (`model/`: `AppItem`, `Launch`, `TileState`...) con `GestureAction`, `ThemeStyle` e gli enum di impostazioni estratti dai pacchetti di funzione: rompe `data → gestures`.
3. `:store` (`SettingsStore`, `SettingsCodec`, `Prefs`, `SecureStore`, `AtomicEncryptedFile`, `LayoutBackup`): dipende solo da `:model`. Richiede di togliere da `BackupExtras`/`LayoutBackup` i tipi di `todo`/`wallet` (interfaccia `BackupSection` registrata dalle funzioni).
4. `:feature-<nome>` per ogni mini app autosufficiente (`mail`, `notes`, `wallet`, `recorder`, `files`, `datasense`, `calculator`, `calendar`, `todo`, `news`): ognuna dipende da `:store`, `:ui-kit`; `data/MiniApps.kt` (catalogo) sale in `:app` e le conosce per nome.
5. `:ui-kit` (`ui/components`, tema, animazioni `Motion`): oggi dipende da `calls`, `gestures`, `notifications`, `shizukuterminal`: vanno rovesciate.
6. `:app` resta con `MainActivity`, `LauncherViewModel`, `LauncherApp`, manifest e l'unione delle funzioni.
Regola da imporre con `tools/package-deps.py --check` (estendere: `data` → nessuna schermata) in CI quando la violazione scende a zero.

## Rischi
- Ogni modulo Android aggiunge `AndroidManifest`/risorse: le stringhe in 6 lingue e `R` condiviso complicano i moduli di funzione (`android.nonTransitiveRClass=true` è già attivo).
- `compose_stability.conf` e R8 (`proguard-rules.pro`) valgono per `:app`: i moduli con Compose avrebbero bisogno della stessa configurazione.
- Build più lente al primo giro, più veloci incrementali; la regressione di avvio dell'app va misurata (`docs/progetti/macrobenchmark.md`).
- F-Droid: la ricetta compila con `subdir: app`; i moduli fratelli sono trovati da `settings.gradle.kts` nella radice.
- Le modifiche non ancora salvate nel repository di altri lavori possono toccare gli stessi file: spostare un file alla volta, ogni volta con test verdi.
