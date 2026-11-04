package com.picksure.android.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Records only a sanitized operation label and stack trace when Firebase is configured. */
object CrashReporter {
    fun record(context: Context, operation: String, error: Throwable) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val sanitized = RuntimeException("PickSure operation failed: $operation").apply {
            stackTrace = error.stackTrace
        }
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("operation", operation)
                recordException(sanitized)
            }
        }
    }
}
