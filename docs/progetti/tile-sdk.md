# Tile di terze parti: protocollo e SDK

Stato: **contratto documentato e bloccato da test** (`TilePublishContractTest`, `ThirdPartyTilesTest`). Nessun SDK (libreria) ancora.

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

## Versionamento (da fare)
Oggi non c'è numero di versione: i campi sono tre e fissi. Regola proposta:
1. Aggiungere il metodo `version` che risponde `Bundle{version=1}` (i client vecchi non lo chiamano, quelli nuovi lo usano per scegliere i campi).
2. Compatibile (senza cambiare versione): campi nuovi opzionali in `publish`; il launcher ignora gli extra che non conosce.
3. Incompatibile (versione 2): nuova autorità o nuovo metodo, mai cambiare significato a `title/text/count`.
4. Ogni modifica a nomi/limiti rompe `TilePublishContractTest`: va cambiato insieme a questo documento.

## Rischi
- `protectionLevel="normal"`: qualunque app installata può scrivere sul proprio tile. Non può impersonare un'altra app, ma può mostrare testo
  ingannevole sul proprio tile (limite 160 caratteri, nessun link). Alternativa se serve: `signature` (nessuna terza parte) o un elenco consentito scelto dall'utente.
- Visibilità dei pacchetti (Android 11+): l'app chiamante deve dichiarare `<queries><provider android:authorities="com.metrowin.launcher.tiles" /></queries>`,
  altrimenti `call` fallisce come se il launcher non ci fosse. Da provare su telefono; da mettere anche nel manifest dell'SDK.
- I produttori aggressivi possono fermare il launcher (`docs/progetti/sfondo-affidabile.md`); la chiamata fa ripartire il processo del provider.

## SDK: passi
1. Modulo `tile-sdk` (libreria Android con una sola classe `MetroTile.publish(context, title, text, count)` e `clear`) con `consumer` manifest che dichiara il permesso.
2. Pubblicarlo su Maven Central o JitPack; licenza GPL-3.0 del launcher: l'SDK dovrebbe essere Apache-2.0 per poter essere usato da app non libere.
3. App di esempio che si installa accanto al launcher; prova su telefono: fissare l'app sulla Start e vedere il numero e il testo che si girano.

## Cosa serve per provare su telefono
Un'app di prova con il permesso, il launcher installato e il tile di quell'app fissato sulla Start. L'adb non basta: `adb shell content call --uri content://com.metrowin.launcher.tiles --method publish --extra title:s:Prova --extra text:s:Ciao --extra count:i:3`
gira come utente shell, che non ha il permesso `PUBLISH_TILE`: dà `SecurityException` (da provare anche questo).
