# Tile di terze parti: protocollo e SDK

Stato: **contratto bloccato da test** (`TilePublishContractTest`, `ThirdPartyTilesTest`); **SDK e app di esempio implementati in 26.10.5S** (`tile-sdk/`, `sample-tile/`, prova di `MetroTile` in `TileSdkTest`). Da fare: pubblicarlo su un repository Maven (vedi sotto).

## Contratto (ricavato da `data/TilePublishProvider.kt`, `data/ThirdPartyTiles.kt`, `AndroidManifest.xml`)

| Voce | Valore |
|---|---|
| Autorità | `com.metrowin.launcher.tiles` (`${applicationId}.tiles`; con `-PidSuffix` diventa `<id><suffisso>.tiles`) |
| Accesso | `ContentResolver.call(Uri.parse("content://com.metrowin.launcher.tiles"), method, null, extras)` |
| Permesso | `com.metrowin.launcher.permission.PUBLISH_TILE`, `protectionLevel="normal"`: va dichiarato con `<uses-permission>`, concesso all'installazione, nessun avviso |
| Provider | esportato; solo `call`: `query/insert/update/delete/getType` non fanno nulla |
| Metodo `publish` | extras `title` (String, max 60), `text` (String, max 160), `count` (Int, 0..99 999) |
| Metodo `clear` | nessun extra: toglie il contenuto dell'app chiamante |
| Risposta | `Bundle`: `accepted` (Boolean) e, se `false`, `reason` (String) |
| Motivi di rifiuto | `unknown caller`, `turned off by the user`, `unknown method` |

Esempio (Kotlin):

```kotlin
val extras = Bundle().apply { putString("title", "Posta"); putString("text", "3 nuovi messaggi"); putInt("count", 3) }
val answer = contentResolver.call(Uri.parse("content://com.metrowin.launcher.tiles"), "publish", null, extras)
if (answer?.getBoolean("accepted") != true) Log.i("tile", "rifiutato: ${answer?.getString("reason")}")
```

Se MetroWin Launcher non è installato `call` lancia `IllegalArgumentException` («Unknown URI»): l'app deve prenderla.

## Regole che l'app deve conoscere
- **Il tile è sempre quello del chiamante**: il pacchetto lo decide Android (`callingPackage`), non l'extra; un extra `package` è ignorato.
  Il contenuto compare sul tile di quell'app sulla Start; se l'utente non l'ha fissata non si vede (resta salvato).
- Testo ripulito: una riga, senza caratteri di controllo, spazi doppi ridotti, troncato ai limiti. Titolo e testo vuoti: si vede solo il numero.
- Gli stessi valori ripetuti non scrivono su disco né ridisegnano: si può chiamare spesso, ma conviene solo al cambiamento.
- Al massimo 64 app; oltre, restano le più recenti.
- L'utente può spegnere la funzione (`thirdPartyTiles` nelle impostazioni): `accepted=false`, `reason="turned off by the user"`.
- Contenuto salvato in `Prefs.TILES`, in chiaro (vedi `docs/progetti/cifratura.md`).
- Nessuna immagine, nessun colore, nessun clic personalizzato: il tile apre l'app.

## Versionamento (fatto)
Il metodo `version` risponde `Bundle{accepted=true, version=1}` (anche quando l'utente ha spento la funzione, ma non a un chiamante sconosciuto). Il contratto 1 non cambia mai il significato di `title/text/count`:
1. Compatibile (senza cambiare versione): campi nuovi opzionali in `publish`; il launcher ignora gli extra che non conosce.
2. Incompatibile (versione 2): nuova autorità o nuovo metodo.
3. Ogni modifica a nomi/limiti rompe `TilePublishContractTest`: va cambiato insieme a questo documento.

## Controlli di sicurezza (fatti)
- Il chiamante è quello che dice Android (`callingPackage`); un extra `package` è ignorato; senza chiamante noto: `unknown caller`.
- Testo ripulito (una riga, niente caratteri di controllo, troncato), numero in 0..99 999, al massimo 64 app (le più recenti restano).
- **Limite di frequenza**: una modifica a meno di 250 ms dall'ultima della stessa app è rifiutata (`too fast`); gli stessi valori ripetuti sono sempre accettati e non scrivono (un tile non è un flusso, e una app che gira in ciclo non consuma la memoria del launcher).
- L'utente può spegnere la funzione: `turned off by the user`. Il permesso resta `normal`: vedi i rischi qui sotto.

## SDK
- `tile-sdk/`: libreria Android (Apache-2.0, `com.metrowin.launcher.tilesdk.MetroTile`): `publish(context, title, text, count)`, `clear`, `version`, `isInstalled`; accorcia il testo prima di inviarlo; ogni errore (launcher assente, permesso mancante) è un risultato (`MetroTileResult`), mai un'eccezione. Il suo manifest porta nel manifest dell'app il permesso e il `<queries>` del provider.
- `sample-tile/`: app di esempio (nessuna dipendenza oltre all'SDK): si installa accanto al launcher, si fissa sulla Start, «Publish» fa girare il tile.
- Si costruiscono con `./gradlew :tile-sdk:assembleRelease :sample-tile:assembleDebug` (in CI). Il checksum del plugin `com.android.library` in `gradle/verification-metadata.xml` coincide con quello pubblicato da Google Maven.
- Da fare: pubblicare l'AAR su Maven Central o JitPack (serve un account e una chiave di firma dell'autore) e provare su telefono: fissare l'app di esempio sulla Start e vedere il numero e il testo che si girano.

## Rischi
- `protectionLevel="normal"`: qualunque app installata può scrivere sul proprio tile. Non può impersonare un'altra app, ma può mostrare testo
  ingannevole sul proprio tile (limite 160 caratteri, nessun link). Alternativa se serve: `signature` (nessuna terza parte) o un elenco consentito scelto dall'utente.
- Visibilità dei pacchetti (Android 11+): l'app chiamante deve dichiarare `<queries><provider android:authorities="com.metrowin.launcher.tiles" /></queries>`,
  altrimenti `call` fallisce come se il launcher non ci fosse. Da mettere anche nel manifest dell'SDK.
- I produttori aggressivi possono fermare il launcher (`docs/progetti/sfondo-affidabile.md`); la chiamata fa ripartire il processo del provider.

## SDK: passi (originali)
1. Modulo `tile-sdk` (libreria Android con una sola classe `MetroTile.publish(context, title, text, count)` e `clear`) con `consumer` manifest che dichiara il permesso.
2. Pubblicarlo su Maven Central o JitPack; licenza GPL-3.0 del launcher: l'SDK dovrebbe essere Apache-2.0 per poter essere usato da app non libere.
3. App di esempio che si installa accanto al launcher; prova su telefono: fissare l'app sulla Start e vedere il numero e il testo che si girano.

## Cosa serve per provare su telefono
Un'app di prova con il permesso, il launcher installato e il tile di quell'app fissato sulla Start. L'adb non basta: `adb shell content call --uri content://com.metrowin.launcher.tiles --method publish --extra title:s:Prova --extra text:s:Ciao --extra count:i:3`
gira come utente shell, che non ha il permesso `PUBLISH_TILE`: dà `SecurityException`.
