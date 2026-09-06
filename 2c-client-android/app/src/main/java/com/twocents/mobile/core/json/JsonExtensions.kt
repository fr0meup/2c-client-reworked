package com.twocents.mobile.core.json

import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON accessors shared by API parsers. They deliberately preserve the loose
 * coercion used by the original feature-local helpers because older API
 * responses contain a mixture of JSON numbers and numeric strings.
 */
internal fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else buildList {
    for (index in 0 until length()) optJSONObject(index)?.let(::add)
}

internal fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else buildList {
    for (index in 0 until length()) optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
}

internal fun JSONObject.objectValue(key: String): JSONObject? = when (val value = opt(key)) {
    is JSONObject -> value
    is String -> runCatching { JSONObject(value) }.getOrNull()
    else -> null
}

internal fun JSONObject.string(key: String): String? = opt(key)
    .takeUnless { it == null || it == JSONObject.NULL }
    ?.toString()
    ?.takeIf { it.isNotBlank() && it != "null" }

internal fun JSONObject.number(key: String): Number? = when (val value = opt(key)) {
    is Number -> value
    is String -> value.toDoubleOrNull()
    else -> null
}

internal fun JSONObject.int(key: String): Int = number(key)?.toInt() ?: 0
internal fun JSONObject.double(key: String): Double = number(key)?.toDouble() ?: 0.0
internal fun JSONObject.nullableInt(key: String): Int? = number(key)?.toInt()
internal fun JSONObject.nullableDouble(key: String): Double? = number(key)?.toDouble()
