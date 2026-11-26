package com.jimz.jimztune.data.social

import androidx.compose.runtime.mutableStateMapOf
import com.jimz.jimztune.AppGraph
import com.jimz.jimztune.data.cloud.CloudApi
import com.jimz.jimztune.data.owner.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class Equipped(val frame: String? = null, val plate: String? = null, val effect: String? = null, val bubble: String? = null) {
    val any: Boolean get() = frame != null || plate != null || effect != null || bubble != null
    val gold: Boolean get() = frame == "frame_gold" || plate == "plate_gold" || effect == "fx_sparkle"
}

data class CosmeticItem(
    val id: String, val kind: String, val name: String, val rarity: String, val unlockLevel: Int?, val owned: Boolean,
    val active: Boolean = true, val holders: Int = 0
)

data class CosmeticsCatalog(val level: Int, val equipped: Equipped, val items: List<CosmeticItem>)
data class HolderRow(val userId: String, val name: String, val username: String?)

private fun JSONObject.s(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
private fun eq(o: JSONObject?) = Equipped(o?.s("frame"), o?.s("plate"), o?.s("effect"), o?.s("bubble"))

object CosmeticsApi {
    private suspend fun rpc(name: String, body: JSONObject = JSONObject()): Res<String> {
        if (!CloudApi.configured) return Res(null, "Server belum diatur")
        val token = AppGraph.auth.validToken() ?: return Res(null, "Masuk dulu")
        val r = CloudApi.json("POST", "/rest/v1/rpc/$name", body.toString(), token)
        return if (r.ok) Res(r.body.ifBlank { "null" }) else Res(null, CloudApi.errorText(r))
    }

    private fun items(a: JSONArray?) = (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }.map {
        CosmeticItem(
            it.optString("id"), it.optString("kind"), it.optString("name"), it.optString("rarity", "common"),
            if (it.isNull("unlock_level")) null else it.optInt("unlock_level"), it.optBoolean("owned"),
            it.optBoolean("active", true), it.optInt("holders")
        )
    }

    suspend fun catalog(): Res<CosmeticsCatalog> {
        val r = rpc("cosmetics_catalog")
        val t = r.value ?: return Res(null, r.error)
        return try {
            val o = JSONObject(t)
            Res(CosmeticsCatalog(o.optInt("level", 1), eq(o.optJSONObject("equipped")), items(o.optJSONArray("items"))))
        } catch (_: Exception) { Res(null, "Respons tidak dikenali") }
    }

    suspend fun equip(e: Equipped): String? {
        var r = rpc("cosmetics_equip_v2", JSONObject().put("p_frame", e.frame ?: "").put("p_plate", e.plate ?: "").put("p_effect", e.effect ?: "").put("p_bubble", e.bubble ?: ""))
        // SQL tahap 33 belum dipasang: simpan tiga jenis lama saja.
        if (r.error != null && r.error.contains("cosmetics_equip_v2", true)) {
            r = rpc("cosmetics_equip", JSONObject().put("p_frame", e.frame ?: "").put("p_plate", e.plate ?: "").put("p_effect", e.effect ?: ""))
        }
        if (r.error == null) CosmeticsStore.put(AppGraph.auth.session?.userId ?: "", e)
        return r.error
    }

    suspend fun of(ids: List<String>): Map<String, Equipped>? {
        val r = rpc("cosmetics_of", JSONObject().put("p_users", JSONArray(ids)))
        val t = r.value ?: return null
        return try {
            val o = JSONObject(t)
            o.keys().asSequence().associateWith { eq(o.optJSONObject(it)) }
        } catch (_: Exception) { null }
    }

    // ---- owner
    suspend fun ownerItems(): Res<List<CosmeticItem>> {
        val r = rpc("owner_cosmetic_items")
        val t = r.value ?: return Res(null, r.error)
        return try { Res(items(JSONArray(t))) } catch (_: Exception) { Res(null, "Respons tidak dikenali") }
    }
    suspend fun ownerSet(item: String, level: Int?, active: Boolean): String? =
        rpc("owner_cosmetic_set", JSONObject().put("p_item", item).put("p_unlock_level", level ?: 0).put("p_active", active)).error
    suspend fun grant(user: String, item: String): String? =
        rpc("owner_cosmetic_grant", JSONObject().put("p_user", user).put("p_item", item)).error
    suspend fun revoke(user: String, item: String): String? =
        rpc("owner_cosmetic_revoke", JSONObject().put("p_user", user).put("p_item", item)).error
    suspend fun ownerOfUser(user: String): Res<List<String>> {
        val r = rpc("owner_cosmetic_of_user", JSONObject().put("p_user", user))
        val t = r.value ?: return Res(null, r.error)
        return try { val a = JSONArray(t); Res((0 until a.length()).map { a.getJSONObject(it).optString("id") }) } catch (_: Exception) { Res(null, "Respons tidak dikenali") }
    }
    suspend fun holders(item: String): Res<List<HolderRow>> {
        val r = rpc("owner_cosmetic_holders", JSONObject().put("p_item", item))
        val t = r.value ?: return Res(null, r.error)
        return try {
            val a = JSONArray(t)
            Res((0 until a.length()).map { val o = a.getJSONObject(it); HolderRow(o.optString("user_id"), o.optString("name"), o.s("username")) })
        } catch (_: Exception) { Res(null, "Respons tidak dikenali") }
    }
    suspend fun socialStats(): Res<Map<String, Int>> {
        val r = rpc("owner_social_stats")
        val t = r.value ?: return Res(null, r.error)
        return try { val o = JSONObject(t); Res(o.keys().asSequence().associateWith { o.optInt(it) }) } catch (_: Exception) { Res(null, "Respons tidak dikenali") }
    }
}

/** Cache kosmetik yang dipakai orang lain; permintaan digabung supaya satu layar = satu panggilan server. */
object CosmeticsStore {
    val map = mutableStateMapOf<String, Equipped>()
    private val asked = HashMap<String, Long>()
    private val pending = LinkedHashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: kotlinx.coroutines.Job? = null

    fun put(id: String, e: Equipped) { map[id] = e; asked[id] = System.currentTimeMillis() }

    fun request(id: String) {
        if (id.isBlank()) return
        val last = asked[id]
        if (last != null && System.currentTimeMillis() - last < 90_000) return
        asked[id] = System.currentTimeMillis()
        pending.add(id)
        if (job?.isActive == true) return
        job = scope.launch {
            delay(120)
            while (pending.isNotEmpty()) {
                val batch = pending.take(100)
                pending.removeAll(batch.toSet())
                val got = CosmeticsApi.of(batch)
                if (got == null) { batch.forEach { asked.remove(it) }; continue }
                batch.forEach { map[it] = got[it] ?: Equipped() }
            }
        }
    }
}
