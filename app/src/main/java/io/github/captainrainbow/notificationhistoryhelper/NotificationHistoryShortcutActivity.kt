package io.github.captainrainbow.notificationhistoryhelper

import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** A fixed-purpose launcher entry. Caller data/extras never choose the destination. */
class NotificationHistoryShortcutActivity : LocalizedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Re-resolve on every tap: system updates can change or remove the settings page.
        if (!AndroidNotificationHistoryGateway(this).open()) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java))
        }
        finish()
    }
}
