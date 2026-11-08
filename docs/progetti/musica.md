# Hub Musica nativo con libreria locale

Stato: **documentata**, non implementata (decisione motivata sotto). Il lavoro dell'hub Foto (`docs/progetti/foto.md`) fa da modello: stessa struttura, stessi permessi.

## Obiettivo
Mini app «Musica» in stile Windows Phone: pivot **Artisti / Album / Brani / Playlist**, sola lettura dalla libreria locale (`MediaStore.Audio`), riproduzione.

## Stato oggi
- Tile stock `music` (`MetroCatalog`): apre l'app musicale del telefono (`CATEGORY_APP_MUSIC`, YouTube Music, Spotify); tile dal vivo `LiveKind.Music` e pannello «in riproduzione» da `notifications/MediaSessions.kt` (legge e comanda la sessione multimediale attiva).
- Permessi già nel manifest: `READ_MEDIA_AUDIO`, `READ_EXTERNAL_STORAGE` (fino ad API 32).

## Perché non è stato fatto qui
1. La **riproduzione** è il pezzo grosso: `MediaPlayer` semplice si ferma quando l'app va in secondo piano; una riproduzione vera richiede un servizio in primo piano di tipo `mediaPlayback` (permesso `FOREGROUND_SERVICE_MEDIA_PLAYBACK`), una `MediaSession` (tasti cuffie, schermata di blocco, Bluetooth), gestione del focus audio, rumorosità (`ACTION_AUDIO_BECOMING_NOISY`), accodamento, gapless. Non è verificabile senza audio e telefono; l'emulatore ha l'audio disattivato nel modo con cui lo si avvia qui.
2. Metà dei casi d'uso passa già dall'app musicale che l'utente ha (Spotify, YouTube Music, VLC): una libreria locale in sola lettura che poi manda il brano a un'altra app non dà molto.
3. Il resto (la libreria) è la stessa cosa dell'hub Foto, ma su `MediaStore.Audio` con titoli, artisti, durata.

## Piano a fette
1. **Libreria in sola lettura**: `music/MusicLibrary.kt` (modello puro: `Track(id, uri, title, artist, album, durationMs, trackNo, year)`; raggruppamenti per artista/album, ordine, «brani recenti») con test con dati finti; `MusicReader` con `MediaStore.Audio.Media` (`IS_MUSIC != 0`, `ARTIST`, `ALBUM`, `ALBUM_ID`, `DURATION`, `TRACK`, `DISPLAY_NAME`) e un provider finto in Robolectric.
2. **Pivot** con `PivotHeader` + liste (`LazyColumn`): artisti → album → brani. Copertine con `ContentResolver.loadThumbnail` (API 29+) e ripiego a icona.
3. **Riproduzione tramite l'app esistente**: `Intent.ACTION_VIEW` con `audio/*` sul brano, o `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH` (`query`, `EXTRA_MEDIA_ARTIST`, ecc.): nessun servizio nostro, nessun nuovo permesso. Il pannello «in riproduzione» di `MediaSessions` mostra poi lo stato.
4. **Playlist**: `MediaStore.Audio.Playlists` è deprecato (rimosso da API 31 per le app); playlist proprie in un file del launcher (`Prefs`) con l'elenco di id; modifica (aggiunta/rimozione) solo dopo la sola lettura.
5. **Riproduzione propria** (`MediaPlayer`/Media3 `ExoPlayer` + `MediaSessionService`): solo se serve davvero; Media3 è una dipendenza grande (+2–3 MB) e nuovi checksum.

## Rischi
- File senza tag, brani doppi, compilation (artista = «Various Artists»): raggruppare per `ALBUM_ARTIST` quando c'è.
- Cartella dei brani in volume SD/USB, e file nascosti da `.nomedia`.
- DRM e formati (FLAC, OGG, OPUS) dipendono dal telefono.

## Per provare su telefono
Telefono con 500+ brani (tag buoni e cattivi), permesso concesso e negato, «solo audio selezionato» (`READ_MEDIA_VISUAL_USER_SELECTED` non vale per l'audio), avvio di un brano nell'app musicale, ritorno al launcher.
