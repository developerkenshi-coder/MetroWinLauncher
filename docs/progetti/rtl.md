# Lingue da destra a sinistra (RTL)

Stato: **fetta implementata** (test Robolectric `RtlTest`); problemi trovati e documentati sotto. Nessuna lingua RTL è ancora tradotta (nessuna `values-ar`/`values-he`): oggi RTL si vede solo con le «opzioni sviluppatore › forza layout RTL».

## Che cosa è stato provato (`app/src/test/.../screens/RtlTest.kt`, `LayoutDirection.Rtl` forzato con `LocalLayoutDirection`)
| Caso | Esito |
|---|---|
| Titoli del pivot (`PivotHeader`): il primo titolo parte dal bordo destro, il successivo segue a sinistra | **corretto** |
| Pivot: il titolo della pagina corrente resta al bordo di partenza (destro) su ogni pagina | **corretto**: `Modifier.offset { IntOffset(...) }` specchia già l'asse x nelle lingue RTL (`rtlAware` di Compose), quindi `-shift` non va cambiato |
| Start: le tile riempiono la riga da destra (A a destra di B) | **corretto** |
| Gli stessi controlli in LTR (guardia del test) | verdi |
Nessuna correzione è servita sul codice di produzione.

## Problemi trovati per lettura del codice (non corretti: cosmetici o non verificabili in Robolectric)
1. **Turnstile e piuma** (`ui/anim/Motion.kt`: `turnstile`, `featherIn`): `TransformOrigin(0f, 0.5f)` e `rotationY` sono **assoluti**: in RTL le carte ruotano attorno al bordo sinistro anziché destro. Correggere richiede la direzione del layout dentro `GraphicsLayerScope` (non c'è: va passata da fuori e `if (rtl) TransformOrigin(1f, 0.5f)` con `rotationY` invertito). Altri punti uguali: `ui/gesture/LauncherPager.kt:36-38`, `ui/settings/SettingsScreen.kt:263,274` (la pagina entra con `translationX` positivo = da destra), `ui/mini/PageTurn.kt:65-66`, `ui/actioncenter/ActionCenter.kt:1077`, `ui/start/StartPhoto.kt` (parallasse).
2. **Panorama** (`ui/gesture/Panorama.kt:84`): ha già un parametro `reverse`; non risulta collegato alla direzione del layout.
3. **Gesti**: swipe da sinistra/destra del launcher (indietro, Action Center, pagina precedente) usano coordinate assolute; in RTL «indietro» dovrebbe venire dal bordo destro.
4. **Icone direzionali** (frecce di Glyphs: indietro, avanti): vanno specchiate (`autoMirror`) nei `Glyphs.kt` disegnati a mano.
5. **Testo**: `TextAlign.Left/Right` e `Arrangement.Left/Right` non sono usati (controllato con grep): `Start/End` ovunque. Numeri e codici in testo arabo restano LTR (bidi di Android).
6. **Manifest**: `supportsRtl="true"` c'è. Nessun `values-ldrtl`.
7. **Lingue**: servono `values-ar`, `values-he`, plurali a 6 forme (arabo), cifre (`NumberFormat`) e il calendario/orologio (`data/ClockFormat.kt`).

## Passi
1. Passare `LayoutDirection` ai punti del punto 1 (una funzione `rtlOrigin(x)`), con test che confrontano `graphicsLayer` in LTR e RTL.
2. Test dei gesti con `performTouchInput` da bordo destro con `Rtl`.
3. `autoMirror` sulle icone direzionali (`ImageVector.autoMirror` o `Modifier.scale(-1f, 1f)` in RTL).
4. Prima lingua RTL con un traduttore madrelingua (`docs/progetti/traduzioni.md`), poi prova su telefono con arabo/ebraico.

## Per provare su telefono
Impostazioni › Sistema › Opzioni sviluppatore › «Forza direzione layout RTL», riavviare il launcher: Start, elenco app, impostazioni, centro azioni, blocco, mini app; swipe da entrambi i bordi.
