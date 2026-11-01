package com.sparktube.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.sparktube.app.R

object HelpLine {

    const val EMAIL = "8bit.softworks@gmail.com"

    fun openEmail(context: Context) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$EMAIL")
        }
        val launched = runCatching { context.startActivity(intent) }.isSuccess
        if (!launched) {
            Toast.makeText(context, R.string.help_line_no_mail, Toast.LENGTH_SHORT).show()
        }
    }
}
