# Sincronizzazione con risoluzione dei conflitti

Stato: **già implementata a tre vie** (`data/SyncEngine.kt`, `data/SyncMerge.kt`, `data/SyncCrypto.kt`); qui: modello, limiti e test aggiunti.

## Modello di oggi
- File unico `metrowin-sync.bin` nella cartella scelta (SAF, può stare in un cloud), cifrato con la passphrase (PBKDF2 600 000 iterazioni, AES-256-GCM).
  Dentro: `deviceId`, `stamp` (ora di scrittura, orologio di chi carica), `layout` (JSON `LayoutBackupCodec`).
- `SyncPlanner.decide` confronta l'hash SHA-256 del layout locale con quello dell'ultima sincronizzazione e il `stamp` remoto con quello visto:
  nessuno cambiato → niente; solo il locale → carica; solo il remoto → applica; entrambi → unione. Un telefono che non ha mai sincronizzato prende il file della cartella.
- **Unione a tre vie** (`SyncMerge.merge(base, local, remote)`), `base` = layout dell'ultima sincronizzazione (tenuto cifrato):
  - liste (tile per pagina, scorciatoie, compiti, promemoria, layout salvati, portafoglio) per `id`: ciò che ha cambiato un lato solo si prende, uguale su entrambi si tiene;
  - impostazioni chiave per chiave; l'auto parcheggiata come valore unico;
  - cancellato da un lato e modificato dall'altro: **resta** (contato in `kept`);
  - stessa voce modificata in modo diverso: **vince questo telefono** e il file dell'altro si conserva a parte (`metrowin-sync-other-<stamp>.bin`), da cui l'utente può ripristinare le parti scelte.
- Il `stamp` serve solo a vedere se il file è cambiato, mai a ordinare: un orologio sbagliato non fa vincere la versione sbagliata.

## Limiti (confermati da test)
1. **I conflitti non convergono**: «vince questo telefono» vale su entrambi i telefoni, quindi A tiene la sua versione e B la sua, e il ciclo si ripete a ogni modifica. Oggi è il file conservato a parte a evitare la perdita di dati. (`aClashIsNotSettledForBothPhones`)
2. L'ordine delle liste è quello del telefono locale: un riordino fatto solo sull'altro non passa (commento `shortcut:` in `SyncMerge.list`). (`aListOrderedAgainOnlyOnTheOtherPhoneKeepsThisPhonesOrder`)
3. Senza `base` (versioni vecchie) vale la regola vecchia: vince il locale, il remoto va da parte.
4. Nessun campo di data per voce: «l'ultima modifica vince» non è possibile oggi.

Senza conflitto invece converge: dopo che A carica l'unione, B unendola con il proprio layout ottiene le stesse voci (`theOtherPhoneEndsWithTheSameItemsWhenNothingClashed`).

## Passi per risolvere i conflitti
1. **Versione per voce**: ogni elemento di una lista guadagna `rev` (contatore, +1 a ogni modifica) e `by` (`deviceId`). `pick` confronta `rev`; a parità vince `by` più piccolo, uguale su entrambi i telefoni → convergenza senza orologio.
   Più semplice, meno esatta: `updatedAt` (ora) per voce, «l'ultima modifica vince»; sbaglia con orologi non allineati.
2. **Lapidi** (`deleted: true` con `rev`) per distinguere «cancellato» da «mai visto» senza `base`, e rimuoverle dopo 30 giorni.
3. **Per campo** per le impostazioni: già chiave per chiave; con `rev` per chiave si evita «vince questo telefono».
4. **Ordine**: indice frazionario per voce (tipo `fractional-indexing`) invece della posizione.
5. Migrare `LayoutBackupCodec` (campo `version` già presente), tenendo la lettura dei file senza `rev` (limite 3).
6. Test: ogni regola con le tabelle di `SyncMergeTest`, più una proprietà (merge a caso su due telefoni simulati, alla fine stesse voci).

## Rischi
- Cambiare il formato del file della cartella rompe un telefono con la versione vecchia: `version` del layout e lettura all'indietro (già c'è per `iterations`).
- Le lapidi crescono; vanno potate.
- Provare con due telefoni o due emulatori sulla stessa cartella (Syncthing, Nextcloud o una cartella locale condivisa).

## Per provare su telefono
Due dispositivi, stessa cartella e passphrase; modificare la stessa voce su entrambi offline, poi sincronizzare in sequenza e guardare quale versione vince e dove finisce l'altra.
