# Nascondere le barre di sistema e ripristinarle in modo sicuro

Stato: **documentata** (con prove su emulatore). **Non implementata**: una funzione sulle barre ha già reso inutilizzabile il telefono una volta.

## Che cosa è stato provato (emulatore `Medium_Phone`, Android 17, API 37, senza Shizuku, comandi come utente shell via `adb`)
| Comando | Esito |
|---|---|
| `cmd statusbar send-disable-flag home recents` | esce con 0; `dumpsys statusbar` mostra `mDisabled1=0x1200000` (home `0x00200000` + recenti `0x01000000`) |
| `cmd statusbar send-disable-flag none` | `mDisabled1=0x0`: ripristina tutto |
| `cmd statusbar send-disable-flag statusbar-expansion` | `mDisabled1=0x10000`: la tendina non si apre |
| flag disponibili | `search home recents notification-peek statusbar-expansion system-icons clock notification-icons quick-settings` |
| **Il pulsante Indietro non ha flag**: resta sempre attivo | coerente con la prova dell'utente: nascondono stato e home/recenti, non indietro |
| Persistenza | i flag **restano dopo la fine del comando** (letti da un `dumpsys` successivo): non dipendono dal processo che li ha mandati. Un riavvio del telefono li azzera (stato del system server). Un crash del launcher no |

Quindi: se il launcher cade con le barre nascoste, **restano nascoste** finché qualcuno manda `none` o si riavvia.

## Perché è rischioso
- Home e Recenti disabilitati + nessuno che li rimette = l'utente non esce da un'app: solo Indietro e il riavvio.
- Lo stato vive fuori dal launcher: un blocco di Shizuku (si ferma da solo su molti telefoni, vedi `ShizukuWatch`) impedisce di mandare `none`.
- Nessuna API pubblica senza permesso `STATUS_BAR`: solo l'identità shell (Shizuku/adb) lo può fare, dunque la funzione sparisce quando Shizuku si ferma, ma i flag no.

## Progetto del watchdog (da realizzare prima di qualunque funzione)
1. **Registro dell'intenzione**: prima di mandare un flag, scrivere con `commit()` in un file (`Prefs.SHIZUKU`) `barsHidden=<flags>` e l'ora. Dopo averli tolti, cancellare. Il registro dice sempre «le barre potrebbero essere nascoste».
2. **Ripristino all'avvio**: a ogni avvio del processo (`Application.onCreate`), se `barsHidden` è presente e la funzione non è attiva (o il processo è ripartito), mandare `none` (con Shizuku pronto) e, se non si può, mostrare una notifica «Barre nascoste: tocca per ripristinare / apri Shizuku».
3. **Ripristino a tempo**: lavoro `JobScheduler` ogni 15 minuti (come `ShizukuWatchJob`) che controlla `barsHidden` e, se il launcher non è in primo piano (`UsageStats`/`ActivityManager`) o Shizuku non risponde, prova `none` e notifica.
4. **Ascolto di Shizuku**: `ShizukuWatch` ha già i listener di morte del binder: all'evento `binderDead` con `barsHidden`, mandare subito la notifica di ripristino (non si può mandare il comando: Shizuku è morto).
5. **Nascondere solo con il launcher in primo piano**: togliere i flag in `onPause`/`onStop` dell'attività principale e quando arriva un'altra app in primo piano (accessibilità: `TYPE_WINDOW_STATE_CHANGED`). Mai flag che restano a schermo spento.
6. **Via d'uscita fuori dal telefono**: guida in `docs/` e nella schermata della funzione con il comando `adb shell cmd statusbar send-disable-flag none` e la nota «riavviare azzera lo stato».
7. **Interruttore di sicurezza**: il tasto di emergenza (già presente: `SafeMode`/modalità sicura) spegne la funzione e manda `none`.
8. **Test**: `BarFlags` (puro: insieme di flag → argomenti del comando e viceversa) con test; registro e watchdog con `Robolectric` e `ShizukuShell` finto; prova su emulatore con `dumpsys statusbar` prima e dopo, **uccidendo il processo** del launcher (`adb shell am force-stop`) con le barre nascoste.

## Per provare su telefono
Telefono con Shizuku acceso, funzione attiva → uccidere il launcher da «Forza arresto», fermare Shizuku, riavviare: verificare in ogni caso che le barre tornino o che la notifica di ripristino compaia. Provare con più utenti/profilo di lavoro e con la rotazione schermo.
