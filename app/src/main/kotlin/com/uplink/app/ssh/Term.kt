package com.uplink.app

object TermCore {
    init {
        System.loadLibrary("nxcore")
    }

    external fun create(cols: Int, rows: Int): Long
    external fun destroy(handle: Long)
    external fun feed(handle: Long, data: CharArray, count: Int): String?
    external fun resize(handle: Long, cols: Int, rows: Int)
    external fun state(handle: Long, out: IntArray)
    external fun row(handle: Long, index: Int, ch: IntArray, fg: IntArray, bg: IntArray, fl: IntArray)
}

class Line(cols: Int) {
    val ch = IntArray(cols)
    val fg = IntArray(cols)
    val bg = IntArray(cols)
    val fl = IntArray(cols)
}

class HistoryCount(private val owner: Term) {
    val size: Int get() = owner.historySize
}

class Term(startCols: Int, startRows: Int) {
    var cols = startCols
        private set
    var rows = startRows
        private set
    var cx = 0
        private set
    var cy = 0
        private set
    var appCursor = false
        private set
    var bracketed = false
        private set
    var cursorVisible = true
        private set
    var historySize = 0
        private set
    var reply: (String) -> Unit = {}

    val history = HistoryCount(this)

    private var handle = TermCore.create(startCols, startRows)
    private val snapshot = IntArray(6)
    private var scratch = Line(startCols)

    private fun pull() {
        TermCore.state(handle, snapshot)
        cx = snapshot[0]
        cy = snapshot[1]
        appCursor = snapshot[2] != 0
        bracketed = snapshot[3] != 0
        cursorVisible = snapshot[4] != 0
        historySize = snapshot[5]
    }

    fun feed(b: CharArray, n: Int) {
        val answer = TermCore.feed(handle, b, n)
        pull()
        if (answer != null) reply(answer)
    }

    fun resize(nc: Int, nr: Int) {
        TermCore.resize(handle, nc, nr)
        cols = nc
        rows = nr
        scratch = Line(nc)
        pull()
    }

    fun lineAt(i: Int): Line {
        val l = scratch
        TermCore.row(handle, i, l.ch, l.fg, l.bg, l.fl)
        return l
    }

    protected fun finalize() {
        val h = handle
        handle = 0L
        if (h != 0L) TermCore.destroy(h)
    }
}
