# Esecuzione in background affidabile su produttori aggressivi

Stato: **fetta implementata** = guida per l'utente (`docs/ESCLUDERE-DALLA-BATTERIA.md`) e test di coerenza del manifest (`BackgroundManifestTest`). Il comportamento reale su vivo/Xiaomi **non è stato provato**.

## Che cosa gira in secondo piano oggi
| Lavoro | Meccanismo | Dopo riavvio | Dopo «forza arresto» |
|---|---|---|---|
| Promemoria, timer | `AlarmManager` esatto (`SCHEDULE_EXACT_ALARM`), `AlertReceiver` | riprogrammati da `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` | persi fino alla prossima apertura |
| Controllo aggiornamenti 12 h, posta 30 min, dati mobili 1 h, registro batteria 15 min, controllo Shizuku 15 min | `JobScheduler`, `setPersisted(true)` | sopravvivono (serve `RECEIVE_BOOT_COMPLETED`, presente) | persi; `LauncherViewModel` li riprogramma all'avvio |
| Posta in tempo reale (IMAP IDLE) | servizio in primo piano `specialUse` (`MailIdleService`), spento di default | `AlertReceiver` chiama `MailIdle.sync` | fermo |
| Registratore | servizio in primo piano `microphone` | non riparte (voluto) | fermo |
| Notifiche (tile) | `NotificationListenerService` | il sistema lo riaggancia | il sistema lo riaggancia solo se l'utente non l'ha spento |

`BackgroundWorkTest` verifica che senza schermo acceso non si legga nulla; `BackgroundManifestTest` che ogni job schedulato sia dichiarato con `BIND_JOB_SERVICE`,
che ogni tipo di servizio in primo piano abbia il suo permesso e che il ricevitore di avvio abbia le azioni giuste.

## Da verificare su telefono (lista)
1. Avvio automatico del produttore attivo: dopo il riavvio arriva un promemoria? Un job (`adb shell dumpsys jobscheduler | grep metrowin`) è ancora in lista?
2. Ottimizzazione batteria: `adb shell dumpsys deviceidle whitelist | grep metrowin` (la voce c'è se è «senza restrizioni»).
3. `adb shell cmd appops get com.metrowin.launcher SCHEDULE_EXACT_ALARM` = `allow`.
4. `adb shell dumpsys activity services com.metrowin.launcher`: il servizio dei tile e (se acceso) `MailIdleService` sono vivi dopo 1 h a schermo spento?
5. Dopo «chiudi tutto» nelle recenti: il listener delle notifiche si riaggancia? (`adb shell dumpsys notification | grep -i listener`)
6. Il blocco schermo Metro (accessibilità) resta attivo dopo ore? Alcuni produttori lo spengono: ripristino manuale.
7. Registrare quale funzione si ferma e dopo quanto: è la base per decidere se serve un watchdog.

## Passi possibili (non fatti)
- Riconoscere il produttore (`Build.MANUFACTURER`) e mostrare nel tutorial il passo giusto, con `Intent` verso le schermate note (`com.miui.securitycenter` «Autostart», `com.vivo.permissionmanager`):
  sono componenti non documentati e cambiano; usare solo con `resolveActivity` e ripiegare sulla schermata generica `ACTION_APPLICATION_DETAILS_SETTINGS`.
- Un banner «il launcher è stato fermato dal sistema» quando `ApplicationExitInfo` (API 30+) riporta `REASON_USER_REQUESTED` o `REASON_OTHER` per il processo:
  lo si legge all'avvio e si mostra una volta la guida.
- Non usare servizio in primo piano permanente: costa batteria e un avviso fisso; la guida all'esclusione è la via giusta.

## Rischi
- Percorsi dei menu cambiati o sbagliati nella guida: provarli su un vivo e un Xiaomi veri prima di linkarla nell'app.
- Le schermate interne dei produttori non sono API: nessuna garanzia, e F-Droid/Play non le gradiscono se richiedono permessi extra.
