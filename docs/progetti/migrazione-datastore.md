# Migrazione da `SharedPreferences` a DataStore / Room, con schema versionato

Stato: **documentata**. Non implementata: il punto di migrazione esiste già (`PrefsMigrations` in `data/Prefs.kt`, `schema_version` nel file `metrowin`, test `PrefsMigrationsTest`) e oggi non ha passi.

## Stato oggi
- Tutte le aperture passano da `Prefs` (`data/Prefs.kt`): 14 file di preferenze con nome fisso (`metrowin`, `metrowin_todo`, `metrowin_tiles`...), 39 punti di chiamata. I nomi non si cambiano: sono quelli che hanno già i telefoni.
- `SettingsStore` tiene **tutte le impostazioni in una sola stringa JSON** (`SettingsCodec`, chiave `KEY_JSON`), con una cache decodificata e letture di singoli interruttori senza decodifica (`thirdPartyTilesOn()` ecc.),
  usate dal servizio notifiche e dal provider dei tile, cioè fuori dal thread principale e senza schermata.
- Lo schema ha due livelli di versione: `schema_version` (file di preferenze) e `version` dentro il JSON del layout (`LayoutBackupCodec`, per backup e sincronizzazione).
- `SharedPreferences.edit { }` scrive in modo asincrono (`apply`), le letture sono sincrone dalla memoria: il codice di tutto il launcher le usa così (`SettingsStore.load()` ovunque, anche in `onCreate`).

## Perché non migrare ora
| Cosa darebbe | Costo |
|---|---|
| Letture asincrone e coerenti (`Flow`), niente ANR da I/O sul main thread | oggi non risulta un ANR da preferenze; `SettingsStore.load()` è sincrona in 31 punti: tutti da riscrivere in `suspend`/`Flow`, anche le letture dei servizi e del provider (`ContentProvider.call` è sincrono: servirebbe `runBlocking`) |
| Migrazione tipizzata | il JSON unico è già versionabile; i campi nuovi hanno default in `SettingsCodec` |
| Room: query e relazioni | i dati del launcher sono piccoli (tile, scorciatoie, promemoria: centinaia di righe), nessuna query reale; `LayoutBackup`/sync lavorano già su JSON |
| — | perdita di dati se la migrazione fallisce a metà: nessun modo di provarla su tutti i telefoni; `schema_version` si scrive con `commit()` proprio per questo |
| — | nuove dipendenze (DataStore, Room, KSP) in una build F-Droid con verifica dei checksum (`gradle/verification-metadata.xml`) e R8 |

Conclusione: i vantaggi principali (non bloccare il thread) non pesano per file piccoli; il rischio è la perdita di impostazioni. Si migra solo quando serve (dati grandi o relazionali).

## Piano a passi sicuri (se si decide)
1. **Prima del cambio, congelare lo schema 0** con un test: un file di preferenze per ognuno dei 14 nomi, con valori reali di una versione vecchia, che il codice legge (esiste in parte: `PrefsMigrationsTest`, `SettingsCodecTest`, `BackupPrefsTest`).
2. **Un file per volta**, partendo dal più piccolo e meno critico (`metrowin_tiles` o `metrowin_news`), dietro `Prefs.open` così che i chiamanti non cambino: `Prefs.open` restituisce un adattatore con la stessa interfaccia (`getString/edit`) sopra DataStore, riempito la prima volta da `SharedPreferencesMigration` di DataStore.
3. Passo `PrefsMigrations.steps[n]`: copia il contenuto nel nuovo archivio, scrive `schema_version = n+1` con `commit()`, **tiene il vecchio file** per due versioni (rollback) e poi lo cancella.
   Deve poter essere ripetuto (il processo può morire a metà): se il nuovo archivio ha già la chiave, non copia.
4. `SettingsStore`: tenere la stringa JSON come valore unico (`Preferences.Key<String>`) e la cache decodificata; i piccoli interruttori restano letti dalla cache in memoria aggiornata da un `collect`, non da `runBlocking`.
5. Per i dati con righe (promemoria, compiti, portafoglio): Room con `exportSchema = true` e test `MigrationTestHelper` per ogni versione; il formato di backup/sync (JSON) non cambia.
6. Rollback: se la versione nuova trova uno schema più alto di quello che conosce (utente che torna indietro), **non tocca nulla** e avvisa; il test lo copre.

## Rischi
- Perdita o duplicazione di impostazioni se il passo si ripete a metà; il test deve uccidere il processo tra due passi (simulato con `steps` che lanciano eccezione).
- Servizi (notifiche, accessibilità) che girano senza schermata: leggono le preferenze in processi separati se il launcher non è vivo; DataStore non supporta più processi per lo stesso file senza `MultiProcessDataStore`.
- Backup: `allowBackup=false` ed estrazione escluse: nulla da migrare lato sistema.

## Per provare su telefono
Installare la versione attuale, riempire il launcher (layout, mail, note), installare sopra la versione migrata, controllare tutto; poi installare sopra la versione precedente (rollback) e verificare che il launcher si avvii senza perdere nulla.
