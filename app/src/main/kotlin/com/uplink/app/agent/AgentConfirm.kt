package com.uplink.app

import android.content.Context

object AgentConfirm {
    private val say: NxSay = { key, args -> tr(key, *args) }

    fun ask(
        app: Context, place: String, line: String, replace: Boolean, targets: List<NxTarget>,
        stopped: () -> Boolean, onOut: (String) -> Unit,
    ): Boolean {
        val names = NxConfirm.distinct(targets)
        val head = NxConfirm.head(replace, names.size, say)
        onOut(tr("Waiting for your approval"))
        return ApprovalBoard.request(app, place, line, head + "\n" + NxConfirm.lines(names).joinToString("\n"), stopped)
    }
}
