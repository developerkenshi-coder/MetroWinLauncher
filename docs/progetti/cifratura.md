# Cifratura di tutta l'app: modello di minaccia e decisione

Stato: **documentata**; fetta di verifica **implementata** (`LogPrivacyTest`). Decisione: **non cifrare tutte le impostazioni; cifrare i segreti**.

## Che cosa c'è oggi e dove
| Dato | Dove | Protezione oggi |
|---|---|---|
| Password dei conti di posta, bozze SMS | `SecureStore` (AES-256-GCM, chiave nel Keystore) in `metrowin_mail_secure`, `metrowin_sms_drafts` | cifrato |
| Portafoglio, note cifrate, base della sincronizzazione, registro notifiche | `AtomicEncryptedFile` / `WalletVault` / `NoteCrypt` / `KeystoreCrypt` | cifrato |
| File di sincronizzazione e backup scelti dall'utente | `SyncCrypto` con la passphrase | cifrato con passphrase |
| **Passphrase di sincronizzazione** (`syncPassphrase`) | `SettingsStore` → `metrowin` | **in chiaro** |
| Layout Start, impostazioni, scorciatoie, promemoria, compiti, città e coordinate del meteo, numeri recenti del dialer (`metrowin_dialer_numbers`), tile pubblicati | `SharedPreferences` / file nel dato privato | in chiaro (ma privato all'app) |
| Cartella di backup (URI SAF) | `SettingsStore` | in chiaro; non è un segreto, dà accesso solo a chi ha già il permesso SAF |

`allowBackup="false"`, `fullBackupContent="false"` e `data_extraction_rules.xml` escludono tutto da backup cloud e trasferimento da telefono a telefono.

## Minacce
1. **Altra app sullo stesso telefono**: non legge il dato privato (sandbox). Nessun aiuto dalla cifratura.
2. **Telefono rubato e bloccato**: il dato privato è già cifrato da Android (cifratura basata su file, chiavi legate al blocco schermo). Cifrare ancora con una chiave Keystore non cambia nulla finché il telefono è bloccato; dopo il primo sblocco la chiave è usabile dall'app, quindi neanche allora.
3. **Telefono con root / debug USB acceso e sbloccato**: chi ha root legge anche la memoria del processo. Una chiave nel Keystore non esce dal chip, ma chi ha root può chiedere al processo di decifrare. Protezione parziale.
4. **Backup e condivisione fatti dall'utente**: già cifrati con passphrase (`SyncCrypto`). Le esportazioni condivisibili escludono già passphrase e cartella (`SettingsCodec.encode(portable = true)`); il file privato `metrowin` le ha in chiaro (minaccia 3).
5. **Registro e segnalazione bug**: la segnalazione è un'email preparata, inviata dall'utente. Contiene il registro dell'app (solo `logcat` della propria app), il diario delle notifiche (pacchetti e motivi, mai parole) e versione/telefono. `LogPrivacyTest` ora impedisce che entri una riga di registro nuova fuori da `Diagnostics` e `NotificationService`, che un'interpolazione citi testo/numeri/indirizzi, e che `Diagnostics` tagli il messaggio a 200 caratteri; i punti dove l'errore può contenere dati (backup, chiamate, sincronizzazione, shell) usano `withMessage = false`.

## Decisione
**Cifrare tutto il `SettingsStore` non conviene**:
- non aggiunge protezione contro le minacce 1–2 (già coperte da Android) e poco contro la 3;
- le impostazioni si leggono in modo sincrono all'avvio e in molti punti del thread principale (cache `SettingsStoreCacheTest`); AES-GCM con Keystore costa ms per chiamata sull'hardware lento;
- se il Keystore perde la chiave (ripristino, cambio del blocco schermo su certi telefoni, aggiornamenti) **si perde tutto il layout**: oggi `SecureStore` restituisce testo vuoto in quel caso, accettabile per una password, non per tutta la Start;
- il flusso di migrazione sarebbe rischioso (`PrefsMigrations` oggi non ha passi) e non verificabile senza telefono.

**Da fare, in piccolo**: spostare `syncPassphrase` (e solo quella) in `SecureStore`, toglierla dal JSON delle impostazioni (la modalità `portable` la esclude già); se il Keystore fallisce l'utente reinserisce la passphrase (un messaggio, nessuna perdita di layout). Passi:
1. Passo di migrazione `PrefsMigrations.steps[0]`: legge `syncPassphrase` dal JSON delle impostazioni, la scrive cifrata, cancella il valore in chiaro (sicuro se ripetuto: se il cifrato esiste già non fa niente).
2. `SettingsStore.syncPassphrase()` legge da `SecureStore`.
3. Test: migrazione due volte, Keystore che fallisce (`SecureStore.encrypt` restituisce ""), esportazione senza passphrase.
Non fatto qui: tocca `SettingsStore`, `SettingsCodec`, backup e sincronizzazione insieme e va provato con la sincronizzazione vera.

## Per provare su telefono
Dopo la migrazione: sincronizzare tra due telefoni, cambiare il blocco schermo, riavviare, cancellare i dati del Keystore (disinstallare e reinstallare) e controllare che chieda solo la passphrase.
