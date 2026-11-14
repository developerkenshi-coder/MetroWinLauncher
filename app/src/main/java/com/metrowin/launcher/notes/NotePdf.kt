package com.metrowin.launcher.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import androidx.core.graphics.withSave
import java.io.OutputStream

/**
 * A note as a PDF of A4 pages: its text (the title first, with its titles, bullets, boxes and bold as they are read) over as many pages as it
 * takes, then its photos and its sketch, if it has one, each on a page of its own.
 */
object NotePdf {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val MARGIN = 40

    /** The note as the styled text of the pages: no `# ` or `**` left in it. */
    fun styled(note: Note): CharSequence {
        val out = SpannableStringBuilder()
        fun add(piece: String, vararg spans: Any) {
            val start = out.length
            out.append(piece)
            spans.forEach { out.setSpan(it, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        if (note.title.isNotBlank()) add(note.title + "\n\n", StyleSpan(Typeface.BOLD), AbsoluteSizeSpan(20))
        val lines = NoteText.parse(note.text)
        lines.forEachIndexed { i, line ->
            val start = out.length
            add(
                when (line.kind) {
                    NoteText.Kind.Bullet -> "•  "
                    NoteText.Kind.Numbered -> "${line.number}.  "
                    NoteText.Kind.Unchecked -> "☐  "
                    NoteText.Kind.Checked -> "☑  "
                    else -> ""
                },
            )
            val heading = line.kind == NoteText.Kind.Title || line.kind == NoteText.Kind.Subtitle
            NoteText.spans(line.body).forEach { (piece, bold) -> if (bold || heading) add(piece, StyleSpan(Typeface.BOLD)) else add(piece) }
            when (line.kind) {
                NoteText.Kind.Title -> out.setSpan(AbsoluteSizeSpan(17), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                NoteText.Kind.Subtitle -> out.setSpan(AbsoluteSizeSpan(14), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else -> {}
            }
            if (i < lines.lastIndex) out.append('\n')
        }
        return out
    }

    fun write(note: Note, sketchPng: ByteArray?, out: OutputStream, photos: List<Bitmap> = emptyList()) {
        val doc = PdfDocument()
        val paint = TextPaint().apply { isAntiAlias = true; textSize = 12f }
        val text = styled(note).ifEmpty { " " }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, WIDTH - 2 * MARGIN).build()
        var line = 0
        var number = 1
        while (line < layout.lineCount) {
            val top = layout.getLineTop(line)
            var end = line + 1
            // As many whole lines as fit under the top margin; one line always goes, so a page is never empty.
            while (end < layout.lineCount && layout.getLineBottom(end) - top <= HEIGHT - 2 * MARGIN) end++
            val page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number++).create())
            page.canvas.withSave {
                translate(MARGIN.toFloat(), MARGIN - top.toFloat())
                clipRect(0f, top.toFloat(), (WIDTH - 2 * MARGIN).toFloat(), layout.getLineBottom(end - 1).toFloat())
                layout.draw(this)
            }
            doc.finishPage(page)
            line = end
        }
        photos.forEach { bitmap ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number++).create())
            val room = minOf((WIDTH - 2 * MARGIN) / bitmap.width.toFloat(), (HEIGHT - 2 * MARGIN) / bitmap.height.toFloat())
            page.canvas.drawBitmap(bitmap, null, RectF(MARGIN.toFloat(), MARGIN.toFloat(), MARGIN + bitmap.width * room, MARGIN + bitmap.height * room), null)
            doc.finishPage(page)
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
