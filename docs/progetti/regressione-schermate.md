# Regressione a schermate per le sei lingue e i temi

Stato: **fetta implementata**; l'immagine-contro-immagine **non è stata fatta** (vedi sotto).

## Che cosa c'è
`screens/TextFitTest.kt` disegna ~90 schermate (ogni sezione delle impostazioni, Centro azioni, menu, dialoghi, elenco app, ricerca, tutorial…) nel caso peggiore e per ognuna interroga ogni testo: tagliato (`…` o altezza esaurita), parola spezzata a metà, fuori dallo schermo, due testi sovrapposti. Non usa immagini: usa il risultato del layout (`TextLayoutResult`), quindi non c'è nulla di fragile da rigenerare.

| Classe | Lingua | Testo | Larghezza | Stile |
|---|---|---|---|---|
| `TextFitItalian{Normal,Stress,Huge,Landscape}Test`, `Glass`, `Droid`, `Tour` | it | 1,0 / 1,3 / 1,7 | 360, 320, 700×340 | Classic, Glass, Droid |
| `TextFitEnglishStressTest`, `TourTest` | en | 1,3 | 320 | Classic |
| **nuove** `TextFit{German,Spanish,French,Portuguese}{Stress,Tour}Test` | de, es, fr, pt | 1,3 (de: **1,15 a 360 dp**) | 320 (de 360) | Classic |
| **nuove** `TextFitTablet600Test`, `Tablet840Test`, `Tablet600TourTest` | en | 1,0 | 600, 840 | Classic, Glass |

## Che cosa ha trovato (corretto)
- **Francese**: `wx_sunshine` «Heures d’ensoleillement» andava a capo spezzando «ensoleillement» → «Heures de soleil».
- **Tedesco** (a 360 dp, testo 1,15): composti con trattino nei pulsanti stretti e nelle righe delle impostazioni andavano a capo spezzando la parola → stringhe più corte o con spazio: `qa_timeout` «Timeout», `qa_auto_brightness` «Auto Helligkeit», `ac_all_settings` e `qa_device_settings` «Android Einstellungen», `lock_sensor_own`, `lock_dream_button` (trattino → spazio), `tut_start_title` «Dein Start».

## Che cosa **non** passa (noto): tedesco a 320 dp con testo al 130 %
Provato e tolto dalla suite: 12 test su 90 e 3 su 9 del tutorial falliscono con parole composte spezzate: «Backup-Ordner», «Start-Bildschirme», «Bildschirm-Timeout», «Android-Einstellungen», «Metro-Sperrbildschirm beim Einschalten», «Bildschirmschoner-Einstellungen öffnen», «Live-Kacheln», «Flache Windows-Phone-Kacheln, wie eh und je.», «Bedienungshilfen-Dienst jetzt ausschalten», «Im Web suchen»… e il testo della prima pagina del tutorial. Sono schermi stretti (320 dp, 130 %) con parole lunghe.
Rimedio vero: sillabazione automatica (`Hyphens.Auto` in `LineBreak`/`TextStyle`, Compose ≥1.7) nei testi lunghi, o ridurre il carattere nei pulsanti (autosize). Non fatto: cambia l'aspetto di tutte le lingue.

## Perché non il confronto fra immagini
Un confronto pixel per pixel (Paparazzi, Roborazzi o gli stessi `capture()`) avrebbe falsi positivi a ogni modifica dell'antialiasing e dei font di Robolectric e costringerebbe a rigenerare ~540 immagini (90 schermate × 6 lingue). Il controllo sul layout dà lo stesso segnale (tagli, sovrapposizioni) senza immagini. Le catture `build/screenshots/*.png` servono per guardare a occhio.

## Passi
1. Un controllo di contrasto automatico (esiste `ContrastTest`) esteso ai temi chiaro/scuro/Glass/Droid per ogni schermata.
2. Aggiungere le lingue nuove (`docs/progetti/traduzioni.md`) come sottoclassi di una riga.
3. Sillabazione del tedesco e prova a 320 dp.
