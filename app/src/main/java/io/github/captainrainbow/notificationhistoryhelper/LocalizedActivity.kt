package io.github.captainrainbow.notificationhistoryhelper

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatActivity

abstract class LocalizedActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        AppAppearance.prepare(newBase)
        AppLanguage.prepare(newBase)
        // LocaleManager applies asynchronously. A simultaneous night-mode recreation must
        // inflate XML using the selected language, not the previous resource configuration.
        // Override only locales: keep density, font scale and day/night under framework control.
        val localeOverride = Configuration().apply {
            setLocales(LocaleList.forLanguageTags(AppLanguage.desiredTag(newBase)))
        }
        super.attachBaseContext(newBase.createConfigurationContext(localeOverride))
    }

    override fun onResume() {
        super.onResume()
        AppLanguage.synchronize(this)
    }
}
