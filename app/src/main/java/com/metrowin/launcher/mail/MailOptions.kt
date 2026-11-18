package com.metrowin.launcher.mail

import androidx.compose.foundation.layout.PaddingValues
import com.metrowin.launcher.ui.mini.MiniFrame
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.metrowin.launcher.R
import com.metrowin.launcher.data.Prefs
import com.metrowin.launcher.ui.components.MetroButton
import com.metrowin.launcher.ui.components.MetroText
import com.metrowin.launcher.ui.components.MetroTextField
import com.metrowin.launcher.ui.components.MetroToggle
import com.metrowin.launcher.ui.components.centeredContent
import com.metrowin.launcher.ui.components.topBarsPadding
import com.metrowin.launcher.ui.pages.PageNote
import com.metrowin.launcher.ui.theme.LocalMetroColors
import com.metrowin.launcher.ui.theme.pageBackground

/**
 * What the user can switch in Mail (the "options" item of the "…" menu). Each one is a thing that was not there before or that changes how
 * Mail looks or acts; what touches privacy, the network, the battery or a permission starts off. Errors, lost data, safety, accessibility and
 * speed are fixed for everybody and have no switch.
 */
enum class MailOption(val key: String, val default: Boolean, @StringRes val label: Int, @StringRes val hint: Int) {
    Rows("rows", true, R.string.mail_opt_rows, R.string.mail_opt_rows_hint),
    Compact("compact", false, R.string.mail_opt_compact, R.string.mail_opt_compact_hint),
    BigText("big_text", false, R.string.mail_opt_big_text, R.string.mail_opt_big_text_hint),
    DarkPages("dark_pages", true, R.string.mail_opt_dark_pages, R.string.mail_opt_dark_pages_hint),
    Transitions("transitions", true, R.string.mail_opt_transitions, R.string.mail_opt_transitions_hint),
    Move("move", true, R.string.mail_opt_move, R.string.mail_opt_move_hint),
    TwoPanes("two_panes", true, R.string.mail_opt_two_panes, R.string.mail_opt_two_panes_hint),
    Contacts("contacts", false, R.string.mail_opt_contacts, R.string.mail_opt_contacts_hint),
    NotifActions("notif_actions", false, R.string.mail_opt_notif_actions, R.string.mail_opt_notif_actions_hint),
    Swipe("swipe", true, R.string.mail_opt_swipe, R.string.mail_opt_swipe_hint),
    Autoconfig("autoconfig", false, R.string.mail_opt_autoconfig, R.string.mail_opt_autoconfig_hint),
}

/** The choices of Mail, in a preference file of its own. */
object MailOptions {
    private const val FILE = "metrowin_mail_options"
    private const val SIGNATURE = "signature"

    private fun prefs(context: Context) = Prefs.open(context.applicationContext, FILE)

    fun on(context: Context, option: MailOption): Boolean = prefs(context).getBoolean(option.key, option.default)

    fun set(context: Context, option: MailOption, on: Boolean) = prefs(context).edit { putBoolean(option.key, on) }

    /** The lines added at the end of a new message ("" for none). */
    fun signature(context: Context): String = prefs(context).getString(SIGNATURE, "").orEmpty()

    fun setSignature(context: Context, text: String) = prefs(context).edit { putString(SIGNATURE, text) }
}

/** The page of the choices: a switch for each [MailOption] and the signature. */
@Composable
fun MailOptionsView(onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    val values = remember { mutableStateOf(MailOption.entries.associateWith { MailOptions.on(context, it) }) }
    var signature by remember { mutableStateOf(MailOptions.signature(context)) }
    MiniFrame(contentPadding = PaddingValues(horizontal = 24.dp)) {
        MetroText(stringResource(R.string.mail_opt_title), size = 34.sp, weight = FontWeight.Light, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MailOption.entries.forEach { option ->
                MetroToggle(
                    stringResource(option.label), values.value.getValue(option),
                    { on ->
                        MailOptions.set(context, option, on)
                        values.value = values.value + (option to on)
                        onChanged()
                    },
                    Modifier.testTag("mail:opt:${option.key}"),
                )
                PageNote(stringResource(option.hint), Modifier.padding(bottom = 8.dp))
            }
            MetroText(stringResource(R.string.mail_opt_signature), size = 18.sp, color = LocalMetroColors.current.subtle, modifier = Modifier.padding(top = 8.dp))
            MetroTextField(signature, { signature = it; MailOptions.setSignature(context, it) }, stringResource(R.string.mail_opt_signature_hint), Modifier.testTag("mail:opt:signature"), singleLine = false)
        }
        MetroButton(stringResource(R.string.mail_back), Modifier.fillMaxWidth().padding(vertical = 12.dp), onClick = onBack)
    }
}
