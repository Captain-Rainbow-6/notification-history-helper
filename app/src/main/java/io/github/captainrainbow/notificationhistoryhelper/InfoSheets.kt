package io.github.captainrainbow.notificationhistoryhelper

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.captainrainbow.notificationhistoryhelper.databinding.DialogInfoBinding

internal enum class HelpTopic { HISTORY, SHORTCUT, EXISTING_SHORTCUT, PERMISSION, SETTINGS, ABOUT, LICENSES }

internal class InfoSheets(
    private val activity: MainActivity,
    private val openSettings: () -> Unit,
    private val addAgain: () -> Unit,
) {
    private var dialog: BottomSheetDialog? = null

    fun show(topic: HelpTopic) {
        dismiss()
        val content = DialogInfoBinding.inflate(activity.layoutInflater)
        val sheet = BottomSheetDialog(activity)
        dialog = sheet
        content.closeSheetButton.setOnClickListener { sheet.dismiss() }
        when (topic) {
            HelpTopic.HISTORY -> {
                content.sheetTitle.setText(R.string.open_history)
                content.sheetBody.setText(R.string.history_help)
            }
            HelpTopic.SHORTCUT, HelpTopic.PERMISSION -> {
                content.sheetTitle.setText(if (topic == HelpTopic.SHORTCUT) R.string.add_shortcut else R.string.open_app_settings)
                content.sheetBody.setText(if (topic == HelpTopic.SHORTCUT) R.string.add_help else R.string.shortcut_help)
                content.sheetAction.visibility = View.VISIBLE
                content.sheetAction.setText(R.string.go_settings)
                content.sheetAction.setOnClickListener { sheet.dismiss(); openSettings() }
                if (topic == HelpTopic.SHORTCUT) {
                    content.sheetSecondaryAction.visibility = View.VISIBLE
                    content.sheetSecondaryAction.setText(R.string.shortcut_retry)
                    content.sheetSecondaryAction.setOnClickListener { sheet.dismiss(); addAgain() }
                }
            }
            HelpTopic.EXISTING_SHORTCUT -> {
                content.sheetTitle.setText(R.string.shortcut_added_status)
                content.sheetBody.setText(R.string.shortcut_existing_help)
                content.sheetSecondaryAction.visibility = View.VISIBLE
                content.sheetSecondaryAction.setText(R.string.shortcut_retry)
                content.sheetSecondaryAction.setOnClickListener { sheet.dismiss(); addAgain() }
            }
            HelpTopic.SETTINGS -> {
                content.sheetTitle.setText(R.string.settings)
                content.sheetBody.setText(R.string.language_note)
                content.languageLabel.visibility = View.VISIBLE
                content.languageGroup.visibility = View.VISIBLE
                content.languageGroup.check(when (AppLanguage.mode(activity)) {
                    LanguageMode.SYSTEM -> R.id.systemLanguage
                    LanguageMode.CHINESE -> R.id.chineseLanguage
                    LanguageMode.ENGLISH -> R.id.englishLanguage
                })
                content.languageGroup.setOnCheckedChangeListener { _, checked ->
                    val choice = when (checked) {
                        R.id.systemLanguage -> LanguageMode.SYSTEM
                        R.id.chineseLanguage -> LanguageMode.CHINESE
                        R.id.englishLanguage -> LanguageMode.ENGLISH
                        else -> return@setOnCheckedChangeListener
                    }
                    sheet.dismiss()
                    AppLanguage.select(activity, choice)
                }
                content.themeLabel.visibility = View.VISIBLE
                content.themeGroup.visibility = View.VISIBLE
                content.themeGroup.check(when (AppAppearance.mode(activity)) {
                    ThemeMode.SYSTEM -> R.id.systemTheme
                    ThemeMode.LIGHT -> R.id.lightTheme
                    ThemeMode.DARK -> R.id.darkTheme
                })
                content.themeGroup.setOnCheckedChangeListener { _, checked ->
                    val choice = when (checked) {
                        R.id.systemTheme -> ThemeMode.SYSTEM
                        R.id.lightTheme -> ThemeMode.LIGHT
                        R.id.darkTheme -> ThemeMode.DARK
                        else -> return@setOnCheckedChangeListener
                    }
                    sheet.dismiss()
                    AppAppearance.select(activity, choice)
                }
            }
            HelpTopic.ABOUT -> {
                content.sheetTitle.setText(R.string.about)
                val version = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "—"
                content.sheetBody.text = activity.getString(R.string.about_body, version)
                content.sheetAction.visibility = View.VISIBLE
                content.sheetAction.setText(R.string.licenses)
                content.sheetAction.setOnClickListener { show(HelpTopic.LICENSES) }
            }
            HelpTopic.LICENSES -> {
                content.sheetTitle.setText(R.string.licenses)
                content.sheetBody.text = activity.getString(R.string.license_intro,
                    activity.assets.open("material_icons_license.txt").bufferedReader().use { it.readText() })
            }
        }
        sheet.setContentView(content.root)
        val originalBottom = content.root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content.root) { view, insets ->
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight,
                originalBottom + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
        sheet.behavior.maxHeight = (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()
        sheet.behavior.skipCollapsed = true
        sheet.setOnShowListener { sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        sheet.setOnDismissListener { if (dialog === sheet) dialog = null }
        sheet.show()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
