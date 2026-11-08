# Miniature vere nelle app recenti

Stato: **documentata**; dipende dal servizio utente Shizuku (`docs/shizuku-user-service.md`), che non esiste ancora. Nessuna implementazione.

## Stato oggi
`recents/RecentsScreen.kt` mostra carte con l'icona dell'app (a colori) invece della miniatura della finestra: Android non dà a un launcher qualsiasi la schermata dei task.
L'elenco delle app recenti viene da `data/RecentTasks.kt` (eventi `UsageStatsManager`: «ultimo uso», non i task veri); `Recents` di accessibilità (`GLOBAL_ACTION_RECENTS`) apre quelli di Android.

## Come si ottiene una miniatura vera
La miniatura che Android conserva per un task è `TaskSnapshot`, restituita da API **nascoste**:
- `ActivityTaskManager.getService().getTaskSnapshot(taskId, isLowResolution)` → `ActivityManager.TaskSnapshot` → `HardwareBuffer` → `Bitmap.wrapHardwareBuffer`.
- Serve il permesso di sistema `READ_FRAME_BUFFER` (firma/privilegiato): **non concedibile** a un'app normale; lo ha l'identità `shell` (e root).
- Anche l'elenco dei task veri (`getRecentTasks`) serve `GET_TASKS`/`REAL_GET_TASKS`: shell sì.
Quindi la via è un **servizio utente Shizuku** (processo con identità shell) che chiama queste API e restituisce i bitmap al launcher per Binder (AIDL, `ParcelFileDescriptor` o `SharedMemory`: un bitmap supera il limite di 1 MB della transazione).

## Passi
1. Realizzare il servizio utente (voce rimasta del lavoro precedente): AIDL `IShellService`, `Shizuku.bindUserService`.
2. Estendere l'AIDL: `List<TaskInfo> recentTasks(int max)` e `ParcelFileDescriptor snapshot(int taskId, boolean lowRes)`; implementare con riflessione sulle classi nascoste (`ActivityTaskManager`, `TaskSnapshot`) **con un ripiego a ogni passo** (le firme cambiano fra Android 12, 13, 14, 15, 16, 17).
3. `RecentsScreen`: se il servizio c'è, carte con la miniatura (caricata fuori dallo schermo, cache `LruCache` di 12), altrimenti le carte di oggi.
4. Test: parte pura (mappa task → carta, ordine, ripiego) con servizio finto; la parte nascosta **non è provabile in Robolectric**.
5. Interruttore nelle impostazioni (la funzione conserva immagini di altre app: privacy, vedi sotto) e `FLAG_SECURE`: le app che lo usano (banche) restituiscono miniatura vuota; **non** aggirarlo.

## Rischi
- API nascoste: possono sparire in un aggiornamento di Android (liste `hiddenapi-flags`), il servizio gira come shell e non è soggetto al blocco, ma la firma cambia → ripiego sull'icona.
- Privacy: le miniature mostrano schermate di altre app (messaggi, banca): non salvarle su disco, tenerle in memoria, cancellarle alla chiusura; rispettare `FLAG_SECURE`.
- Memoria: bitmap grandi (a bassa risoluzione ~0,5 MB ciascuno): massimo 12 in cache.
- Richiede Shizuku in esecuzione: senza, funzione assente (come oggi).

## Per provare su telefono
Shizuku attivo, aprire 5 app (una con `FLAG_SECURE`, una in orizzontale, una in schermo diviso), aprire le recenti del launcher: miniature corrette, vuote per la app sicura, e nessuna miniatura che resta dopo la chiusura. Provare su 2 versioni di Android.
