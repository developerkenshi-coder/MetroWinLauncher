# Tablet e pieghevoli

Stato: **fetta implementata** (test su 600 dp e 840 dp, nessun difetto da correggere); il resto documentato.

## Che cosa è stato verificato (Robolectric, `qualifiers = w600dp-h960dp` e `w840dp-h1200dp`)
- `TextFitTablet600Test`, `TextFitTablet840Test` (`screens/TextFitTest.kt`): le ~90 schermate del controllo di testo (impostazioni in ogni sezione, Centro azioni, menu, dialoghi, elenco app, ricerca, tutorial…) senza testo tagliato, sovrapposto o fuori dallo schermo; l'840 dp con lo stile Glass. `TextFitTablet600TourTest`: il tutorial a 600 dp.
- `WideScreensTest`: la Start con tile di ogni misura (piccola, media, larga, grande) a 600 e 840 dp: tutte dentro la larghezza, nessuna sopra un'altra; la tile media misura 130–200 dp; la calcolatrice (mini app) a 840 dp resta dentro lo schermo.
- Già esistenti per il paesaggio: `LandscapeTest`, `TutorialLandscapeTest`, `TextFitItalianLandscapeTest`.

## Osservazioni
- **A 600 dp le tile medie sono circa 188 dp** (un quarto più grandi che su telefono): le colonne vanno a blocchi di due (`TileGrid.columnsFor`: 6 colonne a 600 dp, 8 a 840 dp). Non è un difetto, ma chi ha un tablet da 600 dp vede tile grandi; l'opzione «colonne» (`settings.tileColumns`, `TileGrid.customChoices`) permette 8 anche lì.
- Le mini app (`MiniFrame`, `centeredContent`, `pageSide`) usano una colonna centrata: a 840 dp resta con ampi margini. Un layout a due riquadri (elenco + dettaglio) per posta, file e note non esiste.

## Non fatto
1. **Layout a due riquadri** sugli schermi larghi (mail, messaggi, note, file): lavoro di progetto per mini app.
2. **Pieghevoli**: reazione a piega/cerniera (`WindowInfoTracker`/Jetpack WindowManager `FoldingFeature`: modalità tavolo, due schermi), continuità fra schermo esterno e interno (il launcher è ricreato; lo stato Start è già nel `ViewModel`/impostazioni). Serve la dipendenza `androidx.window` e un pieghevole vero o l'emulatore di pieghevoli di Android Studio.
3. **Multi-finestra e split screen**: `resizeableActivity`, ridimensionamento in corso; il test `TextFit` non lo copre.
4. **Orientamento a 600 dp**: i tablet sono usati in orizzontale; il test paesaggio c'è per 700×340 e 800×360, non per 960×600.

## Per provare
Emulatore «Resizable» (API 37) e «Pixel Fold» in Android Studio: Start in tutte le piegature, rotazione, split screen, ridimensionamento a caldo, poi un tablet vero.
