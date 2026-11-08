# App Fotocamera in stile Lumia

Stato: **documentata**, non implementata (richiede il telefono: l'emulatore ha una camera finta).

## Obiettivo
Una mini app «Fotocamera» come quella dei Lumia: tasto di scatto dedicato in basso, anteprima a tutto schermo, tasti per lampo/timer/lenti, cambio fronte/retro, passaggio rapido a video, e «lenses» (app che si aggiungono) fuori ambito.

## Stato oggi
- Tile stock `camera` (`data/MetroCatalog.kt`): apre l'app della fotocamera del telefono (`MediaStore.ACTION_IMAGE_CAPTURE` e i pacchetti noti). Nessun permesso `CAMERA` nel manifest.
- Gesto/azione rapida `QuickAction.Camera` e tile «ultima foto» (`AddMenuIndex`): leggono dalla galleria, non scattano.

## Scelta tecnica
**CameraX** (`androidx.camera:camera-core/camera2/lifecycle/view` ≥ 1.4): una dipendenza stabile, `PreviewView` (o `viewfinder-compose`) per l'anteprima, `ImageCapture` per le foto, `VideoCapture` per i video, `CameraControl` per lampo/zoom/messa a fuoco.
Camera2 diretta darebbe controlli manuali (ISO, esposizione) ma è mesi di lavoro e di prove per telefono: fuori dal progetto iniziale.

## Passi
1. Dipendenze in `gradle/libs.versions.toml` + `app/build.gradle.kts`, **con** voci nuove in `gradle/verification-metadata.xml` e regola R8 se servono. Controllare con la scansione di F-Droid: CameraX è Apache 2.0 e senza servizi Google.
2. Manifest: `<uses-permission android:name="android.permission.CAMERA"/>` e `<uses-feature android:name="android.hardware.camera" android:required="false"/>` (il launcher gira anche su dispositivi senza camera). Registrazione video: `RECORD_AUDIO` c'è già.
3. `camera/CameraActivity.kt` (`MiniActivity`) + `CameraScreen.kt` (anteprima, tasto di scatto, ruota lenti) + `CameraSession.kt` (lega `ProcessCameraProvider` al ciclo di vita).
4. Permessi a runtime con `rememberLauncherForActivityResult(RequestPermission)`; senza permesso la schermata offre «Apri la fotocamera del telefono» (comportamento di oggi).
5. Salvataggio: `MediaStore` (`RELATIVE_PATH = Pictures/MetroWin`), nessun accesso al disco intero; geotag solo se l'utente lo attiva (permesso di posizione già usato dal meteo, ma non automatico).
6. Test unitari possibili: logica pura (nome file, scelta di lente, timer, stato del lampo); non è possibile in Robolectric provare anteprima e scatto reali.
7. Registrare il tile `camera` → `CameraActivity` solo dopo le prove (oggi apre l'app di sistema: un difetto qui lascia l'utente senza fotocamera). Interruttore nelle impostazioni «usa la fotocamera di MetroWin», spento di default.

## Rischi
- Differenze enormi fra produttori (HDR, lenti multiple, modalità notte): l'app di sistema fa meglio; CameraX copre il comune denominatore.
- Peso: CameraX aggiunge circa 1–2 MB all'APK e un permesso che spaventa chi ha scelto un launcher; per questo l'opzione resta facoltativa.
- Privacy: nessun invio in rete; il permesso si chiede solo all'apertura della schermata.

## Per provare su telefono
Telefono vero con più lenti: foto, video, lampo, cambio fronte/retro, rotazione, permesso negato, permesso revocato mentre l'app è aperta, app che parte dal tasto di scatto fisico (`android.intent.action.CAMERA_BUTTON`), foto e video nella galleria e con il formato giusto.
