# Shizuku: servizio utente al posto della riflessione su `newProcess` (non fatto)

Oggi `data/ShizukuShell.kt` chiama `Shizuku.newProcess` per riflessione (il metodo è privato nella libreria 13.1.5) e
`app/proguard-rules.pro` lo salva da R8. Funziona per comandi singoli e `ShizukuRadioTest`/`ShizukuTerminalTest` lo coprono, ma non c'è un
modo di provare un servizio utente senza un telefono con Shizuku in esecuzione: per questo non è stato fatto.

## Piano

1. `app/build.gradle.kts`: `android { buildFeatures { aidl = true } }`.
2. `app/src/main/aidl/com/metrowin/launcher/IShellService.aidl`: `String run(in String[] args, int limit, long timeoutMs);` e `void destroy() = 16777114;`
   (il numero 16777114 è il codice riservato da Shizuku per `destroy`).
3. `data/ShellUserService.kt`: `class ShellUserService : IShellService.Stub()` con costruttore senza argomenti (o con `Context`), che esegue
   `ProcessBuilder(args)` e restituisce uscita + codice. Gira nel processo di Shizuku, con l'identità shell.
4. `ShizukuShell`: `Shizuku.bindUserService(UserServiceArgs(ComponentName(packageName, ShellUserService::class.java.name)).daemon(false).processNameSuffix("shell").version(1), connection)`;
   la connessione tiene `IShellService?`; `run` usa il servizio se collegato, altrimenti il ripiego attuale (riflessione).
   Il legame va chiuso con `Shizuku.unbindUserService` quando Shizuku muore (`ShizukuWatch` ha già i listener).
5. `proguard-rules.pro`: `-keep class com.metrowin.launcher.ShellUserService { <init>(...); }` e l'interfaccia AIDL generata. Non togliere la regola
   `newProcess` finché il ripiego esiste.
6. Prova su telefono con Shizuku: terminale (`ShizukuTerminalActivity`), interruttori (`ShizukuButtons`), monitor, in debug e in `assembleRelease`.

## Hotspot e VPN (voce 20): dipende dal punto sopra

Con un servizio utente collegato si può provare `TetheringManager.startTethering(TetheringRequest, executor, callback)` /
`stopTethering(type)` dal processo di Shizuku (permesso `TETHER_PRIVILEGED`, che l'identità shell ha). Va provato: l'esito è sconosciuto finché non esiste il servizio.
Non fatta: dipende dalla voce 1.
