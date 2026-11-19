package com.jimz.jimztune.data.search

/**
 * Daftar instance publik. Instance sering mati/berganti, makanya daftarnya banyak dan dicoba bersamaan.
 * Kalau ada yang mati, cukup hapus atau tambah di sini.
 */
object PublicInstances {
    val piped = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru",
        "https://piped-api.privacy.com.de",
        "https://pipedapi.drgns.space",
        "https://pipedapi.owo.si",
        "https://pipedapi.reallyaweso.me",
        "https://pipedapi.darkness.services",
        "https://api.piped.yt"
    )
    val invidious = listOf(
        "https://inv.nadeko.net",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de",
        "https://invidious.privacyredirect.com",
        "https://invidious.fdn.fr",
        "https://iv.melmac.space",
        "https://invidious.lunar.icu",
        "https://inv.tux.pizza",
        "https://yt.artemislena.eu"
    )
}
