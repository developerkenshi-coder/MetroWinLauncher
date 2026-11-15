# Il super tile (InfoTile): le scelte, come sono ordinate

Il menu del super tile è fatto come le impostazioni del launcher: due livelli, mai di più. La **radice** elenca le categorie (icona, nome, una riga, la «i» che spiega) e mostra il tile dal vivo in alto; una **categoria** è un pivot delle sue pagine. Ogni scelta ha la sua «i» in sei lingue. Si apre quando il tile viene messo su Start (prima propone un modello da cui partire) e di nuovo dalla matita del tile in modalità modifica di Start (e dall'azione «Modifica questo tile» di TalkBack).

Il codice è in `ui/start/InfoEditor*.kt`; l'elenco di tutte le scelte è `InfoIndex` (`InfoEditorIndex.kt`), controllato da `InfoDataTest`. I testi stanno in `values*/strings_info_tile.xml` (prefissi `infoc_` categoria, `infop_` pagina, `infos_` titolo di una scelta, `infoi_` testo della sua «i», `infoe_` altri testi del menu, `infot_` testi dei tile, `infom_` modelli).

## Struttura

| categoria | pagine (pivot) | scelte |
|---|---|---|
| cosa mostra | il giorno · il telefono · foto e meteo · dal web | le 32 facce, una per interruttore (con data, appunto, app, widget dove serve) |
| ordine e ritmo | ordine · intelligente · programmi | ordine delle facce, ritmo, tempo per faccia; ordine intelligente, risparmio, «presto», notte, batteria, pioggia; facce per orario e regole «solo quando» |
| ora e fusi | ora | fusi (fino a tre), orologio a lancette, timer veloci, foto dietro l'ora |
| calendario e meteo | calendario · meteo | calendari scelti; città del meteo del tile |
| foto | foto · ricordi | album, foto scelte a mano, preferiti, senza screenshot, cartelle escluse; giorni intorno ai ricordi |
| aspetto | aspetto | puntini, colore che segue ciò che dice, pulsante per condividere un ricordo, facce affiancate |
| privacy e automatismi | privacy · da solo | titoli e foto nascosti, angolo bambini; Non disturbare, ora di casa in viaggio, rete Wi-Fi, audio Bluetooth, riepilogo del mattino |
| modelli e condivisione | modelli | modelli, condividi/importa come testo, leggi a voce, torna all'inizio |

## Come è tenuta la scelta

Una stringa corta nel tile (`InfoConfig.encode()`): le lettere delle prime sette facce (come prima, una versione vecchia le legge ancora), i due interruttori `S` ed `E`, poi coppie `;chiave=valore`. L'ordine scelto dall'utente sta in `;o=`; le chiavi che questa versione non conosce si conservano così come sono. Per passare una scelta a un altro telefono si usa il testo `metrowin-tile:…` senza album, appunti, reti, foto, widget (`shareable()`).

## Cosa fa il tile

- `InfoPlan` decide le facce di adesso: angolo bambini o Non disturbare, poi un piano per rete/Bluetooth, poi per orario, poi le regole, poi le facce senza dati escono, poi l'ordine intelligente. Puro, provato da `InfoTileMoreTest`.
- `InfoFace` le gira (un evento dopo l'altro, le parti del meteo, le foto), con la sosta che serve a leggerle. Con «riduci movimento» o in risparmio cambia faccia senza muoversi (lento in risparmio). Con TalkBack acceso non gira da solo: l'azione «Faccia successiva» la cambia.
- I dati nuovi si leggono solo se un tile li chiede (`InfoNeeds`): foto e ricordi per ogni scelta diversa (una query per anno per i ricordi), calendari, settimana, giornata.

## Non fatto

- Miniature delle foto della misura del tile (resta 720 px per tutte).
- Persone: il tocco apre Persone, non chiama.
- «Ospite» nell'angolo bambini: vale solo l'angolo bambini.
