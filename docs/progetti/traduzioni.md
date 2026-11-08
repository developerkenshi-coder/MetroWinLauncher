# Traduzioni oltre le 6 lingue

Stato: **fetta implementata** (controllo automatico), il resto documentato.

## Obiettivo
Aggiungere lingue (es. ru, pl, nl, tr, ar, ja) senza che stringhe mancanti o segnaposto sbagliati rompano l'app, e lasciare che
altre persone traducano senza toccare il codice.

## Stato oggi
- 6 lingue: `values` (en), `-it`, `-de`, `-es`, `-fr`, `-pt`; 2386 stringhe e 34 `plurals` per lingua.
- `TranslationsTest` (Gradle) controlla chiavi, plurali, array e segnaposto.
- **Nuovo** `tools/check-translations.py`: stesso controllo senza Gradle, più il numero di voci degli array e i segnaposto per
  quantità dei plurali (la forma `one` può non avere il numero, non può averne uno in più). Trova da solo le cartelle `values-<codice>`.
  `tools/test_check_translations.py` lo prova su risorse finte e sulle vere. In CI gira prima di Gradle (`.github/workflows/ci.yml`).

## Passi per una lingua nuova
1. Creare `values-<codice>/strings.xml` copiando `values/strings.xml`; tradurre; `python3 tools/check-translations.py`.
2. **Plurali**: ogni lingua ha le sue quantità CLDR. Russo/polacco: `one`, `few`, `many`, `other`; arabo: `zero`..`other`. Il controllo
   attuale non esige le stesse quantità dell'inglese (non dovrebbe): lint (`MissingQuantity`) segnala quelle che mancano.
3. **Arabo/ebraico**: richiede anche il lavoro RTL (`docs/progetti/rtl.md`).
4. Tutorial e voci con data/ora: `TutorialLanguagesTest` e i test dei formati orario vanno estesi alla lingua nuova.
5. `fastlane/metadata/android/<lingua>/` per la scheda di F-Droid; README tradotto solo se c'è chi lo mantiene.
6. Riconoscimento vocale (`data/VoiceCommands.kt`, `data/Assistant.kt`): i comandi sono per lingua; senza tabella la voce funziona solo come dettatura.

## Flusso per i traduttori
- Consigliato: **Weblate** (ospitato, gratuito per software libero, usato da F-Droid): legge `values*/strings.xml` dal repository, apre
  pull request, mostra i segnaposto e i plurali. Crowdin è equivalente ma meno diffuso nel mondo F-Droid.
- Contesto: aggiungere un commento XML sopra le stringhe ambigue («Start» = schermata o verbo?). Weblate lo mostra al traduttore.
- Le stringhe nuove si scrivono prima in `values/` e in `values-it/` (lingua dell'autore); le altre restano in inglese finché non
  tradotte? **No**: il test le esige tutte. Con Weblate si passa a «fallback all'inglese» per le lingue nuove: il controllo va
  allora limitato alle 6 lingue complete (oggi basta dare a `check()` un elenco di lingue obbligatorie) e le altre solo ai segnaposto.

## Rischi
- Qualità: traduzioni automatiche senza revisione; Weblate permette di marcarle «da controllare».
- Testo troppo lungo nei tile: serve il test di troncamento (`docs/progetti/regressione-schermate.md`).

## Per provare su telefono
Lingua del dispositivo → impostazioni Android > Lingue; controllare Start, impostazioni e tutorial.
