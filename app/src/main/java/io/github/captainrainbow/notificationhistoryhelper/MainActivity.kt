package io.github.captainrainbow.notificationhistoryhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import io.github.captainrainbow.notificationhistoryhelper.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : LocalizedActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: NotificationHistoryController
    private lateinit var shortcuts: HistoryShortcutController
    private lateinit var shortcutGateway: AndroidHistoryShortcutGateway
    private lateinit var sheets: InfoSheets
    private val shortcutWorker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastPinOutcome: PinOutcome? = null
    private var checkRevision = 0
    private var resumed = false
    private var requesting = false
    private var awaitingSettingsReturn = false
    private var checkedSettings = false
    private var settingsOpenFailed = false
    private var noticeRevision = 0
    private val pinResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshShortcutStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureInsets()
        sheets = InfoSheets(this, ::openAppSettings) { requestShortcut(allowDuplicate = true) }
        controller = NotificationHistoryController(AndroidNotificationHistoryGateway(this))
        shortcutGateway = AndroidHistoryShortcutGateway(this)
        shortcuts = HistoryShortcutController(AndroidNotificationHistoryGateway(this), shortcutGateway,
            AndroidShortcutPermissionGateway(this))
        // A reopened/recreated home screen starts quietly. Do not restore old notices.

        binding.openHistoryButton.setOnClickListener {
            when (controller.open()) {
                OpenOutcome.OPENED -> Unit
                OpenOutcome.UNAVAILABLE -> {
                    render(SupportState.UNSUPPORTED)
                    Toast.makeText(this, R.string.status_unsupported, Toast.LENGTH_LONG).show()
                }
                OpenOutcome.FAILED -> Toast.makeText(this, R.string.open_failed, Toast.LENGTH_LONG).show()
            }
        }
        binding.addShortcutButton.setOnClickListener { requestShortcut() }
        binding.appSettingsButton.setOnClickListener { openAppSettings() }
        binding.historyHelpButton.setOnClickListener { sheets.show(HelpTopic.HISTORY) }
        binding.shortcutHelpButton.setOnClickListener { sheets.show(HelpTopic.SHORTCUT) }
        binding.permissionHelpButton.setOnClickListener { sheets.show(HelpTopic.PERMISSION) }
        binding.settingsButton.setOnClickListener { sheets.show(HelpTopic.SETTINGS) }
        binding.aboutButton.setOnClickListener { sheets.show(HelpTopic.ABOUT) }
        binding.closeShortcutMessageButton.setOnClickListener {
            clearNotice()
        }
        render(controller.supportState())
    }

    private fun configureInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, pinResultReceiver,
            IntentFilter(ShortcutPinResultReceiver.REFRESH_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (awaitingSettingsReturn) {
            awaitingSettingsReturn = false
            checkedSettings = true
        }
        if (::controller.isInitialized) {
            render(controller.supportState())
            refreshShortcutStatus()
        }
    }

    override fun onPause() {
        resumed = false
        checkRevision++
        super.onPause()
    }

    override fun onStop() {
        unregisterReceiver(pinResultReceiver)
        // Leaving home discards old guidance, including an unconfirmed attempt.
        // Only an explicit settings round-trip is evaluated when the user returns.
        if (!awaitingSettingsReturn) clearNotice()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            clearNotice()
        }
    }

    override fun onDestroy() {
        if (::sheets.isInitialized) sheets.dismiss()
        checkRevision++
        shortcutWorker.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun openAppSettings() {
        clearNotice()
        awaitingSettingsReturn = true
        if (!AndroidAppSettingsGateway(this).open()) {
            awaitingSettingsReturn = false
            settingsOpenFailed = true
            showShortcutMessage(R.string.app_settings_failed)
        }
    }

    private fun requestShortcut(allowDuplicate: Boolean = false) {
        if (requesting) return
        clearNotice()
        val noticeRequest = noticeRevision
        requesting = true
        checkRevision++
        setEnabled(binding.addShortcutButton, false)
        shortcutWorker.execute {
            val outcome = shortcuts.request(allowDuplicate)
            mainHandler.post {
                if (isDestroyed || isFinishing) return@post
                requesting = false
                if (noticeRequest == noticeRevision) lastPinOutcome = outcome
                refreshShortcutStatus()
                if (resumed && noticeRequest == noticeRevision && outcome == PinOutcome.ALREADY_PINNED) {
                    sheets.show(HelpTopic.EXISTING_SHORTCUT)
                }
                // A denied/unconfirmed attempt uses the card; help opens only on request.
            }
        }
    }

    private fun refreshShortcutStatus() {
        if (!resumed || isDestroyed || isFinishing) return
        val revision = ++checkRevision
        setEnabled(binding.addShortcutButton, false)
        // Keep the last visible result while checking; only replace it with a fresh result.
        shortcutWorker.execute {
            val appearanceUpdated = shortcutGateway.refreshPinnedAppearance()
            val state = shortcuts.inspect()
            val permission = shortcuts.permissionState()
            mainHandler.post {
                if (!resumed || isDestroyed || isFinishing || revision != checkRevision) return@post
                // Actual creation evidence is independent from a dismissed/expired UI notice.
                ShortcutCreationFeedback.confirm(this, shortcutGateway.pendingRequestToken,
                    pinned = state == ShortcutState.PINNED)
                lastPinOutcome = ShortcutScreenPresenter.outcomeAfterInspection(state, lastPinOutcome)
                val message = when {
                    settingsOpenFailed -> R.string.app_settings_failed
                    checkedSettings -> ShortcutScreenPresenter.settingsMessage(permission)
                    else -> ShortcutScreenPresenter.message(state, lastPinOutcome, appearanceUpdated, permission)
                }
                val status = when (ShortcutScreenPresenter.buttonStatus(state, lastPinOutcome, permission)) {
                    ShortcutButtonStatus.ADDED -> R.string.shortcut_added_status
                    ShortcutButtonStatus.NOT_ADDED -> R.string.shortcut_not_added_status
                    ShortcutButtonStatus.UNCONFIRMED -> R.string.shortcut_pending_status
                    ShortcutButtonStatus.UNAVAILABLE -> R.string.shortcut_unavailable_status
                    ShortcutButtonStatus.UNKNOWN -> R.string.shortcut_unknown_status
                    ShortcutButtonStatus.PERMISSION_REQUIRED -> R.string.shortcut_permission_required
                }
                setActionStatus(binding.addShortcutButton, R.string.add_shortcut, status)
                setActionStatus(binding.appSettingsButton, R.string.open_app_settings,
                    ShortcutScreenPresenter.permissionStatus(permission))
                showShortcutMessage(message)
                setEnabled(binding.addShortcutButton, !requesting &&
                    state != ShortcutState.HISTORY_UNAVAILABLE && state != ShortcutState.HOME_SCREEN_UNSUPPORTED)
            }
        }
    }

    private fun showShortcutMessage(message: Int?) {
        binding.shortcutStatusText.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let { setNoticeText(binding.shortcutStatusText, it,
            if (checkedSettings || settingsOpenFailed) HelpTopic.PERMISSION else HelpTopic.SHORTCUT) }
        updateNoticeVisibility()
    }

    private fun clearNotice() {
        noticeRevision++
        lastPinOutcome = null
        checkedSettings = false
        settingsOpenFailed = false
        binding.statusText.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        showShortcutMessage(null)
    }

    private fun updateNoticeVisibility() {
        val hasMessage = listOf(binding.statusText, binding.shortcutStatusText, binding.errorText)
            .any { it.isVisible }
        binding.shortcutFeedbackGroup.visibility = if (hasMessage) View.VISIBLE else View.GONE
    }

    private fun setNoticeText(view: TextView, messageRes: Int, topic: HelpTopic) {
        val message = getString(messageRes)
        val help = getString(R.string.notice_help)
        view.text = SpannableString("$message $help").apply {
            setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { sheets.show(topic) }
            }, message.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // LinkMovementMethod handles only the span; the explanation and card
        // must not become an additional button or long-press action.
        view.movementMethod = LinkMovementMethod.getInstance()
        view.isClickable = false
        view.isLongClickable = false
    }

    private fun render(supportState: SupportState, outcome: OpenOutcome? = null) {
        val model = MainScreenPresenter.present(supportState, outcome)
        binding.statusText.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        ViewCompat.setStateDescription(binding.openHistoryButton, model.statusTextRes?.let(::getString))
        setEnabled(binding.openHistoryButton, model.buttonEnabled)
        updateNoticeVisibility()
    }

    private fun setEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.45f
    }

    private fun setActionStatus(button: TextView, labelRes: Int, statusRes: Int) {
        val label = getString(labelRes)
        val status = getString(statusRes)
        button.text = SpannableString("$label\n$status").apply {
            setSpan(RelativeSizeSpan(0.75f), label.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.NORMAL), label.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // The whole action is one accessible control; the help icon remains independent.
        button.contentDescription = label
        ViewCompat.setStateDescription(button, status)
    }
}
