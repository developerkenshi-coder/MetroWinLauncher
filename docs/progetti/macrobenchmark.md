# Macrobenchmark e profilo di base con soglie nel CI

Stato: **configurazione implementata e compilata, non eseguita**: modulo `:benchmark` (`com.android.test`) con `StartupBenchmark`, tipo di build `optimized` dell'app con `<profileable android:shell="true"/>` solo lì (`app/src/optimized/AndroidManifest.xml`). Nessun dispositivo è stato usato.

## Che cosa c'è
- `benchmark/build.gradle.kts`: AGP test module, `targetProjectPath = ":app"`, solo la variante `optimized` (codice di release, chiave di debug; `matchingFallbacks = release`). Dipendenze: `androidx.benchmark:benchmark-macro-junit4` 1.4.1, `uiautomator` 2.3.0, `androidx.test.ext:junit`, `espresso`.
- `StartupBenchmark`: avvio a freddo, con `CompilationMode.None()` (installazione nuova) e `Partial()` (con il profilo di base): `StartupTimingMetric`, 10 iterazioni.
- Il profilo di base che c'è è scritto a mano: `app/src/main/baseline-prof.txt` (tutte le classi e i metodi del pacchetto: `HSPLcom/metrowin/launcher/**;->**(**)**`). È largo; un profilo generato dagli avvii veri è più piccolo e preciso.
- Costo nel repository: `gradle/verification-metadata.xml` cresce di 362 righe (checksum di `androidx.benchmark`, `uiautomator`, `androidx.tracing`, `okio`, `wire`, `moshi` e di versioni nuove di alcune librerie Compose/lifecycle già usate). Prima del rilascio vanno confrontate con Maven Central/Google Maven.
- Provato: `./gradlew :benchmark:compileOptimizedKotlin :benchmark:assembleOptimized` (compila e assembla l'APK di test).

## Come si esegue (telefono vero consigliato; l'emulatore dà numeri senza senso)
```
./gradlew :benchmark:connectedOptimizedAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```
(il parametro serve solo su emulatore: un dispositivo vero non lo vuole). I risultati: `benchmark/build/outputs/connected_android_test_additional_output/.../*-benchmarkData.json`.

## Soglie nel CI (da fare)
1. Un runner con **dispositivo fisso** (non `ubuntu-latest` con emulatore: troppo variabile). Opzioni: Firebase Test Lab (non libero: contrasta con F-Droid), un telefono collegato a un runner self-hosted, o `reactivecircus/android-emulator-runner` come controllo grossolano.
2. Lo script `tools/check-benchmark.py` (da scrivere) legge il JSON e confronta `timeToInitialDisplayMs` mediana con `docs/benchmarks/baseline.json`; fallisce se peggiora oltre il 15 % o oltre la soglia assoluta (proposta: avvio freddo con profilo < 700 ms su un telefono di fascia media).
3. Baseline aggiornato a mano dopo un cambiamento voluto, con commit separato.
4. Altre misure utili: scorrimento della Start (`FrameTimingMetric`), apertura dell'elenco app, apertura del Centro azioni.

## Profilo generato (passo successivo)
Aggiungere `BaselineProfileGenerator` (`BaselineProfileRule`) che apre la Start, scorre, apre l'elenco app e le impostazioni; plugin `androidx.baselineprofile` per copiare il file in `app/src/main/`. Il file resta in git; F-Droid lo compila senza passi extra.
Controllare con la build riproducibile (`docs/progetti/build-riproducibile.md`) che il profilo generato sia stabile.

## Rischi
- Il numero di dipendenze nuove e il loro checksum.
- Misure instabili: servono ≥10 iterazioni e telefono a temperatura/batteria costanti.
- `profileable` rende il codice profilabile dalla shell: solo nel tipo `optimized`, non in `release`.
