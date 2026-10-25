package com.metrowin.launcher.data

import kotlin.random.Random

/** "dado", "2d6+3", "d20", "lancia una moneta", "testa o croce": a throw of dice or a coin. Pure, the dice are passed in. */
object Rolls {
    private val DICE_WORDS = Regex("""^(?:(?:tira|lancia|roll|throw) )?(?:(\d+) )?(?:(?:un|uno|a|the) )?(?:dado|dadi|dice|die)$""")
    private val NOTATION = Regex("""^(\d{0,2})d(\d{1,4})([+-]\d{1,4})?$""")
    private val COIN = Regex("""^(?:(?:tira|lancia|flip|toss) )?(?:(?:una|a|the) )?(?:moneta|coin)$|^(?:testa o croce|croce o testa|heads or tails|tails or heads)$""")

    const val MAX_DICE = 20
    const val MAX_SIDES = 1000
    private const val MAX_BONUS = 999

    /** The throw [text] asks for, or null when it asks for none. */
    fun of(text: String, random: Random): QuickAnswer? {
        val plain = AppSections.fold(text).trim()
        // The notation first, because it has signs the word search takes away: "2d6+3".
        NOTATION.matchEntire(plain.replace(" ", ""))?.let { m ->
            val count = m.groupValues[1].takeIf { it.isNotEmpty() }?.toInt() ?: 1
            val sides = m.groupValues[2].toInt()
            val bonus = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            if (count !in 1..MAX_DICE || sides !in 2..MAX_SIDES || bonus < -MAX_BONUS || bonus > MAX_BONUS) return null
            return dice(count, sides, bonus, random)
        }
        val words = VoiceCommands.normalize(text)
        if (COIN.matches(words)) return QuickAnswer.Coin(random.nextBoolean())
        DICE_WORDS.matchEntire(words)?.let { m ->
            // "2 dadi" / "3 dice": that many six-sided dice.
            val count = m.groupValues[1].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 1
            if (count !in 1..MAX_DICE) return null
            return dice(count, 6, 0, random)
        }
        return null
    }

    private fun dice(count: Int, sides: Int, bonus: Int, random: Random) =
        QuickAnswer.Dice(count, sides, List(count) { random.nextInt(1, sides + 1) }, bonus)
}
