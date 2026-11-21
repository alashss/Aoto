package com.jimz.jimztune.data.profile

import com.jimz.jimztune.AppConfig
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi

/** Halaman profil publik (opt-in): rekap dengar + lagu teratas yang bisa dibuka di web tanpa aplikasi. */
object PublicProfile {
    /** Status sekarang, atau null kalau gagal dibaca. */
    suspend fun isOn(): Boolean? {
        val token = AppGraph.auth.validToken() ?: return null
        val r = CloudApi.json("POST", "/rest/v1/rpc/my_public_profile", "{}", token)
        return if (r.ok) r.body.trim() == "true" else null
    }

    /** Menyalakan / mematikan. Mengembalikan status baru, atau null kalau gagal. */
    suspend fun set(on: Boolean): Boolean? {
        val token = AppGraph.auth.validToken() ?: return null
        val r = CloudApi.json("POST", "/rest/v1/rpc/set_public_profile", """{"p_on":$on}""", token)
        return if (r.ok) r.body.trim() == "true" else null
    }

    /** Tautan web profil, atau null kalau alamat web belum diisi / username belum ada. */
    fun link(): String? {
        val base = AppConfig.SHARE_BASE_URL.trim()
        val user = AppGraph.profile.profile?.username ?: return null
        return if (base.startsWith("http")) base.trimEnd('/') + "/u/" + user else null
    }
}
