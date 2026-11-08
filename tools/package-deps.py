#!/usr/bin/env python3
"""Grafo delle dipendenze fra i pacchetti di app/src/main (voce «moduli Gradle», docs/progetti/moduli.md).

    tools/package-deps.py                 elenco: per ogni pacchetto da chi dipende e chi dipende da lui
    tools/package-deps.py --leaves        file di data/ che non importano nulla del progetto (candidati a un modulo foglia)
    tools/package-deps.py --cycles        gruppi di pacchetti che si importano a vicenda (non separabili in moduli senza prima rompere il ciclo)
    tools/package-deps.py --check         esce con 1 se un file di data/ importa un pacchetto della UI (regola dei confini)

Legge le righe `import com.metrowin.launcher....` e i riferimenti senza import dello stesso pacchetto non si vedono: è un'approssimazione per eccesso
dei confini veri (gli oggetti dello stesso pacchetto restano invisibili, quindi i file «foglia» vanno confermati compilando).
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

SRC = Path(__file__).resolve().parent.parent / "app/src/main/java/com/metrowin/launcher"
BASE = "com.metrowin.launcher"
UI = {"ui", "calls", "gestures", "mail", "messages", "notes", "wallet", "recorder", "files", "news", "todo", "calendar", "calculator",
      "dialer", "datasense", "power", "recents", "shizukuterminal", "systemsettings", "notifications"}


def package_of(path):
    rel = path.relative_to(SRC).parent
    return rel.parts[0] if rel.parts else "(root)"


def imports(path):
    found = set()
    for m in re.finditer(rf"^import {re.escape(BASE)}\.([\w.]+)", path.read_text(encoding="utf-8"), re.M):
        parts = m.group(1).split(".")
        # `import com.metrowin.launcher.R` e le classi nella radice contano come (root)
        found.add(parts[0] if len(parts) > 1 and parts[0].islower() else "(root)")
    return found


def graph():
    deps, per_file = defaultdict(set), {}
    for f in SRC.rglob("*.kt"):
        pkg, used = package_of(f), imports(f)
        per_file[f] = (pkg, used)
        deps[pkg] |= used - {pkg}
    return deps, per_file


if __name__ == "__main__":
    deps, per_file = graph()
    if "--leaves" in sys.argv:
        for f, (pkg, used) in sorted(per_file.items()):
            if pkg == "data" and not (used - {"data"}):
                print(f.relative_to(SRC))
    elif "--cycles" in sys.argv:
        # Tarjan: componenti fortemente connesse del grafo dei pacchetti.
        index, low, stack, on, out, counter = {}, {}, [], set(), [], [0]

        def visit(v):
            index[v] = low[v] = counter[0]
            counter[0] += 1
            stack.append(v)
            on.add(v)
            for w in deps.get(v, ()):
                if w not in index:
                    visit(w)
                    low[v] = min(low[v], low[w])
                elif w in on:
                    low[v] = min(low[v], index[w])
            if low[v] == index[v]:
                group = []
                while True:
                    w = stack.pop()
                    on.discard(w)
                    group.append(w)
                    if w == v:
                        break
                out.append(sorted(group))

        for v in sorted(deps):
            if v not in index:
                visit(v)
        for g in sorted(out, key=len, reverse=True):
            print(f"{len(g)} packages: {', '.join(g)}")
    elif "--check" in sys.argv:
        bad = [f"{f.relative_to(SRC)} -> {sorted((used & UI))}" for f, (pkg, used) in sorted(per_file.items()) if pkg == "data" and used & UI]
        print("\n".join(bad) or "ok: data/ does not import the screens")
        sys.exit(1 if bad else 0)
    else:
        users = defaultdict(set)
        for p, ds in deps.items():
            for d in ds:
                users[d].add(p)
        for p in sorted(deps.keys() | users.keys()):
            print(f"{p}\n   uses:    {', '.join(sorted(deps.get(p, ()))) or '-'}\n   used by: {', '.join(sorted(users.get(p, ()))) or '-'}")
