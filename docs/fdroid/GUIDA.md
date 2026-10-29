# MetroWin Launcher su F-Droid: come funziona e come si cambia

Questa guida spiega dove vive ogni cosa che F-Droid mostra (nome, descrizioni, immagini, changelog), come si cambiano,
come si rilascia una versione nuova e che cosa succede quando si manda l'app in pubblicazione.

## Stato

La richiesta a F-Droid è **rimandata**: la policy chiede almeno 3 mesi di sviluppo attivo e il primo commit è del 24 ottobre 2026, quindi
si riprende **verso fine gennaio 2027**. Nel frattempo le versioni 26.1S, 26.2S, 26.3S e 26.4S sono pubbliche su GitHub, firmate con una chiave propria del
progetto. Quando si riprende: porta la ricetta all'ultima versione (`versionName`, `versionCode`, `CurrentVersion*`), metti nel campo `commit`
l'hash completo del tag, e segui la sezione «Che cosa succede quando mandi l'app in pubblicazione».

## In breve

F-Droid **compila l'app dal sorgente** e **firma con la propria chiave**. Per farlo usa due luoghi diversi:

| Luogo | Dove sta | Che cosa contiene | Chi lo cambia |
|---|---|---|---|
| **Il repository dell'app** | GitHub, `developerkenshi-coder/MetroWinLauncher` | codice, versione (`versionCode`, `versionName`), tag, e la cartella `fastlane/metadata/android/` con testi, immagini e changelog | tu, con un normale commit |
| **La ricetta** | GitLab, repository `fdroid/fdroiddata`, file `metadata/com.metrowin.launcher.yml` | come si compila, categorie, licenza, link, autore, come F-Droid cerca le versioni nuove | tu, ma con una **merge request** che approvano i volontari di F-Droid |

Una copia della ricetta sta in [`com.metrowin.launcher.yml`](com.metrowin.launcher.yml), così è versionata con l'app. Non la legge nessuno
da lì: è il modello da copiare nel fork di fdroiddata.

Scelta già fatta: **firma di F-Droid** (niente build riproducibile con la tua chiave). Chi ha installato una build firmata da te (per
esempio dal tuo telefono o da GitHub) deve **disinstallarla** prima di passare a quella di F-Droid, perché la firma è diversa: si fa
prima un backup dalle impostazioni del launcher. Dopo la pubblicazione questa scelta **non si può cambiare**.

## Dove si cambia che cosa

Tutti i file stanno in `fastlane/metadata/android/<lingua>/`. Le lingue ora sono `en-US` (obbligatoria: è quella di riserva) e `it-IT`.

| Cosa | File | Limite | Note |
|---|---|---|---|
| **Nome nella scheda di F-Droid** | `title.txt` | 50 caratteri | una riga |
| **Descrizione breve** | `short_description.txt` | 80 caratteri | la riga sotto il nome nell'elenco |
| **Descrizione lunga** | `full_description.txt` | 4000 caratteri | tag HTML ammessi: `<b>`, `<i>`, `<u>`, `<em>`, `<strong>`, `<br>`, `<ul>`, `<ol>`, `<li>`, `<a href>`; i ritorni a capo diventano a capo |
| **Icona** | `images/icon.png` | PNG, 512×512 | |
| **Screenshot** | `images/phoneScreenshots/1.png`, `2.png`, … | PNG o JPG | si mostrano in ordine numerico; ora sono 8 per lingua, 1080×2400 |
| **Immagine in evidenza** (facoltativa) | `images/featureGraphic.png` | PNG, 1024×500 | il banner in cima alla scheda |
| **Changelog di una versione** | `changelogs/<versionCode>.txt` | 500 caratteri | il nome è il `versionCode`: la versione 26.4S (codice 5) usa `5.txt` |
| **Nome sotto l'icona sul telefono** | `app/src/main/res/values/strings.xml` e `values-it/strings.xml`, voce `app_name` | | è un cambio dell'app, esce con la versione |
| **Categorie, licenza, autore, link, tipo di aggiornamento** | la ricetta in fdroiddata | | richiede una merge request |

Per aggiungere una lingua, crea una cartella nuova (per esempio `de-DE`) con almeno `title.txt`, `short_description.txt`,
`full_description.txt`.

Regole da ricordare:

- **Non scrivere** nome, descrizione o riassunto nella ricetta: se ci sono, hanno la precedenza sui file fastlane e poi bisogna chiedere
  ai volontari di toglierli. La ricetta deve restare senza.
- Un'immagine non può contenere cose che non sono tue: gli screenshot attuali usano solo dati inventati e foto generate al computer.
- Se un cambio di testo non compare, non è un errore: vedi la sezione seguente.

## Quando compaiono le modifiche

F-Droid legge testi e immagini **dal commit dell'ultima versione che conosce**, non dall'ultimo commit di `main`. In pratica: se cambi una
descrizione o uno screenshot oggi, lo vedi su F-Droid **dal rilascio successivo** (un nuovo tag). Se una correzione non può aspettare,
rilascia una versione piccola (per esempio 26.1.1S).

## Rilasciare una versione nuova

1. In `app/build.gradle.kts` alza `versionCode` (sempre di più, mai uguale o minore) e cambia `versionName`.
2. Scrivi `changelogs/<nuovo versionCode>.txt` in ogni lingua, massimo 500 caratteri.
3. Se serve, aggiorna testi e screenshot.
4. Fai il commit su `main` e il push.
5. **Solo dopo** crea e pubblica il tag, col nome della versione preceduto da `v`:

   ```bash
   git tag v26.2S
   git push origin v26.2S
   ```

   Il tag deve puntare al commit in cui la versione è già quella nuova. (All'inizio di questo progetto il tag `v26.1S` era stato messo
   un commit **prima** del cambio di versione: F-Droid avrebbe compilato un APK diverso dalla ricetta.)
6. Basta questo: la ricetta ha `UpdateCheckMode: Tags` e `AutoUpdateMode: Version`, quindi il sistema di F-Droid trova il tag, legge
   `versionName` e `versionCode` dal file gradle, aggiunge la build alla ricetta e la compila da solo. **Nessuna merge request.**

Regole: il nome del tag è sempre `v` + `versionName`; un tag che F-Droid ha già visto **non si sposta e non si cancella**: se hai
sbagliato, alza la versione e fai un tag nuovo.

## Che cosa succede quando mandi l'app in pubblicazione

1. Si copia la ricetta nel fork di fdroiddata (ramo `com.metrowin.launcher`, un solo commit "New app: MetroWin Launcher") con il
   `commit` della build uguale all'**hash completo** del commit taggato.
2. Si fa il push sul tuo fork su GitLab: parte da sola la pipeline di controllo (lint, compilazione, scansione). Si guarda nel menu
   *CI/CD* del fork che sia verde.
3. Si apre la **merge request** verso `fdroid/fdroiddata`, col titolo **`New app: MetroWin Launcher`**, compilando il modello.
4. Un volontario la guarda, può fare domande o chiedere modifiche: **rispondi presto** e correggi nello stesso ramo.
5. Quando la MR viene unita, F-Droid compila e pubblica. I tempi non sono garantiti: dipendono dai volontari e dalla coda di
   compilazione (la FAQ di F-Droid ne parla).

Testo suggerito per la MR, dove il modello chiede perché non c'è la build riproducibile: *"Reproducible builds are not enabled
because the project is new and no signed release APK is published yet; F-Droid signing is used. No key is committed to the
repository, the release build is unsigned unless a local keystore.properties exists."*

## Che cosa potrebbero obiettare i revisori

- **Sviluppo attivo da almeno 3 mesi** (punto 6 della policy di inclusione). Il primo commit di questo progetto è del 24 ottobre 2026,
  quindi la regola si compie a fine gennaio 2027. Fa eccezione chi ha utenti già dimostrati o già mantiene altre app su F-Droid. Se
  la MR viene rimandata per questo, non è un rifiuto: si riapre quando è passato il tempo. I volontari consigliano, per chi è alla
  prima volta, di aprire prima una richiesta nel tracker [RFP](https://gitlab.com/fdroid/rfp/-/issues).
- **Servizio di rete non libero** (anti-feature `NonFreeNet`): il riquadro Notizie legge Google News di base, ma si può mettere qualsiasi
  feed RSS e il riquadro è facoltativo. Se lo chiedono, si spiega così oppure si accetta l'etichetta.
- **Marchi di terzi**: il nome «MetroWin» e i riferimenti a Windows Phone nei testi. I nomi dei riquadri stock sono ora generici
  (Browser, Hub giochi, Videochiamate, Documenti…) e il browser ha un glifo a finestra con globo. Restano i glifi di Skype (una S nel
  cerchio), Xbox (una X nel cerchio) e Cortana (due anelli): se i revisori lo chiedono, si cambiano in `Glyphs.kt`.
- **Permessi e servizi**: `QUERY_ALL_PACKAGES` (vedere tutte le app per la lista), il servizio di accessibilità (solo per bloccare lo
  schermo e per l'Action Center sopra le altre app). Lo scopo di ciascuno è nella tabella dei permessi della [documentazione](../DOCUMENTAZIONE.md#permessi).

## Come rifare gli screenshot con l'emulatore

Gli screenshot attuali vengono dall'emulatore Android *Medium Phone* (1080×2400, 420 dpi). Per rifarli, per esempio dopo un cambio di
aspetto:

1. Compila e installa: `./gradlew assembleOptimized`, poi `adb install -r app/build/outputs/apk/optimized/app-optimized.apk`.
2. Imposta il launcher come Home e dai i permessi: `adb shell cmd role add-role-holder android.app.role.HOME com.metrowin.launcher`.
3. Barra di stato pulita (nessuna icona di Google, ora e batteria a posto):

   ```bash
   adb shell settings put global sysui_demo_allowed 1
   adb shell am broadcast -a com.android.systemui.demo -e command enter
   adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200
   adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
   adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
   ```

4. Dati **inventati**: contatti e chiamate perse (`adb shell content insert --uri content://com.android.contacts/...`), eventi in un
   calendario locale, foto generate al computer in `/sdcard/Pictures` (niente immagini con diritti), notifiche da piccole app di prova.
   L'emulatore deve avere il Wi-Fi acceso, altrimenti il meteo non funziona.
5. Cambia lingua dell'app con `adb shell cmd locale set-app-locales com.metrowin.launcher --locales it-IT` (e `en-US`), e il formato
   24 ore con `adb shell settings put system time_12_24 24`.
6. Cattura a risoluzione piena: `adb exec-out screencap -p > 1.png`, nella cartella `images/phoneScreenshots/` della lingua giusta.

Attenzione: non disattivare le app di Google dell'emulatore per «pulire» la lista app, perché i riquadri di Start legati a quelle
app spariscono e vanno ripristinati da *Impostazioni → schermata start → ripristina Start* (da toccare due volte).

## Comandi utili per controllare la ricetta

Con `pip install fdroidserver`, dentro il clone del fork di fdroiddata:

```bash
fdroid lint com.metrowin.launcher          # nessun avviso sulla ricetta
fdroid rewritemeta com.metrowin.launcher   # ordina e formatta; poi non deve cambiare più nulla
fdroid checkupdates com.metrowin.launcher  # controlla che veda il tag e la versione
fdroid build -v -l com.metrowin.launcher   # compilazione completa (di solito solo su Linux)
```

La compilazione completa la fa comunque la pipeline di GitLab quando fai il push.
