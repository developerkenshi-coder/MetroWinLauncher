package com.metrowin.launcher.ui.settings

import androidx.annotation.StringRes
import com.metrowin.launcher.R
import com.metrowin.launcher.calculator.CalcOption
import com.metrowin.launcher.mail.MailOption

/** What each switch of the mini apps is for, one text each (see [MiniOptions]). Mail and Calculator reuse the hint their own menus show. */
internal object MiniInfoTable {
    private val own: Map<String, Int> = mapOf(
        "mini.calendar.week_grid" to R.string.infom_cal_week_grid,
        "mini.calendar.week_numbers" to R.string.infom_cal_week_numbers,
        "mini.calendar.row_details" to R.string.infom_cal_row_details,
        "mini.calendar.now_label" to R.string.infom_cal_now_label,
        "mini.calendar.tap_to_create" to R.string.infom_cal_tap_to_create,
        "mini.calendar.year_view" to R.string.infom_cal_year_view,
        "mini.calendar.split_wide" to R.string.infom_cal_split_wide,
        "mini.calendar.attendees" to R.string.infom_cal_attendees,
        "mini.calendar.open_day" to R.string.infom_cal_open_day,
        "mini.calendar.repeat_new" to R.string.infom_cal_repeat_new,
        "mini.calendar.swipe_days" to R.string.infom_cal_swipe_days,
        "mini.calendar.hide_declined" to R.string.infom_cal_hide_declined,
        "mini.calendar.birthdays" to R.string.infom_cal_birthdays,
        "mini.calendar.drag_events" to R.string.infom_cal_drag_events,
        "mini.calendar.heatmap" to R.string.infom_cal_heatmap,
        "mini.calendar.by_calendar" to R.string.infom_cal_by_calendar,
        "mini.calendar.large_text" to R.string.infom_cal_large_text,
        "mini.calendar.dim_colors" to R.string.infom_cal_dim_colors,
        "mini.datasense.chart" to R.string.infom_ds_chart,
        "mini.datasense.icons" to R.string.infom_ds_icons,
        "mini.datasense.accent" to R.string.infom_ds_accent,
        "mini.datasense.compact" to R.string.infom_ds_compact,
        "mini.datasense.wide" to R.string.infom_ds_wide,
        "mini.datasense.trends" to R.string.infom_ds_trends,
        "mini.datasense.tools" to R.string.infom_ds_tools,
        "mini.datasense.cleanup" to R.string.infom_ds_cleanup,
        "mini.datasense.hide_launcher" to R.string.infom_ds_hide_launcher,
        "mini.datasense.live" to R.string.infom_ds_live,
        "mini.datasense.history" to R.string.infom_ds_history,
        "mini.datasense.big_files" to R.string.infom_ds_big_files,
        "mini.datasense.plan_cost" to R.string.infom_ds_plan_cost,
        "mini.datasense.extra_charts" to R.string.infom_ds_extra_charts,
        "mini.datasense.overview" to R.string.infom_ds_overview,
        "mini.messaging.rich_bubbles" to R.string.infom_msg_rich_bubbles,
        "mini.messaging.avatars" to R.string.infom_msg_avatars,
        "mini.messaging.live_list" to R.string.infom_msg_live_list,
        "mini.messaging.two_panes" to R.string.infom_msg_two_panes,
        "mini.messaging.quick_replies" to R.string.infom_msg_quick_replies,
        "mini.messaging.delivery_reports" to R.string.infom_msg_delivery_reports,
        "mini.messaging.otp_copy" to R.string.infom_msg_otp_copy,
        "mini.messaging.spam_block" to R.string.infom_msg_spam_block,
        "mini.messaging.lock_text" to R.string.infom_msg_lock_text,
        "mini.voicerecorder.pause" to R.string.infom_rec_pause,
        "mini.voicerecorder.marks" to R.string.infom_rec_marks,
        "mini.voicerecorder.focus" to R.string.infom_rec_focus,
        "mini.voicerecorder.safe_record" to R.string.infom_rec_safe_record,
        "mini.voicerecorder.level_meter" to R.string.infom_rec_level_meter,
        "mini.voicerecorder.speed" to R.string.infom_rec_speed,
        "mini.voicerecorder.seek_buttons" to R.string.infom_rec_seek_buttons,
        "mini.voicerecorder.fine_trim" to R.string.infom_rec_fine_trim,
        "mini.voicerecorder.cut_any" to R.string.infom_rec_cut_any,
        "mini.voicerecorder.audio_tools" to R.string.infom_rec_audio_tools,
        "mini.voicerecorder.spectrogram" to R.string.infom_rec_spectrogram,
        "mini.voicerecorder.keep_transcripts" to R.string.infom_rec_keep_transcripts,
        "mini.voicerecorder.summary" to R.string.infom_rec_summary,
        "mini.voicerecorder.labels" to R.string.infom_rec_labels,
        "mini.voicerecorder.backup" to R.string.infom_rec_backup,
        "mini.voicerecorder.round_button" to R.string.infom_rec_round_button,
        "mini.voicerecorder.rich_rows" to R.string.infom_rec_rich_rows,
        "mini.voicerecorder.pretty_names" to R.string.infom_rec_pretty_names,
        "mini.voicerecorder.group_days" to R.string.infom_rec_group_days,
        "mini.voicerecorder.transitions" to R.string.infom_rec_transitions,
        "mini.voicerecorder.mini_player" to R.string.infom_rec_mini_player,
        "mini.voicerecorder.wave_theme" to R.string.infom_rec_wave_theme,
        "mini.voicerecorder.two_pane" to R.string.infom_rec_two_pane,
        "mini.voicerecorder.high_contrast" to R.string.infom_rec_high_contrast,
        "mini.voicerecorder.start_on_open" to R.string.infom_rec_start_on_open,
        "mini.voicerecorder.volume_mark" to R.string.infom_rec_volume_mark,
    )

    private val hints: Map<String, Int> =
        MailOption.entries.associate { "mini.mail.${it.key}" to it.hint } + CalcOption.entries.associate { "mini.calculator.${it.key.removePrefix("opt_")}" to it.hint }

    @StringRes
    fun of(id: String): Int? = own[id] ?: hints[id]
}
