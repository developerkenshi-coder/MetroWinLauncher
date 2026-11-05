package com.metrowin.launcher.notes

import android.graphics.BitmapFactory
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream

/** A note as a PDF of A4 pages: its text (the title first) over as many pages as it takes, then its sketch, if it has one, on a page of its own. */
object NotePdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40

    fun write(note: Note, sketchPng: ByteArray?, out: OutputStream) {
        val doc = PdfDocument()
        val paint = TextPaint().apply { isAntiAlias = true; textSize = 12f }
        val text = NoteBackup.plain(note).ifEmpty { " " }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH - 2 * MARGIN).build()
        var line = 0
        var number = 1
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line + 1
            // As many whole lines as fit under the top margin; one line always goes, so a page is never empty.
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= HEIGHT - 2 * MARGIN) end++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number++).create())
            page.canvas.apply {
                save()
                translate(MARGIN.toFloat(), MARGIN - top.toFloat())
                clipRect(0f, top.toFloat(), (WIDTH - 2 * MARGIN).toFloat(), layout.getLineBottom(end - 1).toFloat())
                layout.draw(this)
                restore()
            }
            doc.finishPage(page)
            line = end
        }
        sketchPng?.let { png ->
            BitmapFactory.decodeByteArray(png, 0, png.size)?.let { bitmap ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
                val room = minOf((WIDTH - 2 * MARGIN) / bitmap.width.toFloat(), (HEIGHT - 2 * MARGIN) / bitmap.height.toFloat())
                val w = bitmap.width * room
                val h = bitmap.height * room
                page.canvas.drawBitmap(bitmap, null, RectF(MARGIN.toFloat(), MARGIN.toFloat(), MARGIN + w, MARGIN + h), null)
                doc.finishPage(page)
            }
        }
        try {
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
