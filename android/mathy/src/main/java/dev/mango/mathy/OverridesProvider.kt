package dev.mango.mathy

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Read-only feed of your custom names and icons for Mango OS.
 *   content://dev.mango.mathy.overrides/apps          → key, label, icon (version, 0 = none)
 *   content://dev.mango.mathy.overrides/icon/<key>    → the icon PNG
 * Only Mango OS signed with the same key as Mathy may read it.
 */
class OverridesProvider : ContentProvider() {
    override fun onCreate() = true

    private fun checkCaller() {
        val ctx = context ?: throw SecurityException("no context")
        val caller = callingPackage ?: throw SecurityException("unknown caller")
        if (caller == ctx.packageName) return
        val same = ctx.packageManager.checkSignatures(caller, ctx.packageName) == PackageManager.SIGNATURE_MATCH
        if (!same) throw SecurityException("$caller isn't signed like Mathy")
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        checkCaller()
        val c = MatrixCursor(arrayOf("key", "label", "icon"))
        for (o in Store.all(context!!)) c.addRow(arrayOf<Any?>(o.key, o.label, o.iconVersion))
        c.setNotificationUri(context!!.contentResolver, Store.APPS_URI)
        return c
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkCaller()
        val segs = uri.pathSegments
        if (segs.size < 2 || segs[0] != "icon") throw FileNotFoundException(uri.toString())
        val f = Store.iconFile(context!!, segs[1])
        if (!f.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = if (uri.pathSegments.firstOrNull() == "icon") "image/png" else null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
