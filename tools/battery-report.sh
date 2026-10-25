#!/usr/bin/env sh
# Raccoglie dal telefono (collegato con il debug USB) quello che serve per capire quanto lavora MetroWin Launcher.
# Si esegue sul computer a cui il telefono è collegato, con adb installato.
#
#   tools/battery-report.sh reset      all'inizio: azzera le statistiche della batteria, dei processi e del log
#   (usa il telefono come sempre per qualche ora, staccato dal cavo)
#   tools/battery-report.sh collect    alla fine: scrive battery-report-<data>.tar.gz da mandare indietro
#
# Si legge soltanto: nessun comando cambia impostazioni. "reset" cancella solo le statistiche di consumo di Android (che
# ricominciano da zero) e il log. Non si raccoglie il contenuto delle notifiche, delle foto o dei contatti; ci sono i nomi dei
# pacchetti delle app (le statistiche di Android li elencano) e le righe di log del launcher. Non serve il "bugreport" completo.
#
# Con più dispositivi collegati: ANDROID_SERIAL=<numero di serie> tools/battery-report.sh collect

PKG=com.metrowin.launcher
ADB=${ADB:-adb}
if ! command -v "$ADB" >/dev/null 2>&1; then
    if [ -n "$ANDROID_HOME" ] && [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
        ADB="$ANDROID_HOME/platform-tools/adb"
    else
        echo "adb non trovato: installa le platform-tools di Android o imposta ADB=/percorso/di/adb" >&2
        exit 1
    fi
fi

state=$("$ADB" get-state 2>/dev/null)
if [ "$state" != "device" ]; then
    echo "Nessun telefono pronto ($state). Controlla il cavo, 'Debug USB' acceso e l'autorizzazione sul telefono (adb devices)." >&2
    exit 1
fi

run() { "$ADB" shell "$@" 2>&1; }

case "$1" in
reset)
    run dumpsys batterystats --reset >/dev/null
    run dumpsys batterystats --enable full-wake-history >/dev/null
    run dumpsys procstats --clear >/dev/null
    "$ADB" logcat -c
    echo "Fatto: statistiche azzerate. Usa il telefono come sempre, staccato dal cavo, per almeno 3-4 ore (meglio una giornata),"
    echo "poi esegui: $0 collect"
    ;;
collect)
    stamp=$(date +%Y%m%d-%H%M)
    out="battery-report-$stamp"
    mkdir -p "$out"

    {
        echo "== telefono"
        for p in ro.product.manufacturer ro.product.model ro.build.version.release ro.build.version.sdk ro.build.version.incremental; do
            echo "$p=$(run getprop $p)"
        done
        echo; echo "== batteria adesso"; run dumpsys battery
        echo; echo "== schermo (frequenza, modalita)"; run dumpsys display | grep -iE "refreshrate|activeMode|mDefaultModeId|renderFrameRate|peak|min_refresh" | head -40
        echo; echo "== impostazioni frequenza"; for k in peak_refresh_rate min_refresh_rate refresh_rate_mode; do echo "$k=$(run settings get system $k)"; done
    } > "$out/telefono.txt"

    {
        echo "== pacchetto (versione, debuggable?, data installazione, firma)"
        run dumpsys package $PKG | grep -E "versionName|versionCode|flags=|firstInstallTime|lastUpdateTime|userId=|installerPackageName|signatures|Signing|DEBUGGABLE"
        echo; echo "== fascia di standby"; run am get-standby-bucket $PKG
        echo; echo "== esente dall'ottimizzazione della batteria?"; run dumpsys deviceidle whitelist | grep $PKG || echo "(no: ottimizzazione attiva)"
        echo; echo "== servizi in esecuzione"; run dumpsys activity services $PKG
        echo; echo "== processo (priorita, stato)"; run dumpsys activity processes | grep -i -B2 -A10 "$PKG" | head -80
        echo; echo "== accesso alle notifiche / servizi gestiti"; run dumpsys notification | grep -i "$PKG" | head -40
    } > "$out/app.txt"

    run dumpsys batterystats --charged $PKG > "$out/batterystats-app.txt"
    run dumpsys batterystats --charged > "$out/batterystats-completo.txt"
    run dumpsys procstats --hours 24 $PKG > "$out/procstats-24h.txt"
    run dumpsys cpuinfo | grep -iE "$PKG|Load:|TOTAL" > "$out/cpu.txt"
    run dumpsys power > "$out/power.txt"
    run dumpsys alarm | grep -i -B1 -A12 "$PKG" > "$out/allarmi.txt"
    run dumpsys jobscheduler $PKG > "$out/job.txt"
    { run dumpsys notification stats || run dumpsys notification --stats; } > "$out/notifiche-statistiche.txt"
    run dumpsys meminfo $PKG | head -40 > "$out/memoria.txt"

    pid=$(run pidof $PKG | tr -d '\r')
    if [ -n "$pid" ]; then
        "$ADB" logcat -d -v threadtime --pid="$pid" | tail -n 3000 > "$out/log-launcher.txt"
    else
        echo "il launcher non e' in esecuzione" > "$out/log-launcher.txt"
    fi

    tar czf "$out.tar.gz" "$out" && rm -rf "$out"
    echo "Scritto $out.tar.gz: mandamelo."
    ;;
*)
    echo "Uso: $0 reset | collect" >&2
    exit 2
    ;;
esac
