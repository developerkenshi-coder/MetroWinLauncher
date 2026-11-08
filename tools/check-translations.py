#!/usr/bin/env python3
"""Controlla le traduzioni: ogni stringa, plurale e array di values/ esiste in ogni lingua e i segnaposto (%1$s, %d ...) coincidono.

    tools/check-translations.py [cartella res]     (esce con 1 se trova problemi)

Lo stesso controllo c'è in TranslationsTest (Gradle); questo gira in CI prima della compilazione, senza Gradle, e su una lingua nuova
basta aggiungere la cartella values-<codice>: viene trovata da sola.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([sSdfxXeEgGcb])")


def placeholders(text):
    """Segnaposto per posizione: «%1$d» e un «%d» in prima posizione sono lo stesso. «%%» è un segno di percentuale."""
    found = PLACEHOLDER.findall((text or "").replace("%%", ""))
    return {int(pos) if pos else i + 1: kind for i, (pos, kind) in enumerate(found)}


def read(path):
    root = ET.parse(path).getroot()
    keep = lambda e: e.get("translatable") != "false"
    strings = {e.get("name"): "".join(e.itertext()) for e in root.iter("string") if keep(e)}
    plurals = {e.get("name"): {i.get("quantity"): "".join(i.itertext()) for i in e.iter("item")} for e in root.iter("plurals") if keep(e)}
    arrays = {e.get("name"): ["".join(i.itertext()) for i in e.iter("item")] for e in root.iter("string-array") if keep(e)}
    return strings, plurals, arrays


def check(res):
    res = Path(res)
    base_strings, base_plurals, base_arrays = read(res / "values" / "strings.xml")
    problems = []
    languages = sorted(p for p in res.glob("values-*/strings.xml") if re.fullmatch(r"values-[a-z]{2,3}(-r[A-Z]{2})?", p.parent.name))
    for file in languages:
        lang = file.parent.name.removeprefix("values-")
        strings, plurals, arrays = read(file)
        for kind, base, other in (("string", base_strings, strings), ("plurals", base_plurals, plurals), ("array", base_arrays, arrays)):
            problems += [f"{lang}: {kind} missing: {k}" for k in sorted(base.keys() - other.keys())]
            problems += [f"{lang}: {kind} not in the default ones: {k}" for k in sorted(other.keys() - base.keys())]
        for k, text in base_strings.items():
            if k in strings and placeholders(text) != placeholders(strings[k]):
                problems.append(f"{lang}: {k} has other placeholders ({text!r} / {strings[k]!r})")
        for k, items in base_plurals.items():
            for quantity, text in plurals.get(k, {}).items():
                # «one» may drop the number ("un giorno"), never add one the default does not have.
                want = placeholders(items.get("other", ""))
                got = placeholders(text)
                if not got.items() <= want.items():
                    problems.append(f"{lang}: {k}[{quantity}] has other placeholders ({text!r})")
        for k, items in base_arrays.items():
            if k in arrays and len(items) != len(arrays[k]):
                problems.append(f"{lang}: array {k} has {len(arrays[k])} items, the default has {len(items)}")
    return languages, problems


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "app/src/main/res"
    langs, problems = check(root)
    print("\n".join(problems) or f"ok: {len(langs)} languages")
    sys.exit(1 if problems else 0)
