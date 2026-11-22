package com.metrowin.sampletile

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.metrowin.launcher.tilesdk.MetroTile
import com.metrowin.launcher.tilesdk.MetroTileResult

/**
 * Three fields and two buttons: what MetroTile.publish takes, and what it answers. Pin this app on the Start screen of MetroWin Launcher, press
 * "Publish", and the tile turns to show the title, the text and the number. The calls are made off the main thread, as the SDK asks.
 */
class SampleActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val titleField = EditText(this).apply { hint = "Title (60 characters)"; setText("Inbox") }
        val textField = EditText(this).apply { hint = "Text (160 characters)"; setText("3 new messages") }
        val countField = EditText(this).apply { hint = "Number (0 to 99999)"; inputType = InputType.TYPE_CLASS_NUMBER; setText("3") }
        status = TextView(this).apply {
            this.text = if (MetroTile.isInstalled(this@SampleActivity)) "MetroWin Launcher found, tile contract ${MetroTile.version(this@SampleActivity) ?: "?"}" else "MetroWin Launcher is not installed"
        }
        val publish = Button(this).apply {
            this.text = "Publish"
            setOnClickListener { run { MetroTile.publish(this@SampleActivity, titleField.text.toString(), textField.text.toString(), countField.text.toString().toIntOrNull() ?: 0) } }
        }
        val clear = Button(this).apply {
            this.text = "Clear"
            setOnClickListener { run { MetroTile.clear(this@SampleActivity) } }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            listOf(titleField, textField, countField, publish, clear, status).forEach(::addView)
        })
    }

    private fun run(call: () -> MetroTileResult) {
        Thread {
            val answer = when (val result = call()) {
                MetroTileResult.Accepted -> "Accepted"
                MetroTileResult.NotInstalled -> "MetroWin Launcher is not installed"
                is MetroTileResult.Refused -> "Refused: ${result.reason}"
            }
            runOnUiThread { status.text = answer }
        }.start()
    }
}
