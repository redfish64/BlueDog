package com.rareventure.bluedog.presentation.utils

import android.content.Context
import com.rareventure.bluedog.presentation.MainActivity

/**
 * Runs [action] only when the host activity is ready to handle user input.
 * Falls back to executing the action immediately if no [MainActivity] is available
 * (e.g. during previews or tests).
 *
 * @param updateComposeState optional callback that mirrors the activity's dimming state.
 */
inline fun Context.runIfInteractionAllowed(
    noinline updateComposeState: ((Boolean) -> Unit)? = null,
    crossinline action: () -> Unit
) {
    val mainActivity = this as? MainActivity
    if (mainActivity == null || !mainActivity.shouldIgnoreTapAfterDimmed(updateComposeState)) {
        action()
    }
}
