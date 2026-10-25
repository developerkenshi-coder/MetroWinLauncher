package com.metrowin.launcher.data

/** The store page of an app and the text that shares it. Pure. */
object AppLinks {
    private val PACKAGE = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$""")

    /** Whether [packageName] is a package name (and not something that would change the address). */
    fun valid(packageName: String) = PACKAGE.matches(packageName)

    /** The page of the app in the store app of the phone (`market://`). */
    fun storeUri(packageName: String): String? = packageName.takeIf(::valid)?.let { "market://details?id=$it" }

    /** The same page on the web, for a phone without a store app. */
    fun webUrl(packageName: String): String? = packageName.takeIf(::valid)?.let { "https://play.google.com/store/apps/details?id=$it" }

    /** "Name https://…": what the share sheet sends. */
    fun shareText(label: String, packageName: String): String? = webUrl(packageName)?.let { "${label.trim()} $it".trim() }
}
