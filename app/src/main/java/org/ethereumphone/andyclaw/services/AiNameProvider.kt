package org.ethereumphone.andyclaw.services

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import android.util.Log
import org.ethereumphone.andyclaw.NodeApp

/**
 * Read-only ContentProvider that exposes the user-chosen AI name.
 *
 * The ethOS launcher queries this to display the custom name instead of
 * the static manifest label.
 *
 * Authority: org.ethereumphone.andyclaw.ai.settings
 * Path:      /ai_name   ->  single-row cursor with column "ai_name"
 * Path:      /is_setup  ->  single-row cursor with column "is_setup" (1 or 0)
 */
class AiNameProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "org.ethereumphone.andyclaw.ai.settings"
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        if (uri.lastPathSegment != "ai_name") return null

        // Only allow queries from the system or our own process
        val callingUid = Binder.getCallingUid()
        val myUid = Process.myUid()
        if (callingUid != myUid && callingUid != Process.SYSTEM_UID) {
            val callerPackages = context?.packageManager?.getPackagesForUid(callingUid)
            val allowed = callerPackages?.any { it.startsWith("org.ethereumphone.") } == true
            if (!allowed) {
                Log.w("AiNameProvider", "Rejected query from unauthorized caller (uid=$callingUid)")
                return null
            }
        }

        val app = context?.applicationContext as? NodeApp

        return when (uri.lastPathSegment) {
            "ai_name" -> {
                val name = app?.securePrefs?.aiName?.value ?: "AndyClaw"
                MatrixCursor(arrayOf("ai_name")).apply {
                    addRow(arrayOf(name))
                }
            }
            "is_setup" -> {
                val setup = app?.userStoryManager?.exists() == true
                MatrixCursor(arrayOf("is_setup")).apply {
                    addRow(arrayOf(if (setup) 1 else 0))
                }
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
