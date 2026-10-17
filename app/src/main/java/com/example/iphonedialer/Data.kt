package com.example.iphonedialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat

data class ContactItem(val name: String, val number: String)

data class CallItem(val name: String?, val number: String, val type: Int, val date: Long) {
    val isMissed: Boolean
        get() = type == CallLog.Calls.MISSED_TYPE ||
            type == CallLog.Calls.REJECTED_TYPE ||
            type == CallLog.Calls.BLOCKED_TYPE
    val title: String get() = name ?: number.ifBlank { "Unknown" }
}

fun Context.granted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun loadContacts(ctx: Context): List<ContactItem> {
    val list = mutableListOf<ContactItem>()
    try {
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            null, null,
            "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            val n = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME)
            val p = c.getColumnIndexOrThrow(Phone.NUMBER)
            while (c.moveToNext()) {
                val name = c.getString(n) ?: continue
                val num = c.getString(p) ?: continue
                list += ContactItem(name, num)
            }
        }
    } catch (_: SecurityException) { }
    return list.distinctBy { it.name + it.number.filter(Char::isDigit) }
}

fun loadCalls(ctx: Context, max: Int = 300): List<CallItem> {
    val list = mutableListOf<CallItem>()
    try {
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            null, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            val num = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nam = c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typ = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dat = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            while (c.moveToNext() && list.size < max) {
                list += CallItem(
                    name = c.getString(nam)?.takeIf { it.isNotBlank() },
                    number = c.getString(num).orEmpty(),
                    type = c.getInt(typ),
                    date = c.getLong(dat)
                )
            }
        }
    } catch (_: SecurityException) { }
    return list
}

fun placeCall(ctx: Context, number: String) {
    if (number.isBlank()) return
    val action = if (ctx.granted(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
    ctx.startActivity(Intent(action, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
