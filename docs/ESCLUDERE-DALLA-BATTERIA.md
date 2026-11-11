# Se il launcher si ferma da solo (Xiaomi, vivo, OPPO, Samsung...)

Alcuni produttori chiudono le app in secondo piano per risparmiare batteria. Per MetroWin Launcher questo significa: notifiche dei tile in ritardo,
promemoria e timer che non suonano, posta in tempo reale che si ferma, il launcher che riparte da zero quando torni alla Start.
I nomi dei menu cambiano con la versione del sistema: se non trovi la voce esatta, cerca nelle impostazioni «batteria» + «MetroWin».
*(Percorsi scritti dalla documentazione pubblica dei produttori; non sono stati provati su un telefono vivo o Xiaomi.)*

## Tutti i telefoni
1. **Impostazioni › App › MetroWin Launcher › Batteria › Senza restrizioni** (a volte «Non ottimizzare»). Il tutorial del launcher apre questa schermata da solo.
2. **Impostazioni › App › Accesso speciale › Sveglie e promemoria**: consentito (serve ai timer e ai promemoria).
3. **Accesso alle notifiche** e, se usi il blocco schermo Metro, il **servizio di accessibilità**: devono restare attivi. Se il sistema li spegne
   da solo dopo un po', è il segno che la voce «avvio automatico» qui sotto non è attiva.

## Xiaomi, Redmi, POCO (MIUI / HyperOS)
- **Impostazioni › App › Gestisci app › MetroWin Launcher › Avvio automatico**: attivo.
- Nella stessa schermata **Risparmio batteria › Nessuna restrizione**.
- Apri le app recenti, tieni premuta la scheda di MetroWin Launcher e scegli il **lucchetto** (blocca): così «chiudi tutto» non lo ferma.

## vivo / iQOO (Funtouch / OriginOS)
- **Impostazioni › Batteria › Consumo in background** (o *Gestione consumo in background*) › MetroWin Launcher › **Consenti consumo elevato in background**.
- **Impostazioni › App › Avvio automatico** (o *Autostart*) › MetroWin Launcher: attivo.
- Nelle app recenti, tira giù la scheda del launcher (o premi il lucchetto) per bloccarla.

## OPPO / realme / OnePlus (ColorOS / OxygenOS)
- **Impostazioni › App › Gestione app › MetroWin Launcher › Utilizzo batteria › Consenti attività in background** e **Avvio automatico**: attivi.

## Samsung (One UI)
- **Impostazioni › Batteria › Limiti di utilizzo in background**: togli MetroWin Launcher da «App in sospensione» e «App in sospensione profonda»; aggiungilo a «App mai in sospensione».

## Come controllare
Riavvia il telefono, non aprire il launcher, e fai arrivare una notifica o aspetta un promemoria di prova: deve comparire. Se non succede, la voce
«avvio automatico» del tuo produttore non è attiva. Segnalazioni: *Impostazioni › segnalazione bug* (il registro non contiene i tuoi testi).

---

# English: if the launcher stops by itself
Some makers close background apps. Give MetroWin Launcher **unrestricted battery** (Settings › Apps › MetroWin Launcher › Battery), allow **alarms and reminders**,
turn on **autostart** (Xiaomi: Manage apps › Autostart; vivo: Settings › Apps › Autostart and Battery › Background power consumption › Allow high consumption)
and **lock** its card in the recent apps. Menu names vary by version; these paths come from public vendor documentation.
