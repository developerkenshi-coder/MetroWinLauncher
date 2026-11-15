package com.metrowin.launcher.calculator

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrowin.launcher.R
import com.metrowin.launcher.data.CalcEngine
import com.metrowin.launcher.data.ConvertTable
import com.metrowin.launcher.data.CurrencyRepository
import com.metrowin.launcher.data.Currencies
import com.metrowin.launcher.data.CalcLimits
import com.metrowin.launcher.ui.anim.metroPress
import com.metrowin.launcher.ui.components.AppBar
import com.metrowin.launcher.ui.components.AppBarItem
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.components.DialogFrame
import com.metrowin.launcher.ui.theme.LocalAccent
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.LocalReduceMotion
import com.metrowin.launcher.ui.theme.litFill
import com.metrowin.launcher.ui.theme.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.MathContext
import java.util.Currency
import java.util.Locale

/** The converter of units and money. The rates are asked for only when the money is looked at, and only if the ones kept are old. */
@Composable
internal fun ConverterPage(options: CalcOptions, menu: List<AppBarItem>) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val look = options[CalcOption.Look]
    val shortcuts = options[CalcOption.Shortcuts]
    val kinds = remember { ConvertTable.Kind.entries }
    // The state stays when the page is left and when the screen turns; the money is the position after the last kind.
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("1") }
    var fromSymbol by rememberSaveable { mutableStateOf("") }
    var toSymbol by rememberSaveable { mutableStateOf("") }
    var picking by remember { mutableIntStateOf(0) }
    var swaps by remember { mutableIntStateOf(0) }
    val kind = kinds.getOrNull(kindIndex)
    val repository = remember { CurrencyRepository(context) }
    var rates by remember { mutableStateOf(repository.cached()) }
    LaunchedEffect(kind == null) {
        if (kind == null) {
            val now = System.currentTimeMillis()
            val kept = rates
            if (kept == null || now - kept.fetchedAt > Currencies.MAX_AGE_MS) withContext(Dispatchers.IO) { repository.fetch(now) }?.let { rates = it }
        }
    }
    val kindName = kind?.name ?: "Currency"
    val base: List<String> = kind?.let { k -> ConvertTable.units.getValue(k).map { it.symbol } } ?: rates?.let { (it.perEuro.keys + "EUR").sorted() }.orEmpty()
    fun favorite(symbol: String) = "$kindName:$symbol" in options.favorites
    val symbols = base.sortedBy { if (favorite(it)) 0 else 1 }
    val from = symbols.firstOrNull { it == fromSymbol } ?: symbols.getOrNull(0)
    val to = symbols.firstOrNull { it == toSymbol } ?: symbols.getOrNull(1)
    val amount = ConvertTable.parseAmount(input)

    fun convert(a: String?, b: String?): Double? {
        if (a == null || b == null || amount == null) return null
        return if (kind != null) {
            val units = ConvertTable.bySymbol.getValue(kind)
            ConvertTable.convert(amount, units[a] ?: return null, units[b] ?: return null)
        } else {
            rates?.let { Currencies.convert(Currencies.Query(amount, a, b), it) }
        }
    }

    fun format(value: Double?): String =
        value?.let { CalcEngine.format(BigDecimal(it).round(MathContext(10)).toDouble(), locale) } ?: "—"

    fun chooseKind(index: Int) { kindIndex = index; fromSymbol = ""; toSymbol = "" }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                items(kinds) { k -> Chip(kindName(k), kind == k, "calc:kind:${k.name}", look = look) { chooseKind(kinds.indexOf(k)) } }
                item { Chip(stringResource(R.string.calc_kind_currency), kind == null, "calc:kind:Currency", look = look) { chooseKind(kinds.size) } }
            }
            if (kind == null && rates == null) PageNote(stringResource(R.string.calc_rates_missing), Modifier.padding(bottom = 8.dp).testTag("calc:rates:missing"))
            MetroTextField(input, { input = ConvertTable.cleanAmount(it) }, stringResource(R.string.calc_amount), Modifier.testTag("calc:amount"), keyboardType = KeyboardType.Decimal)
            if (input.isNotEmpty() && amount == null) PageNote(stringResource(R.string.calc_amount_invalid), Modifier.padding(top = 4.dp).testTag("calc:amount:invalid"))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Chip(from?.let { unitLabel(it) } ?: "—", true, "calc:from", Modifier.weight(1f), look = look) { picking = 1 }
                Chip("⇄", false, "calc:swap", rotation = swaps * 180f, look = look, description = stringResource(R.string.calc_key_swap)) {
                    val t = from; fromSymbol = to.orEmpty(); toSymbol = t.orEmpty(); swaps++
                }
                Chip(to?.let { unitLabel(it) } ?: "—", true, "calc:to", Modifier.weight(1f), look = look) { picking = 2 }
            }
            MetroText(
                format(convert(from, to)) + " " + (to?.let { unitLabel(it) } ?: ""), size = 40.sp, maxLines = 2,
                color = if (look) LocalAccent.current else LocalMetroColors.current.foreground,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).testTag("calc:result"),
            )
            if (kind == null) rates?.let {
                PageNote(stringResource(R.string.calc_rates_date, it.date), Modifier.padding(bottom = 6.dp))
                if (System.currentTimeMillis() - it.fetchedAt > CalcLimits.RATES_STALE_MS) PageNote(stringResource(R.string.calc_rates_old), Modifier.padding(bottom = 6.dp).testTag("calc:rates:old"))
            }
            LazyColumn(Modifier.weight(1f).testTag("calc:all")) {
                items(symbols) { symbol ->
                    Row(Modifier.fillMaxWidth().metroPress(onClick = { toSymbol = symbol }).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        MetroText(
                            (if (favorite(symbol)) "★ " else "") + unitLabel(symbol) + if (look && kind == null) currencySymbol(symbol, locale) else "",
                            size = 18.sp, weight = FontWeight.Normal, color = LocalMetroColors.current.subtle, modifier = Modifier.weight(0.4f),
                        )
                        MetroText(format(convert(from, symbol)), size = 22.sp, maxLines = 1, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                }
            }
        }
        AppBar(emptyList(), menu = menu)
    }
    if (picking != 0) {
        val current = if (picking == 1) from else to
        var query by remember { mutableStateOf("") }
        DialogFrame(onCancel = { picking = 0 }) {
            MetroTextField(query, { query = it }, stringResource(R.string.calc_pick_search), Modifier.testTag("calc:pick:search"))
            if (shortcuts) PageNote(stringResource(R.string.calc_pick_favorite_hint), Modifier.padding(vertical = 4.dp))
            val shown = symbols.filter { query.isBlank() || it.contains(query.trim(), ignoreCase = true) || unitLabel(it).contains(query.trim(), ignoreCase = true) }
            // As high as the list needs, and no more than half of the screen.
            LazyColumn(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.5f).dp)) {
                items(shown) { symbol ->
                    MetroText(
                        (if (favorite(symbol)) "★ " else "") + unitLabel(symbol), size = 22.sp, weight = if (symbol == current) FontWeight.SemiBold else FontWeight.Light,
                        color = if (symbol == current) LocalAccent.current else LocalMetroColors.current.foreground,
                        modifier = Modifier.fillMaxWidth().metroPress(
                            onClick = { if (picking == 1) fromSymbol = symbol else toSymbol = symbol; picking = 0 },
                            onLongClick = if (shortcuts) ({ options.toggleFavorite("$kindName:$symbol") }) else null,
                        ).padding(vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** The sign of a currency after its code ("  $"), or nothing when the phone knows no other sign than the code. */
private fun currencySymbol(code: String, locale: Locale): String =
    runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrNull()?.takeIf { it != code }?.let { "  $it" }.orEmpty()

/** A chip of the converter. With the look on the chosen one is also heavier, and [rotation] turns the sign of the swap key. */
@Composable
private fun Chip(
    text: String, selected: Boolean, tag: String, modifier: Modifier = Modifier, look: Boolean, rotation: Float = 0f, description: String? = null, onClick: () -> Unit,
) {
    val turn by animateFloatAsState(rotation, tween(if (LocalReduceMotion.current) 0 else 260), label = "swap")
    Box(
        modifier.testTag(tag).metroPress(onClick = onClick).litFill(selected).padding(horizontal = 14.dp, vertical = 8.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        MetroText(
            text, size = 16.sp, weight = if (selected && look) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) onAccent() else LocalMetroColors.current.foreground, modifier = Modifier.graphicsLayer { rotationZ = turn },
        )
    }
}

@Composable
private fun kindName(kind: ConvertTable.Kind): String = stringResource(
    when (kind) {
        ConvertTable.Kind.Length -> R.string.calc_kind_length
        ConvertTable.Kind.Area -> R.string.calc_kind_area
        ConvertTable.Kind.Volume -> R.string.calc_kind_volume
        ConvertTable.Kind.Mass -> R.string.calc_kind_mass
        ConvertTable.Kind.Temperature -> R.string.calc_kind_temperature
        ConvertTable.Kind.Speed -> R.string.calc_kind_speed
        ConvertTable.Kind.Time -> R.string.calc_kind_time
        ConvertTable.Kind.Data -> R.string.calc_kind_data
        ConvertTable.Kind.Energy -> R.string.calc_kind_energy
        ConvertTable.Kind.Pressure -> R.string.calc_kind_pressure
        ConvertTable.Kind.Angle -> R.string.calc_kind_angle
    },
)

/** The symbol of a unit as the language writes it: the few that differ ("tbsp", "fl oz", "nmi"), the rest are the same everywhere. */
@Composable
private fun unitLabel(symbol: String): String = when (symbol) {
    "tsp" -> stringResource(R.string.calc_unit_tsp)
    "tbsp" -> stringResource(R.string.calc_unit_tbsp)
    "fl oz" -> stringResource(R.string.calc_unit_floz)
    "cup" -> stringResource(R.string.calc_unit_cup)
    "nmi" -> stringResource(R.string.calc_unit_nmi)
    "kn" -> stringResource(R.string.calc_unit_kn)
    "wk" -> stringResource(R.string.calc_unit_wk)
    "yr" -> stringResource(R.string.calc_unit_yr)
    "d" -> stringResource(R.string.calc_unit_d)
    else -> symbol
}
