package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppItem

/** What a spoken phrase asks the launcher to do. */
sealed interface VoiceCommand {
    data class OpenApp(val app: AppItem) : VoiceCommand

    /** A screen of the Android settings, from the catalog of [GlobalSearch]. */
    data class OpenSetting(val setting: SystemSetting) : VoiceCommand

    /** Opens the card of a contact; Android does not let a launcher place a call by itself. */
    data class Call(val name: String) : VoiceCommand

    data class Web(val query: String) : VoiceCommand

    /** [on] is null when the phrase did not say, and the light is simply switched. */
    data class Torch(val on: Boolean?) : VoiceCommand

    /** A timer ("timer 10 minutes") or an alarm ("alarm at 7:30"), set in the clock app. */
    data class Clock(val answer: QuickAnswer) : VoiceCommand

    data object LauncherSettings : VoiceCommand
    data object Weather : VoiceCommand
    data object LockScreen : VoiceCommand
    data object ActionCenter : VoiceCommand
    data object AppList : VoiceCommand
    data object Start : VoiceCommand

    /** The phrase asked to open something that is not on the phone. */
    data class NotFound(val target: String) : VoiceCommand

    /** Nothing was understood. */
    data object Unclear : VoiceCommand
}

/**
 * Turns what the speech recognizer heard into a [VoiceCommand], in Italian and English, with no network and no
 * model: a few verbs and the names of the apps and settings of the phone. Pure, so it is tested with words.
 */
object VoiceCommands {
    private val openVerbs = listOf(
        "vai su", "vai a", "vai in", "apri", "aprimi", "avvia", "avviami", "lancia", "mostra", "mostrami",
        "open", "launch", "start", "run", "show me", "show", "go to",
    )
    private val callVerbs = listOf("telefona a", "telefona", "chiama", "chiamami", "contatta", "call", "phone", "ring", "dial", "contact")
    private val searchVerbs = listOf(
        "cerca su internet", "cerca sul web", "cerca online", "cerca", "cercami", "ricerca",
        "search for", "search", "look up", "look for", "find", "google",
    )
    private val offVerbs = listOf("spegni", "disattiva", "off", "turn off", "switch off")
    private val onVerbs = listOf("accendi", "attiva", "on", "turn on", "switch on")
    private val torchWords = listOf("torcia", "torcetta", "flash", "flashlight", "torch")
    private val weatherWords = listOf("meteo", "weather", "che tempo fa", "previsioni", "forecast")
    private val lockWords = listOf("blocca schermo", "blocca lo schermo", "blocca il telefono", "blocca telefono", "lock screen", "lock the screen", "lock phone", "lock the phone", "lock")
    private val actionCenterWords = listOf("centro notifiche", "centro azioni", "centro di notifica", "action center", "notifications", "notifiche")
    private val appListWords = listOf("tutte le app", "lista app", "lista delle app", "elenco app", "all apps", "app list", "app drawer")
    private val startWords = listOf("home", "schermata start", "schermata home", "start screen", "vai alla home", "go home")
    private val launcherSettingsWords = listOf(
        "impostazioni del launcher", "impostazioni launcher", "impostazioni di metrowin", "launcher settings", "launcher setting", "metrowin settings",
    )

    private val leadingFillers = listOf(
        "ok", "okay", "ehi", "ehi tu", "hey", "per favore", "per piacere", "please", "puoi", "potresti", "puoi per favore",
        "vorrei", "voglio", "can you", "could you", "i want to", "i would like to", "i d like to", "mi", "ti", "tu",
    )
    private val trailingFillers = listOf("per favore", "per piacere", "please", "grazie", "thanks", "thank you", "adesso", "ora", "now")
    private val articles = setOf("il", "lo", "la", "l", "i", "gli", "le", "un", "uno", "una", "the", "a", "an", "to", "al", "alla", "allo", "su", "di", "per", "for", "my")

    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lower case, no accents, no punctuation, single spaces: "L'ora, per favore!" is "l ora per favore". */
    fun normalize(heard: String): String = AppSections.fold(heard).replace(separators, " ").trim()

    private fun words(text: String) = text.split(' ').filter { it.isNotEmpty() }

    /** Whether the whole word sequence [phrase] is in [text] as words (not part of a longer word). */
    private fun hasPhrase(text: String, phrase: String): Boolean = " $text ".contains(" $phrase ")

    private fun hasAny(text: String, phrases: List<String>) = phrases.any { hasPhrase(text, it) }

    /** [text] without the leading phrase [prefix], or null when it does not start with it as whole words. */
    private fun after(text: String, prefix: String): String? = when {
        text == prefix -> ""
        text.startsWith("$prefix ") -> text.removePrefix("$prefix ").trim()
        else -> null
    }

    private fun afterAny(text: String, prefixes: List<String>): String? {
        // The longest verb first, so "search for" wins over "search".
        for (verb in prefixes.sortedByDescending { it.length }) after(text, verb)?.let { return it }
        return null
    }

    private fun stripFillers(text: String): String {
        var result = text
        var changed = true
        while (changed) {
            changed = false
            for (filler in leadingFillers.sortedByDescending { it.length }) {
                after(result, filler)?.let {
                    result = it
                    changed = true
                }
            }
            for (filler in trailingFillers.sortedByDescending { it.length }) {
                if (result == filler) {
                    result = ""
                    changed = true
                } else if (result.endsWith(" $filler")) {
                    result = result.removeSuffix(filler).trim()
                    changed = true
                }
            }
        }
        return result
    }

    private fun stripArticles(text: String): String {
        var list = words(text)
        while (list.size > 1 && list.first() in articles) list = list.drop(1)
        return list.joinToString(" ")
    }

    /**
     * The command in [heard]. [apps] are the apps the list shows and [settingLabel] gives a name to the entries of the
     * Android settings in the language of the phone.
     */
    fun parse(heard: String, apps: List<AppItem>, settingLabel: (SystemSettingDef) -> String): VoiceCommand {
        val text = stripFillers(normalize(heard))
        if (text.isEmpty()) return VoiceCommand.Unclear

        QuickAnswers.timerOrAlarm(text)?.let { return VoiceCommand.Clock(it) }

        afterAny(text, searchVerbs)?.let { rest ->
            val query = stripArticles(rest)
            return if (query.isEmpty()) VoiceCommand.Unclear else VoiceCommand.Web(query)
        }
        afterAny(text, callVerbs)?.let { rest ->
            val name = stripArticles(rest)
            return if (name.isEmpty()) VoiceCommand.Unclear else VoiceCommand.Call(name)
        }

        // The launcher's own commands. A phrase that only names one is also taken, so "meteo" is enough.
        if (hasAny(text, torchWords)) {
            return VoiceCommand.Torch(
                when {
                    hasAny(text, offVerbs) -> false
                    hasAny(text, onVerbs) -> true
                    else -> null
                },
            )
        }
        if (hasAny(text, launcherSettingsWords)) return VoiceCommand.LauncherSettings
        if (hasAny(text, weatherWords)) return VoiceCommand.Weather
        if (hasAny(text, lockWords)) return VoiceCommand.LockScreen
        if (hasAny(text, actionCenterWords)) return VoiceCommand.ActionCenter
        if (hasAny(text, appListWords)) return VoiceCommand.AppList
        if (text in startWords || startWords.any { text.endsWith(" $it") && afterAny(text, openVerbs) != null }) return VoiceCommand.Start

        val opening = afterAny(text, openVerbs)
        val target = stripArticles(opening ?: text)
        if (target.isEmpty()) return VoiceCommand.Unclear

        findApp(apps, target)?.let { return VoiceCommand.OpenApp(it) }
        GlobalSearch.matchSettings(target, labelOf = settingLabel).firstOrNull()?.let { return VoiceCommand.OpenSetting(it) }
        // "open xyz" for something that is not there says so; a bare phrase is taken for a question for the web.
        return if (opening != null) VoiceCommand.NotFound(target) else VoiceCommand.Web(target)
    }

    /**
     * The command to carry out among the recognizer's [alternatives]: the first that is a real command, so that "open
     * maps" heard also as "open mops" still finds the app; failing that, the first one.
     */
    fun best(alternatives: List<String>, apps: List<AppItem>, settingLabel: (SystemSettingDef) -> String): VoiceCommand {
        val commands = alternatives.map { parse(it, apps, settingLabel) }
        return commands.firstOrNull { it !is VoiceCommand.Web && it !is VoiceCommand.NotFound && it !is VoiceCommand.Unclear }
            ?: commands.firstOrNull()
            ?: VoiceCommand.Unclear
    }

    /** The app that [target] names: the exact name first, then the first one the search ranks highest. */
    fun findApp(apps: List<AppItem>, target: String): AppItem? {
        val needle = normalize(target)
        if (needle.isEmpty()) return null
        return apps.firstOrNull { normalize(it.label) == needle }
            ?: AppSearch.filter(apps.filter { !it.pinOnly }, needle).firstOrNull()
    }
}
