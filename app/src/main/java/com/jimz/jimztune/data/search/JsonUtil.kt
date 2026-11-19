package com.jimz.jimztune.data.search

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.obj(vararg path: String): JSONObject? {
    var cur: JSONObject? = this
    for (key in path) cur = cur?.optJSONObject(key)
    return cur
}

internal fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

internal fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

internal fun formatDuration(totalSeconds: Long): String? {
    if (totalSeconds <= 0) return null
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** String aman: JSON null dianggap kosong (bukan teks "null"). */
internal fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key)
