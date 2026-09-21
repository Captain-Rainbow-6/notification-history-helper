package io.github.captainrainbow.notificationhistoryhelper

import androidx.annotation.StringRes

data class MainScreenModel(
    @field:StringRes val statusTextRes: Int?,
    val buttonEnabled: Boolean,
    @field:StringRes val errorTextRes: Int?,
)

object MainScreenPresenter {
    fun present(
        supportState: SupportState,
        outcome: OpenOutcome? = null,
    ): MainScreenModel = MainScreenModel(
        statusTextRes = when (supportState) {
            SupportState.SUPPORTED -> null
            SupportState.UNSUPPORTED -> R.string.status_unsupported
        },
        buttonEnabled = supportState == SupportState.SUPPORTED,
        errorTextRes = if (outcome == OpenOutcome.FAILED) {
            R.string.open_failed
        } else {
            null
        },
    )
}
