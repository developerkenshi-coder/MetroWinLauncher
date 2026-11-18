package com.metrowin.launcher

import java.util.Locale

/** Runs [block] with the language of the phone set to [locale], then puts the old one back: sizes and numbers are written in the language of the phone. */
fun <T> withLocale(locale: Locale = Locale.US, block: () -> T): T {
    val old = Locale.getDefault()
    Locale.setDefault(locale)
    try { return block() } finally { Locale.setDefault(old) }
}
