package com.metrowin.launcher.data

import com.metrowin.launcher.model.AppItem

/** What a spoken phrase asks the launcher to do. */
sealed interface VoiceCommand {
    data class OpenApp(val app: AppItem) : VoiceCommand

    /** A screen of the Android settings, from the catalog of [GlobalSearch]. */
    data class OpenSetting(val setting: SystemSetting) : VoiceCommand

    /** Opens the card of a contact; Android does not let a launcher place a call by itself. */
    data class Call(val name: String) : VoiceCommand

    /** A number said in digits ("chiama 333 1234567"): it is dialed as it is, never looked for among the names. */
    data class CallNumber(val number: String) : VoiceCommand

    data class Web(val query: String) : VoiceCommand

    /** [on] is null when the phrase did not say, and the light is simply switched. */
    data class Torch(val on: Boolean?) : VoiceCommand

    /** A timer ("timer 10 minutes") or an alarm ("alarm at 7:30"), set in the clock app. */
    data class Clock(val answer: QuickAnswer) : VoiceCommand

    /** The media volume or the brightness of the screen, up or down a step, to the top or the bottom, or to a percentage. */
    data class Volume(val change: LevelChange) : VoiceCommand
    data class Brightness(val change: LevelChange) : VoiceCommand

    /** Sound, vibration or silence for calls and notifications. */
    data class Ringer(val mode: RingerMode) : VoiceCommand

    /** Directions to a place, in the maps app of the phone. */
    data class Navigate(val destination: String) : VoiceCommand

    /** A text message for a contact, written in the messages app and left there to be sent. [text] may be empty. */
    data class Message(val name: String, val text: String) : VoiceCommand

    /** A system action of the accessibility service: the screenshot, the power menu, split screen, recent apps. */
    data class System(val action: GestureAction) : VoiceCommand

    /** What the search box does with the same sentence ([Assistant]): a reminder, an event, a note, a place near the user, the camera. */
    data class Assist(val answer: QuickAnswer) : VoiceCommand

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

/** How a level (the volume, the brightness) is asked to change. */
sealed interface LevelChange {
    data object Up : LevelChange
    data object Down : LevelChange
    data object Max : LevelChange
    data object Min : LevelChange

    /** To [percent] of the way, 0 to 100. */
    data class To(val percent: Int) : LevelChange

    /** The new level, as a fraction from 0 to 1, when [current] is the level now. */
    fun apply(current: Float): Float = when (this) {
        Up -> (current + STEP).coerceAtMost(1f)
        Down -> (current - STEP).coerceAtLeast(0f)
        Max -> 1f
        Min -> 0f
        is To -> percent.coerceIn(0, 100) / 100f
    }

    companion object {
        /** A step up or down: a fifth of the way. */
        const val STEP = 0.2f
    }
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

    // Fewer digits are more likely a word misheard than a number to call.
    private const val MIN_NUMBER_DIGITS = 5

    private val separators = Regex("[^\\p{L}\\p{N}]+")

    // --- messages

    private val messageVerbs = listOf(
        "scrivi un messaggio", "scrivi un sms", "scrivi", "manda un messaggio", "manda un sms", "manda messaggio", "invia un messaggio",
        "invia un sms", "invia messaggio", "messaggio", "sms",
        "send a message", "send message", "send a text message", "send a text", "send an sms", "write to", "text message",
    ).map { it.split(' ') }.sortedByDescending { it.size }
    private val prepositions = setOf("a", "ad", "al", "alla", "allo", "per", "to")
    private val messageFillerWords = setOf(
        "ok", "okay", "ehi", "hey", "per", "favore", "piacere", "please", "puoi", "potresti", "vorrei", "voglio", "can", "you", "could",
        "mi", "ti", "tu", "i", "want", "to", "would", "like", "d",
    )
    private val messageConnectors = setOf("dicendo", "dicendogli", "dicendole", "saying", "che", "that")

    /** "scrivi a Marco ciao come va", "manda un messaggio a Anna dicendo che arrivo", "text Sam running late". */
    private fun message(heard: String): VoiceCommand.Message? {
        val spoken = normalize(heard).split(' ').filter { it.isNotEmpty() }
        // The words as they were said, when they match one for one (accents and capitals are lost by the folding).
        val original = heard.split(separators).filter { it.isNotEmpty() }.takeIf { it.size == spoken.size } ?: spoken
        var start = 0
        while (start < spoken.size && spoken[start] in messageFillerWords) start++
        val verb = messageVerbs.firstOrNull { v -> spoken.size >= start + v.size && spoken.subList(start, start + v.size) == v } ?: return null
        var from = start + verb.size
        while (from < spoken.size && spoken[from] in prepositions) from++
        if (from >= spoken.size) return null
        val connector = (from + 1 until spoken.size).firstOrNull { spoken[it] in messageConnectors }
        val nameEnd = connector ?: (from + 1)
        val name = spoken.subList(from, nameEnd).joinToString(" ")
        var bodyFrom = if (connector != null) connector + 1 else nameEnd
        // "dicendo che arrivo", "saying that I am late": the second connector is not part of the message.
        while (connector != null && bodyFrom < spoken.size && spoken[bodyFrom] in messageConnectors) bodyFrom++
        val body = original.subList(bodyFrom.coerceAtMost(original.size), original.size).joinToString(" ")
        return VoiceCommand.Message(stripArticles(name), body)
    }

    // --- directions

    private val navigationVerbs = listOf(
        "portami a", "portami in", "portami da", "portami al", "portami alla", "naviga verso", "naviga a", "naviga per", "naviga fino a", "navigatore per", "navigatore verso",
        "indicazioni per", "indicazioni stradali per", "indicazioni per arrivare a", "come arrivo a", "come si arriva a", "come arrivare a", "percorso per",
        "navigate to", "directions to", "take me to", "how do i get to", "how to get to", "drive to", "route to", "navigation to",
    )

    private fun navigation(text: String): VoiceCommand.Navigate? {
        val rest = afterAny(text, navigationVerbs) ?: return null
        val destination = stripArticles(rest)
        return if (destination.isEmpty()) null else VoiceCommand.Navigate(destination)
    }

    // --- volume and brightness

    private val volumeWord = listOf("volume")
    private val brightnessWord = listOf("luminosita", "brightness", "luminosita schermo")
    private val upWords = listOf("alza", "alzare", "aumenta", "aumentare", "su", "up", "piu alto", "piu alta", "higher", "increase", "raise", "louder", "brighter", "piu luminoso", "piu luminosa")
    private val downWords = listOf("abbassa", "abbassare", "diminuisci", "diminuire", "riduci", "giu", "down", "piu basso", "piu bassa", "lower", "decrease", "reduce", "quieter", "dimmer", "piu scuro", "piu scura")
    private val maxWords = listOf("massimo", "massima", "max", "maximum", "full", "al massimo", "tutto")
    private val minWords = listOf("minimo", "minima", "min", "zero", "muto", "mute", "muta", "lowest", "al minimo")

    /** "alza il volume", "volume al 50 per cento", "luminosità al massimo", "turn down the brightness": null when [text] is not about [words]. */
    private fun level(text: String, names: List<String>): LevelChange? {
        if (!hasAny(text, names)) return null
        val percent = words(text).firstNotNullOfOrNull { it.toIntOrNull() }?.takeIf { it in 0..100 }
        return when {
            percent != null -> LevelChange.To(percent)
            hasAny(text, maxWords) -> LevelChange.Max
            hasAny(text, minWords) -> LevelChange.Min
            hasAny(text, upWords) -> LevelChange.Up
            hasAny(text, downWords) -> LevelChange.Down
            else -> null
        }
    }

    // --- the ringer

    private val silentWords = listOf("silenzioso", "silenziosa", "modalita silenziosa", "modalita silenzioso", "silent", "silent mode", "silenzia il telefono", "silenzia telefono", "metti in silenzioso", "metti in silenzio", "silenzia")
    private val vibrateWords = listOf("vibrazione", "vibra", "vibrate", "vibration", "solo vibrazione", "modalita vibrazione")
    private val ringWords = listOf(
        "attiva la suoneria", "riattiva la suoneria", "suoneria attiva", "suoneria on", "modalita normale", "modalita suoneria", "suono normale",
        "turn on the ringer", "ringer on", "sound on", "unmute the phone", "ring mode", "disattiva il silenzioso", "togli il silenzioso", "esci dal silenzioso",
        "disattiva la modalita silenziosa", "turn off silent mode", "turn off vibrate", "disattiva la vibrazione",
    )

    private fun ringer(text: String): VoiceCommand.Ringer? = when {
        hasAny(text, ringWords) -> VoiceCommand.Ringer(RingerMode.Normal)
        hasAny(text, vibrateWords) -> VoiceCommand.Ringer(RingerMode.Vibrate)
        hasAny(text, silentWords) -> VoiceCommand.Ringer(RingerMode.Silent)
        else -> null
    }

    // --- the system actions

    private val systemWords: List<Pair<List<String>, GestureAction>> = listOf(
        listOf("screenshot", "screen shot", "cattura lo schermo", "cattura schermo", "cattura schermata", "fai una schermata", "screen capture", "take a screenshot") to GestureAction.Screenshot,
        listOf("menu di spegnimento", "menu di accensione", "power menu", "spegni il telefono", "spegni telefono", "spegnimento", "power off", "turn off the phone", "shut down", "shutdown") to GestureAction.PowerMenu,
        listOf("schermo diviso", "dividi lo schermo", "dividi schermo", "split screen", "split the screen") to GestureAction.SplitScreen,
        listOf("app recenti", "applicazioni recenti", "recent apps", "recents", "multitasking", "app switcher") to GestureAction.Recents,
        listOf("notifiche di android", "notifiche di sistema", "tendina di android", "android notifications", "system notifications", "notification shade") to GestureAction.AndroidNotifications,
        listOf("impostazioni rapide", "impostazioni rapide di android", "quick settings", "android quick settings") to GestureAction.AndroidQuickSettings,
    )

    private fun system(text: String): VoiceCommand.System? =
        systemWords.firstOrNull { (phrases, _) -> hasAny(text, phrases) }?.let { VoiceCommand.System(it.second) }

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
    fun parse(heard: String, apps: List<AppItem>, settingLabel: (SystemSettingDef) -> String): VoiceCommand =
        parse(heard, apps, settingLabel, java.time.LocalDateTime.now())

    /** The same, with "now" given, which the times in a sentence ("tomorrow at 6") are counted from. */
    fun parse(
        heard: String,
        apps: List<AppItem>,
        settingLabel: (SystemSettingDef) -> String,
        now: java.time.LocalDateTime,
    ): VoiceCommand {
        val text = stripFillers(normalize(heard))
        if (text.isEmpty()) return VoiceCommand.Unclear

        QuickAnswers.timerOrAlarm(text)?.let { return VoiceCommand.Clock(it) }
        // A message keeps its words as they were said, so it is read before the fillers at its end are taken away.
        message(heard)?.let { return it }
        navigation(text)?.let { return it }
        level(text, volumeWord)?.let { return VoiceCommand.Volume(it) }
        level(text, brightnessWord)?.let { return VoiceCommand.Brightness(it) }
        ringer(text)?.let { return it }
        system(text)?.let { return it }

        // "search for…" and "call…" are what they say, whatever the rest of the sentence looks like ("search for best pizza near me").
        afterAny(text, searchVerbs)?.let { rest ->
            val query = stripArticles(rest)
            return if (query.isEmpty()) VoiceCommand.Unclear else VoiceCommand.Web(query)
        }
        afterAny(text, callVerbs)?.let { rest ->
            val name = stripArticles(rest)
            val digits = name.replace(" ", "")
            return when {
                name.isEmpty() -> VoiceCommand.Unclear
                digits.length >= MIN_NUMBER_DIGITS && digits.all { it.isDigit() } -> VoiceCommand.CallNumber(digits)
                else -> VoiceCommand.Call(name)
            }
        }

        // The sentences the search box turns into a reminder, an event, a note, "near me" or the camera. As they were said, with their
        // accents and capitals, when there is no "please" or "hey" in front; otherwise the same without them (the way [text] is written).
        val sentence = if (normalize(heard) == text) heard else text
        Assistant.of(sentence, now).firstOrNull()?.let { return VoiceCommand.Assist(it) }

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
    fun best(alternatives: List<String>, apps: List<AppItem>, settingLabel: (SystemSettingDef) -> String): VoiceCommand =
        best(alternatives, apps, settingLabel, java.time.LocalDateTime.now())

    fun best(
        alternatives: List<String>,
        apps: List<AppItem>,
        settingLabel: (SystemSettingDef) -> String,
        now: java.time.LocalDateTime,
    ): VoiceCommand {
        val commands = alternatives.map { parse(it, apps, settingLabel, now) }
        return commands.firstOrNull { it !is VoiceCommand.Web && it !is VoiceCommand.NotFound && it !is VoiceCommand.Unclear }
            ?: commands.firstOrNull()
            ?: VoiceCommand.Unclear
    }

    /** The names that [alternatives] ask to call or write to, [first] ahead of the others: a contact missed by one hearing may be found by another. */
    fun contactNames(alternatives: List<String>, apps: List<AppItem>, settingLabel: (SystemSettingDef) -> String, first: String): List<String> =
        (listOf(first) + alternatives.mapNotNull {
            when (val command = parse(it, apps, settingLabel)) {
                is VoiceCommand.Call -> command.name
                is VoiceCommand.Message -> command.name
                else -> null
            }
        }).distinct()

    /** The app that [target] names: the exact name first, then the first one the search ranks highest. */
    fun findApp(apps: List<AppItem>, target: String): AppItem? {
        val needle = normalize(target)
        if (needle.isEmpty()) return null
        return apps.firstOrNull { normalize(it.label) == needle }
            ?: AppSearch.filter(apps.filter { !it.pinOnly }, needle).firstOrNull()
    }
}
