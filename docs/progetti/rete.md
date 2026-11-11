# Impostazioni di rete complete (tipo di rete, APN, hotspot, VPN)

Stato: **documentata**; nessuna implementazione. Prove fatte su emulatore `Medium_Phone` (Android 17, API 37) con `adb shell` (identità shell, senza SIM né Shizuku): **un comando verificato sull'emulatore non è verificato su un telefono con SIM**.

## Comandi verificati sull'emulatore
| Funzione | Comando | Esito sull'emulatore | Note |
|---|---|---|---|
| Dati mobili | `cmd phone data enable` / `data disable` | esce con 0; `settings get global mobile_data` passa a 1 / 0 | provato; su un telefono con SIM va visto lo stato vero della rete |
| Modalità aereo | `cmd connectivity airplane-mode enable|disable` (senza argomento: stato) | `settings get global airplane_mode_on` 1/0; Wi-Fi si riaccende alla fine | provato |
| Wi-Fi acceso/spento | `cmd wifi set-wifi-enabled enabled|disabled` | elencato nell'aiuto; il blocco `cmd wifi status` mostra rete e RSSI | già usato dal launcher (`WifiShell`, `ShizukuRadio`) |
| Elenco/connessione Wi-Fi | `cmd wifi list-networks`, `connect-network <ssid> open|wpa2|wpa3 [passphrase]`, `forget-network <id>`, `list-scan-results`, `start-scan` | `list-networks` provato | la passphrase finirebbe nella riga di comando: va passata senza registro (`withMessage=false`) |
| Tipo di rete preferito | `cmd phone get-allowed-network-types-for-users` | restituisce l'elenco (GPRS…NR) | `set-allowed-network-types-for-users <BITMASK>`: senza argomento risponde «No NETWORK_TYPES_BITMASK». Serve SIM e il comportamento dipende dall'operatore |
| Hotspot | `cmd wifi` **non ha** `start-softap`: solo `get-softap-supported-features`; `cmd tethering` risponde «No shell command implementation» | **nessun comando** | l'unica via è `TetheringManager.startTethering(...)` dal processo di Shizuku (permesso `TETHER_PRIVILEGED`): richiede il servizio utente, vedi `docs/shizuku-user-service.md` |
| VPN | `cmd vpn_management` / `cmd vcn` non esistono | **nessun comando** | si può solo aprire la schermata VPN (`Settings.ACTION_VPN_SETTINGS`) o far partire un'app VPN (`VpnApps`) |
| APN | nessun `cmd`; `cmd phone cc` risponde «Permission denied» | **nessun comando** | gli APN stanno in `Telephony.Carriers` (permesso `WRITE_APN_SETTINGS`, solo app di sistema): solo aprire `Settings.ACTION_APN_SETTINGS` |
| Firewall per app | `cmd connectivity set-package-networking-enabled true|false <pacchetto>`, `set-background-networking-enabled-for-uid` | elencati nell'aiuto (catena `OEM_DENY_3`, solo debug) | non è un'API per l'utente |

## Che cosa si può fare oggi (senza nuove dipendenze)
1. Dati mobili e modalità aereo come interruttori nel Centro azioni via Shizuku (`ShizukuRadio`/`ShizukuButtons` hanno già il modello): comando + lettura dello stato con `settings get global`.
2. Per tutto il resto: **scorciatoie alle schermate di Android** (`Settings.ACTION_NETWORK_OPERATOR_SETTINGS`, `ACTION_APN_SETTINGS`, `ACTION_VPN_SETTINGS`, `ACTION_WIFI_IP_SETTINGS`, hotspot: componente `com.android.settings.TetherSettings`, da verificare su ogni produttore), già nel catalogo `SystemSettings`: verificare che ci siano tutte.
3. Il tipo di rete: lettura sì, la modifica dipende dalla SIM e dall'operatore.

## Passi
- Hotspot: dipende dal servizio utente Shizuku (voce 1 della fascia precedente).
- Ogni interruttore: stato letto dal sistema (`settings`/`dumpsys`), mai l'ultimo valore mandato.

## Rischi
- Interrompere la connessione (aereo, dati) senza possibilità di tornare indietro: sempre via d'uscita visibile.
- Le impostazioni dell'operatore (APN, 5G forzato) possono togliere voce e dati: nessuna scrittura senza conferma.
- I comandi `cmd` cambiano fra versioni di Android; ogni comando va coperto con un «se non disponibile» che apre la schermata di Android.
