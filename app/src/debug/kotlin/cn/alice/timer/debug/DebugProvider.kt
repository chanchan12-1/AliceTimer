package cn.alice.timer.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import cn.alice.timer.graph

class DebugProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "vnd.android.cursor.item/alice.state"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val graph = context!!.graph
        val data = graph.store.value
        return MatrixCursor(arrayOf("phase", "foreground", "started", "due", "reminderAt", "reminder", "service", "json")).apply {
            addRow(arrayOf<Any>(data.cycle.phase.name, data.cycle.gameForeground, data.cycle.startedAt, data.cycle.dueAt,
                data.lastReminderAt, data.lastReminder.replace('\n', ' '), graph.serviceRunning.value, graph.store.encode(data).toString()))
        }
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
