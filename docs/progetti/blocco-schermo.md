# Blocco schermo Metro rafforzato: superfici di bypass

Stato: **documentata** (analisi). Nessuna implementazione: un blocco che sembra sicuro e non lo è fa danni peggiori di nessun blocco.

## Che cosa è oggi
Il blocco Metro (`ui/lock/MetroLockScreen.kt`, `gestures/LockOverlay.kt`, `ui/lock/MetroDreamService.kt`) **non sostituisce** il blocco di Android: un launcher non può farlo.
Quando lo schermo si spegne, il servizio di accessibilità (`LockAccessibilityService`) prepara una finestra `TYPE_ACCESSIBILITY_OVERLAY` con foto, ora, sveglia e notifiche; scorrendola verso l'alto sparisce e
sotto c'è il blocco vero di Android (PIN, impronta). Con una chiamata in corso la finestra mostra una scheda e la lascia al telefono. Quindi la sicurezza dei dati è **tutta** del blocco di Android; il Metro è un abbellimento.

## Superfici di bypass (che cosa vede o fa chi tiene il telefono senza conoscere il PIN)
| Superficie | Rischio oggi | Rafforzabile? |
|---|---|---|
| Notifiche mostrate sul blocco Metro | testo dei messaggi leggibile senza PIN (l'opzione «nascondi il contenuto» esiste, `hiddenOnLock`/`lockOptions`); Android di per sé le nasconde con «contenuti sensibili» | sì: nascondere per default; non si può impedire che l'utente le attivi |
| Barra di stato / Action Center del launcher sopra il blocco | se la finestra non copre la tendina di Android, chi tira giù la tendina di sistema vede le sue notifiche (comandi rapidi inclusi) | solo con `disable-flag` (voce 12), che non è garantito |
| Pulsanti di navigazione (home, recenti) | l'overlay è sotto al keyguard reale: Android mostra il suo blocco prima di qualunque app | niente da fare |
| Il servizio di accessibilità spento dal sistema (o dall'utente) | il blocco Metro sparisce senza avviso; resta quello di Android | rilevabile (`LockAccessibilityService.instance == null`), non impedibile |
| Finestra caricata a schermo spento, mostrata a schermo acceso | per un attimo può comparire il contenuto prima del keyguard (sfarfallio) | ritardo di un frame |
| Foto di sfondo, orologio, sveglia | nessun dato sensibile | — |
| Scheda della chiamata nel blocco | mostra durata, silenzia e termina (`LockCall`): non sblocca nulla, ma dice che c'è una chiamata; la schermata completa si apre sopra il keyguard solo per le chiamate in arrivo | — |
| Assistente vocale / comandi dal blocco | il riconoscimento vocale del launcher gira con lo schermo bloccato? Non risulta | — |
| Riavvio in modalità provvisoria | il servizio di accessibilità non parte: nessun blocco Metro | non impedibile |
| `adb` con debug USB acceso e telefono sbloccato una volta | accesso al dato privato (con `run-as` solo su debug) | fuori dall'ambito |

## Che cosa non si può garantire (e va detto all'utente)
1. Che il blocco Metro sia **sempre** presente: dipende da un servizio di accessibilità che il sistema o il produttore può fermare (`docs/progetti/sfondo-affidabile.md`).
2. Che copra tutto lo schermo: le aree di sistema (barra di stato, gesture di navigazione) appartengono al keyguard e alla SystemUI; senza `STATUS_BAR` privilegiato o root non si bloccano.
3. Che **non sia un blocco**: chi lo supera arriva comunque al keyguard di Android; per l'utente la protezione resta il PIN/impronta di Android.
4. Che `FLAG_SECURE` e simili non siano aggirabili con screenshot di sistema: lo screenshot via accessibilità (`GLOBAL_ACTION_TAKE_SCREENSHOT`) cattura anche l'overlay.

## Passi possibili (non fatti)
- Scheda «Sicurezza» nelle impostazioni del blocco che dice chiaramente: «questo è un aspetto, non un blocco; il PIN è quello di Android» e mostra lo stato (servizio acceso? contenuti nascosti?).
- Valori predefiniti prudenti: contenuto delle notifiche nascosto sul blocco, nome del chiamante nascosto, finestra fuori solo quando il servizio è vivo.
- Test di unità: `lockOptions`/`hiddenOnLock` (già in `LockTextTest`) per ogni combinazione di impostazioni; test dell'overlay con Robolectric `ShadowWindowManager` per verificare tipo, flag e che il toccare non passi.
- Prova con il debug USB: telefono bloccato, tirare giù la tendina di sistema, tentare le gesture, riavviare in modalità provvisoria, fermare il servizio dalle impostazioni, e vedere in ogni caso che cosa è leggibile.

## Rischi
- Dare un'impressione di sicurezza che non c'è è il rischio principale: ogni testo dell'interfaccia deve dire «aspetto», non «protezione».
- Una finestra che non si toglie (bug) rende il telefono inutilizzabile: l'overlay deve sempre sparire con una chiamata in arrivo, `SafeMode` e un'uscita d'emergenza (accessibilità spenta → sparisce).
