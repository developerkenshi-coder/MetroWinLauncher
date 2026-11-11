# Build riproducibile verificabile da F-Droid

Stato: **fetta implementata** (una fonte di non determinismo tolta, due build confrontate). Risultati sotto.

## Che cosa rende non riproducibile una build Android
1. Informazioni di dipendenza firmate nell'APK (`dependenciesInfo`): **già spente** in `app/build.gradle.kts` (`includeInApk = false`, `includeInBundle = false`).
2. **`META-INF/version-control-info.textproto`**: AGP 8.3+ vi scrive l'hash del commit git (`revision: "..."`). Un APK compilato dalla copia di lavoro e uno compilato da F-Droid sul commit del tag hanno hash diversi (basta un commit in più, o una copia senza `.git` che non lo scrive affatto). **Corretto**: `vcsInfo.include = false` nel tipo `release`.
3. Marche di tempo e ordine dei file nello zip: AGP scrive date fisse (1981-01-01 nel file zip) e ordina i file: verificato sotto, nessun intervento.
4. Percorso assoluto della cartella di build dentro l'APK: non risulta (provata una compilazione in un'altra cartella).
5. Firma: F-Droid firma con la propria chiave; per confrontare con l'APK firmato dall'autore si confronta il contenuto senza `META-INF/*.RSA|.SF|.MF` (o si usa `apksigcopier`).
6. Profilo di base `baseline-prof.txt`: scritto a mano e stabile; un profilo generato va controllato (`docs/progetti/macrobenchmark.md`).

## Prova (risultato in fondo al file)
Tre compilazioni di `:app:assembleRelease` con `--no-build-cache --rerun-tasks` (nessuna cache), JDK 21 (Homebrew openjdk@21), dopo la correzione del punto 2:

| Build | Dove | Firma | SHA-1 dell'APK |
|---|---|---|---|
| A | cartella di lavoro | chiave dell'autore | `4bdb88b8…a814a4` |
| B | stessa cartella, subito dopo | chiave dell'autore | `4bdb88b8…a814a4` (**identico byte per byte ad A**) |
| C | copia in un'altra cartella, **senza `.git`**, senza `keystore.properties` | nessuna (release non firmata) | `7f0beafe…` |

- A e B: file identici. Il tipo di firma (APK Signature Scheme v2/v3, nel «signing block» e non fra le voci dello zip) è deterministico con la stessa chiave.
- A contro C: `diff -r` dei contenuti decompressi (`unzip`) **senza alcuna differenza**: stesse voci, stessi byte. L'unica differenza fra i file è il blocco di firma (6 653 186 contro 6 649 090 byte, 4 096 di differenza), che F-Droid sostituisce con la propria. Quindi percorso della cartella, assenza di `.git` e presenza/assenza della chiave non cambiano il codice compilato.
- Prima della correzione, l'APK conteneva `META-INF/version-control-info.textproto` con `revision: "48c8301b…"` (l'hash del HEAD di quel momento): nessun altro APK di un altro commit poteva coincidere. Ora il file non c'è.

## Per F-Droid
`docs/fdroid/com.metrowin.launcher.yml` compila con `subdir: app`, `gradle: yes`; con `Binaries:` e `AllowedAPKSigningKeys:` F-Droid pubblicherebbe l'APK firmato dall'autore dopo aver verificato che coincide. Prima di richiederlo: una build con `fdroid build` sul tag, versione di JDK uguale (Temurin 21), nessun `local.properties`/`keystore.properties` che cambi il risultato.
