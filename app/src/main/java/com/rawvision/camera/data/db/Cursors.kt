package com.rawvision.camera.data.db

import android.database.Cursor

internal fun Cursor.getStringOrNull(column: String): String? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getString(i)
}

internal fun Cursor.getIntOrNull(column: String): Int? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getInt(i)
}

internal fun Cursor.getLongOrNull(column: String): Long? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getLong(i)
}

internal fun Cursor.getFloatOrNull(column: String): Float? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getFloat(i)
}

internal fun Cursor.getDoubleOrNull(column: String): Double? {
    val i = getColumnIndexOrThrow(column)
    return if (isNull(i)) null else getDouble(i)
}

internal fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
internal fun Cursor.float(column: String): Float = getFloat(getColumnIndexOrThrow(column))
internal fun Cursor.bool(column: String): Boolean = getInt(getColumnIndexOrThrow(column)) != 0
