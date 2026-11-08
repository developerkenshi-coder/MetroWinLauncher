# Assistente vocale con comprensione del linguaggio offline

Stato: **documentata**; fetta implementata = tabella dei comandi del riconoscimento vocale estesa a de, es, fr, pt (`VoiceCommands`, `VoiceLanguagesTest`).

## Stato oggi
- `data/VoiceRecognizer.kt`: `RecognizerIntent` di Android con `EXTRA_PREFER_OFFLINE`, lingua del telefono, alternative (`best` sceglie la prima che è un comando vero). Il riconoscimento (voce → testo) lo fa il servizio di Android/Google, non il launcher.
- `data/VoiceCommands.kt`: da testo a `VoiceCommand`, **senza rete e senza modello**: verbi (aprire, chiamare, cercare, accendere/spegnere la torcia), parole chiave (meteo, blocco, Action Center, elenco app, volume, luminosità, suoneria, azioni di sistema), messaggi, indicazioni, nomi di app e di impostazioni.
- `data/Assistant.kt` + `WhenParser.kt`: promemoria, eventi, note, «vicino a me», scansione (frasi in **italiano e inglese**).
- Le **tabelle in tedesco, spagnolo, francese e portoghese** erano assenti: con il telefono in tedesco «öffne Kamera» non apriva niente.

## Fetta fatta
Verbi e parole in de/es/fr/pt (scritti senza accenti, come `normalize` li lascia) per: aprire (`öffne`, `abre`, `ouvre`, `lance`, `inicia`…), chiamare (`ruf`, `llama a`, `appelle`, `liga para`…), cercare (`suche nach`, `busca`, `cherche`, `pesquisa`, `procura`…), torcia (`Taschenlampe`, `linterna`, `lampe de poche`, `lanterna`) con acceso/spento, meteo (`Wetter`, `clima`, `previsiones`, `previsao do tempo`). `VoiceLanguagesTest` ne prova 40 frasi.

Non coperto (resta in it/en): messaggi, indicazioni, volume/luminosità, suoneria, azioni di sistema, `Assistant` (promemoria, eventi, note, orari) nelle quattro lingue nuove.

## Che cosa significa «comprensione offline»
Tre livelli, dal più semplice:
1. **Tabelle e regole** (oggi): prevedibili, testabili con frasi, nessun peso. Limite: l'utente deve dire una frase simile a quelle previste; ogni lingua è un lavoro a mano.
2. **Corrispondenza fuzzy sulle intenzioni**: ogni comando con più formulazioni di esempio per lingua (file di dati, non codice) e un punteggio (parole in comune, distanza di edit, sinonimi). Si scrive in un paio di giorni, sempre testabile; copre i «voglio…», «mi serve…» e gli errori di riconoscimento. **Passo consigliato.**
3. **Modello neurale sul telefono** (classificazione di intenti o piccolo modello linguistico, es. TFLite/ONNX o un modello da pochi MB): dimensione dell'APK (+5–50 MB), licenza dei pesi, memoria, batteria, lingua per lingua; non verificabile senza telefoni. Fuori portata per un launcher F-Droid piccolo salvo come componente facoltativo scaricato.
Il riconoscimento vocale vero offline (voce → testo senza Google) richiede un motore libero (Vosk/whisper.cpp: modelli da 40 MB a 1 GB): è il progetto della registrazione/trascrizione (`recorder/Transcriber.kt`) e va deciso a parte.

## Passi
1. Spostare le frasi di `VoiceCommands` in una tabella per lingua (`VoicePhrases.kt` o risorsa) con la stessa struttura per tutte e 6 le lingue; un test che verifichi che ogni comando abbia frasi in ogni lingua.
2. Estendere messaggi, indicazioni, livelli, suoneria, azioni di sistema e `Assistant`/`WhenParser` («domani alle 18», «morgen um 18 Uhr», «mañana a las 18», «demain à 18 h», «amanhã às 18»).
3. Livello 2 (fuzzy) con soglie e un insieme di frasi di prova per lingua; metrica: percentuale di frasi riconosciute su un insieme registrato.
4. Prova con persone madrelingua e con il riconoscitore vero (errori tipici: «apri» → «a Pri»).

## Rischi
- Collisioni: un verbo di una lingua che è il nome di un'app o una parola comune in un'altra (es. «liga» in portoghese = chiama/accendi): le regole sono ordinate (chiama prima della torcia) e i test lo bloccano; nuovi verbi vanno provati contro i nomi di app.
- La qualità dipende dal riconoscimento di Google (rete), che resta fuori dal controllo del launcher.

## Per provare su telefono
Cambiare la lingua del telefono, parlare le frasi dell'elenco di `VoiceLanguagesTest` con il microfono e controllare che il comando riconosciuto sia quello atteso; contare gli errori.
