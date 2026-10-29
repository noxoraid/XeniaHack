package com.uplink.app

class MindMeter(private val keep: Int = WINDOW) {
    private val on = HashMap<MindSurface, ArrayList<Int>>()
    private val off = HashMap<MindSurface, ArrayList<Int>>()
    private val latest = HashMap<MindSurface, Pair<Boolean, Int>>()

    @Synchronized
    fun record(surface: MindSurface, mindOn: Boolean, chars: Int) {
        val ring = (if (mindOn) on else off).getOrPut(surface) { ArrayList() }
        ring.add(chars)
        while (ring.size > keep) ring.removeAt(0)
        latest[surface] = mindOn to chars
    }

    @Synchronized
    fun averageOn(surface: MindSurface): Double? = on[surface]?.takeIf { it.isNotEmpty() }?.average()

    @Synchronized
    fun averageOff(surface: MindSurface): Double? = off[surface]?.takeIf { it.isNotEmpty() }?.average()

    @Synchronized
    fun countOn(surface: MindSurface): Int = on[surface]?.size ?: 0

    @Synchronized
    fun countOff(surface: MindSurface): Int = off[surface]?.size ?: 0

    @Synchronized
    fun last(surface: MindSurface): Pair<Boolean, Int>? = latest[surface]

    fun percentDiff(surface: MindSurface): Double? {
        val a = averageOn(surface) ?: return null
        val b = averageOff(surface) ?: return null
        if (b <= 0.0) return null
        return (a - b) / b * 100.0
    }

    @Synchronized
    fun clear() {
        on.clear()
        off.clear()
        latest.clear()
    }

    @Synchronized
    fun encode(): String {
        val out = StringBuilder()
        for (s in MindSurface.values()) {
            for (n in on[s].orEmpty()) out.append(s.key).append("\tON\t").append(n).append('\n')
            for (n in off[s].orEmpty()) out.append(s.key).append("\tOFF\t").append(n).append('\n')
            latest[s]?.let { out.append("L\t").append(s.key).append('\t').append(if (it.first) "ON" else "OFF").append('\t').append(it.second).append('\n') }
        }
        return out.toString()
    }

    @Synchronized
    fun decode(text: String) {
        clear()
        for (row in text.split('\n')) {
            val cols = row.split('\t')
            if (cols.size < 3) continue
            if (cols[0] == "L") {
                val s = MindSurface.values().firstOrNull { it.key == cols[1] } ?: continue
                val n = cols.getOrNull(3)?.toIntOrNull() ?: continue
                latest[s] = (cols[2] == "ON") to n
                continue
            }
            val s = MindSurface.values().firstOrNull { it.key == cols[0] } ?: continue
            val n = cols[2].toIntOrNull() ?: continue
            val ring = (if (cols[1] == "ON") on else off).getOrPut(s) { ArrayList() }
            ring.add(n)
            while (ring.size > keep) ring.removeAt(0)
        }
    }

    companion object {
        const val WINDOW = 20

        fun sizeOf(system: String, messages: List<String>): Int = system.length + messages.sumOf { it.length }
    }
}
