# Hub Foto nativo (Album, Data, Preferiti)

Stato: **fetta implementata** = pivot di 3 pagine con griglie di miniature, sola lettura, da MediaStore (`photos/`). Provata su emulatore (vedi sotto).

## Che cosa c'è
- Tile/app **«Hub foto»** (`stock_photohub`, id `photohub`, `MetroCatalog` + `MiniApps` + `PhotosActivity` nel manifest, stringhe nelle 6 lingue). Il tile `photos` esistente resta la galleria del telefono: l'hub è un'app in più, non la sostituisce (scelta voluta, per non togliere a nessuno la sua galleria).
- `photos/PhotoLibrary.kt`: logica pura (album per nome con i senza nome in fondo, giorni dal più recente nel fuso orario, preferiti) → `PhotoLibraryTest`.
- `photos/PhotoReader.kt`: `MediaStore.Images` con `BUCKET_DISPLAY_NAME`, `DATE_TAKEN` (ripiego `DATE_ADDED`), `IS_FAVORITE` (Android 11+), limite 3000 negli argomenti della query (un `LIMIT` nell'ordinamento è rifiutato da Android 10 in poi) → `parse(cursor)` provato con `MatrixCursor`.
- `photos/PhotosScreen.kt`: titolo, `PivotHeader` + `HorizontalPager` (albums / date / favorites), griglie `LazyVerticalGrid`; toccare un album lo apre (Indietro lo chiude); toccare una foto la apre nella galleria (`ACTION_VIEW`). Stati: caricamento, permesso mancante (con spiegazione che le foto non lasciano il telefono), vuoto → `PhotosUiTest`.
- `photos/PhotoThumbs.kt`: miniature con `ContentResolver.loadThumbnail` (API 29+) o `MediaStore.Images.Thumbnails` (26–28), cache LRU di 120, caricate fuori dallo schermo.
- Permessi: `READ_MEDIA_IMAGES` (33+), `READ_MEDIA_VISUAL_USER_SELECTED` (34+: accesso parziale conta), `READ_EXTERNAL_STORAGE` (≤32): già nel manifest.

## Non fatto
Video, selezione multipla, condivisione, eliminazione, modifica, zoom e scorrimento fra foto (si apre la galleria del telefono), persone/luoghi, aggiornamento automatico quando arrivano foto nuove (si rilegge all'apertura), tile dal vivo dell'hub, riordino degli album, `READ_MEDIA_VISUAL_USER_SELECTED` con «seleziona di nuovo».

## Rischi
- Telefoni con cartelle grandi: limite di 3000; paginazione (`PagingSource`-like con offset) da fare.
- Foto in profilo di lavoro/volume SD: MediaStore dell'utente corrente soltanto.
- `IS_FAVORITE` non è riempito da tutte le gallerie dei produttori.
- Le miniature di MediaStore possono mancare per formati rari (HEIC senza decoder): resta il quadrato colorato.

## Per provare su telefono
Concedere/negare/revocare il permesso, accesso parziale (Android 14+), 5000+ foto, foto fra due fusi orari, aprire una foto e tornare, ruotare lo schermo, tema chiaro e Glass.
