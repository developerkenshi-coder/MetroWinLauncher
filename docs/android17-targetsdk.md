# Android 17 (API 37): cosa controllare prima di alzare `targetSdk`

Stato: `compileSdk` e `targetSdk` restano a 36 (l'SDK `android-37.0` è già installato). Alzare solo `targetSdk` a 37 non è stato fatto: vedi il primo punto.
Fonte: <https://developer.android.com/about/versions/17/behavior-changes-17> (solo per chi punta a 37) e
<https://developer.android.com/about/versions/17/behavior-changes-all> (per tutte le app, anche con `targetSdk` 36).

## Perché non si alza ora

1. **Accesso alla rete locale.** Con `targetSdk` 37 il traffico verso la LAN richiede il permesso a runtime `ACCESS_LOCAL_NETWORK`. L'utente
   può impostare un server IMAP/SMTP di casa (`mail/MailClient.kt`, host liberi in `MailAccount`): senza il permesso e senza la richiesta nella
   schermata dell'account, la posta di quell'utente smetterebbe di arrivare senza un errore comprensibile. Serve prima: dichiarare il permesso,
   chiederlo solo quando l'host è un indirizzo privato (10.x, 172.16-31.x, 192.168.x, `.local`), spiegare il rifiuto. Non è piccolo e non si prova
   senza telefono con Android 17.
2. Il resto sotto non blocca, ma va provato su un telefono con Android 17 prima di consegnare.

## Per chi punta a 37: da verificare

| Modifica | Dove tocca il launcher | Da fare |
|---|---|---|
| `ACCESS_LOCAL_NETWORK` | posta su server in LAN (vedi sopra) | permesso + richiesta + test |
| Contacts Provider 2: query su `ContactsContract.Data` senza `READ_CONTACTS` vengono rifiutate con eccezione | `ContactReader`, `People`, `DialerContacts`, `SmartDial`, `ContactImporter`, `LiveTiles`, `PhoneCalls` | controllare che ogni query sia dietro `LivePermissions.contacts` o dentro `runCatching` |
| Audio in background solo con servizio in primo piano | `RecorderService` (ha `foregroundServiceType="microphone"`); `MetroSoundPlayer`, `ClickSound`, `TapFeedback` (suoni dell'interfaccia, in primo piano) | provare registratore a schermo spento e suoni con il launcher non in primo piano |
| Focus audio richiesto in background fallisce | il launcher non chiama `requestAudioFocus` | nessuna azione |
| Limite di memoria di `RemoteViews` | `WidgetService` è solo host: il limite riguarda chi aggiorna | verificare che i widget ospitati si carichino |
| Opt-out dei limiti su schermi grandi (≥ 600 dp) rimosso | `MainActivity` imposta `requestedOrientation` dalle impostazioni | su tablet/pieghevoli l'impostazione "orientamento" non avrebbe effetto; già vero con 36 salvo opt-out, il launcher non ne usa |
| ECH, Certificate Transparency attivi di default | posta, meteo, notizie, aggiornamenti | provare accesso a un server con certificato non in CT (self-hosted) |
| Librerie native: il caricamento dinamico sicuro vale anche per `.so` | nessun `System.loadLibrary` o `DexClassLoader` | nessuna azione |
| Modifica di campi `static final` vietata | nessuna riflessione di questo tipo (`ShizukuShell` chiama un metodo, non scrive campi) | nessuna azione |
| `MessageQueue` senza lock | nessun uso diretto | nessuna azione |
| Limite 50.000 chiavi (Keystore/SharedPreferences) | `Prefs` ha poche chiavi | nessuna azione |

## Per tutte le app, anche con `targetSdk` 36: da provare ora su Android 17

- **OTP via SMS:** un'app con accesso agli SMS che non sia il destinatario atteso non legge il messaggio per 3 ore. Vale per `messages/SmsReceiver`
  se il launcher non è l'app SMS predefinita (quella predefinita è esente). Provare un codice di verifica in arrivo nei due casi.
- **Tastiera dopo una rotazione:** se l'attività non gestisce da sé il cambio di configurazione, la visibilità della tastiera non viene
  ripristinata: provare ricerca e campi di testo ruotando lo schermo.
- **Limiti di memoria per app:** il launcher tiene bitmap per i riquadri vivi; controllare nei log (`ApplicationExitInfo`) che nessun uso intenso
  (foto in galleria, molte pagine Start) lo faccia terminare.
- **Touchpad:** nessuna azione (il launcher non cattura il puntatore).
- **Loopback tra profili:** nessuna azione.

## Quando alzare

Alzare `compileSdk` e `targetSdk` a 37 solo dopo il punto 1, con `:app:compileDebugKotlin`, tutti i test unitari e `:app:lintDebug` verdi,
e le prove su telefono sopra. Il lint `OldTargetApi` resta un avviso finché non si alza.
