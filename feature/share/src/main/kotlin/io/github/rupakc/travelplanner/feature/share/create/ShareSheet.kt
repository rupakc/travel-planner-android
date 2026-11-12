package io.github.rupakc.travelplanner.feature.share.create

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * The system share sheet for a share link (ruling R5): the card PNG (through [ShareCardFileProvider], read-only grant) plus
 * the link as text; the link alone when there is no card. Instagram Stories and TikTok Share Kit are out (they need developer apps).
 */
internal object ShareSheet {
    /** Must match the provider in this module's manifest. */
    fun authority(context: Context): String = "${context.packageName}.share.cards"

    /** "Caption\nlink": the caption is the localised "My trip plan on Voyager" line, the link comes from the server. */
    fun text(caption: String, url: String): String = "$caption\n$url"

    /** The chooser intent; [card] must live in the cache folder the provider exposes. */
    fun intent(context: Context, text: String, card: File?, chooserTitle: String): Intent {
        val send = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
        if (card == null) {
            send.setType("text/plain")
        } else {
            val uri = FileProvider.getUriForFile(context, authority(context), card)
            send.setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("", uri)
        }
        return Intent.createChooser(send, chooserTitle).apply {
            if (card != null) {
                clipData = send.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /** Opens the sheet; false when no app can handle it or the card is outside the provider's folder. */
    fun start(context: Context, text: String, card: File?, chooserTitle: String): Boolean =
        try {
            context.startActivity(intent(context, text, card, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
}
