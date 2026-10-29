package com.uplink.app

import android.content.Context

object AgentLimits {
    const val PHONE_ROUNDS = 30
    const val VPS_ROUNDS = 40
    const val TRIES = 3
    const val TRUNCATED_RETRIES = 6
    const val EMPTY_RETRIES = 2

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nx_agent", Context.MODE_PRIVATE)

    @Volatile var phoneRounds: Int = PHONE_ROUNDS
        private set

    @Volatile var vpsRounds: Int = VPS_ROUNDS
        private set

    @Volatile var tries: Int = TRIES
        private set

    fun load(ctx: Context) {
        val p = prefs(ctx)
        phoneRounds = p.getInt("phone_rounds", PHONE_ROUNDS).coerceIn(4, 200)
        vpsRounds = p.getInt("vps_rounds", VPS_ROUNDS).coerceIn(4, 200)
        tries = p.getInt("tries", TRIES).coerceIn(2, 10)
    }
}
