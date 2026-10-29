package com.uplink.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val PROBE = """
echo @stat; grep '^cpu' /proc/stat
echo @mem; grep -E '^(MemTotal|MemFree|MemAvailable|Buffers|Cached|SReclaimable|SwapTotal|SwapFree):' /proc/meminfo
echo @net; cat /proc/net/dev
echo @load; cat /proc/loadavg
echo @up; cat /proc/uptime
echo @temp; cat /sys/class/thermal/thermal_zone0/temp
echo @host; hostname
echo @kernel; uname -r
echo @arch; uname -m
echo @os; grep -m1 PRETTY_NAME /etc/os-release | cut -d= -f2 | tr -d '"'
echo @ips; hostname -I
echo @cpu; grep -m1 'model name' /proc/cpuinfo; lscpu | grep -m1 'Model name'
echo @cores; grep -c '^processor' /proc/cpuinfo
echo @mhz; grep -m1 'cpu MHz' /proc/cpuinfo
echo @gpu; lspci | grep -i -E 'vga|3d|display'; nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total,temperature.gpu --format=csv,noheader
echo @df; df -PT -B1 | tail -n +2
echo @pc; ps -e --no-headers | wc -l
echo @psc; ps -eo pid,pcpu,rss,comm --sort=-pcpu --no-headers | head -8
echo @psm; ps -eo pid,pcpu,rss,comm --sort=-rss --no-headers | head -8
""".trimIndent().let { "{\n" + it + "\n} 2>/dev/null" }

private val FS_OK = setOf("ext2", "ext3", "ext4", "xfs", "btrfs", "zfs", "f2fs", "vfat", "ntfs", "ntfs3", "exfat", "overlay", "fuseblk")

private class Disk(val mount: String, val dev: String, val type: String, val total: Long, val used: Long)
private class Proc(val pid: Int, val cpu: Float, val mem: Long, val name: String)

private class Snap(
    val host: String, val os: String, val kernel: String, val arch: String, val ips: String,
    val cpuModel: String, val cores: Int, val mhz: Float?, val cpu: Float, val perCore: List<Float>,
    val load: String, val temp: Float?, val uptime: Long,
    val memTotal: Long, val memUsed: Long, val memFree: Long, val buffers: Long, val cached: Long,
    val swapTotal: Long, val swapUsed: Long,
    val disks: List<Disk>, val rx: Double, val tx: Double, val rxTotal: Long, val txTotal: Long,
    val gpu: List<String>, val procCount: Int, val topCpu: List<Proc>, val topMem: List<Proc>,
)

private class Sampler {
    private var prevCpu = emptyMap<String, Pair<Long, Long>>()
    private var prevNet: Pair<Long, Long>? = null
    private var prevAt = 0L

    private fun sections(raw: String): Map<String, List<String>> {
        val out = HashMap<String, MutableList<String>>()
        var cur: MutableList<String>? = null
        for (line in raw.lines()) {
            if (line.startsWith("@") && !line.contains(' ')) {
                cur = mutableListOf()
                out[line.substring(1)] = cur
            } else if (line.isNotBlank()) cur?.add(line)
        }
        return out
    }

    private fun kb(m: Map<String, Long>, k: String) = (m[k] ?: 0L) * 1024

    private fun procs(lines: List<String>) = lines.mapNotNull { l ->
        val f = l.trim().split(Regex("\\s+"), 4)
        if (f.size < 4) null else Proc(f[0].toIntOrNull() ?: 0, f[1].toFloatOrNull() ?: 0f, (f[2].toLongOrNull() ?: 0L) * 1024, f[3])
    }

    fun read(raw: String): Snap {
        val now = System.currentTimeMillis()
        val dt = if (prevAt == 0L) 0.0 else (now - prevAt) / 1000.0
        prevAt = now
        val s = sections(raw)
        fun one(k: String) = s[k]?.firstOrNull()?.trim().orEmpty()

        val cpuNow = HashMap<String, Pair<Long, Long>>()
        val use = HashMap<String, Float>()
        for (l in s["stat"].orEmpty()) {
            val f = l.trim().split(Regex("\\s+"))
            val n = f.drop(1).map { it.toLongOrNull() ?: 0L }
            if (n.size < 5) continue
            val idle = n[3] + n[4]
            val total = n.take(8).sum()
            cpuNow[f[0]] = idle to total
            val p = prevCpu[f[0]]
            if (p != null && total > p.second) {
                use[f[0]] = ((1f - (idle - p.first).toFloat() / (total - p.second)) * 100f).coerceIn(0f, 100f)
            }
        }
        prevCpu = cpuNow
        val perCore = cpuNow.keys.filter { it != "cpu" }.sortedBy { it.removePrefix("cpu").toIntOrNull() ?: 0 }.map { use[it] ?: 0f }

        val mi = HashMap<String, Long>()
        for (l in s["mem"].orEmpty()) {
            val f = l.replace(":", "").trim().split(Regex("\\s+"))
            if (f.size >= 2) mi[f[0]] = f[1].toLongOrNull() ?: 0L
        }
        val total = kb(mi, "MemTotal")
        val avail = if (mi.containsKey("MemAvailable")) kb(mi, "MemAvailable") else kb(mi, "MemFree")

        var rxTotal = 0L
        var txTotal = 0L
        for (l in s["net"].orEmpty().drop(2)) {
            val name = l.substringBefore(':').trim()
            if (name == "lo" || !l.contains(':')) continue
            val f = l.substringAfter(':').trim().split(Regex("\\s+")).map { it.toLongOrNull() ?: 0L }
            if (f.size < 9) continue
            rxTotal += f[0]
            txTotal += f[8]
        }
        val pn = prevNet
        var rx = 0.0
        var tx = 0.0
        if (pn != null && dt > 0) {
            rx = maxOf(0L, rxTotal - pn.first) / dt
            tx = maxOf(0L, txTotal - pn.second) / dt
        }
        prevNet = rxTotal to txTotal

        val seen = HashSet<String>()
        val disks = ArrayList<Disk>()
        for (l in s["df"].orEmpty()) {
            val f = l.trim().split(Regex("\\s+"), 7)
            if (f.size < 7 || f[1] !in FS_OK || !seen.add(f[0])) continue
            val size = f[2].toLongOrNull() ?: 0L
            if (size <= 0) continue
            disks.add(Disk(f[6], f[0], f[1], size, f[3].toLongOrNull() ?: 0L))
        }

        val cpuLine = s["cpu"].orEmpty().firstOrNull { it.contains(':') }?.substringAfter(':')?.trim().orEmpty()
        val temp = one("temp").toFloatOrNull()?.let { if (it > 1000) it / 1000f else it }
        return Snap(
            host = one("host"), os = one("os").ifEmpty { "Linux" }, kernel = one("kernel"), arch = one("arch"), ips = one("ips"),
            cpuModel = cpuLine.ifEmpty { "CPU" }, cores = one("cores").toIntOrNull() ?: perCore.size,
            mhz = one("mhz").substringAfter(':', "").trim().toFloatOrNull(),
            cpu = use["cpu"] ?: 0f, perCore = perCore,
            load = one("load").split(" ").take(3).joinToString("  "),
            temp = temp, uptime = one("up").substringBefore('.').toLongOrNull() ?: 0L,
            memTotal = total, memUsed = total - avail, memFree = kb(mi, "MemFree"), buffers = kb(mi, "Buffers"),
            cached = kb(mi, "Cached") + kb(mi, "SReclaimable"),
            swapTotal = kb(mi, "SwapTotal"), swapUsed = kb(mi, "SwapTotal") - kb(mi, "SwapFree"),
            disks = disks, rx = rx, tx = tx, rxTotal = rxTotal, txTotal = txTotal,
            gpu = s["gpu"].orEmpty().map { it.substringAfter(": ").trim() },
            procCount = one("pc").toIntOrNull() ?: 0, topCpu = procs(s["psc"].orEmpty()), topMem = procs(s["psm"].orEmpty()),
        )
    }
}

private fun size(b: Long): String = when {
    b < 1024L -> "$b B"
    b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    b < 1024L * 1024 * 1024 * 1024 -> "%.2f GB".format(b / 1073741824.0)
    else -> "%.2f TB".format(b / 1099511627776.0)
}

private fun rate(b: Double) = size(b.toLong()) + "/s"

private fun span(sec: Long): String {
    val d = sec / 86400
    val h = sec % 86400 / 3600
    val m = sec % 3600 / 60
    return if (d > 0) "${d}d ${h}h ${m}m" else if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun Panel(title: String, icon: ImageVector, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().nxGlass(18.dp).padding(14.dp)) {
        Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NxIconBox(icon, box = 34.dp)
            Text(title, Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (trailing != null) Text(trailing, fontFamily = FontPrefs.mono.family, fontSize = 13.sp, color = NxDim)
        }
        content()
    }
}

@Composable
private fun Meter(frac: Float, modifier: Modifier = Modifier.fillMaxWidth()) {
    val f = frac.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(50)
    Box(modifier.height(8.dp).clip(shape).background(NxRaised)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(shape).background(if (f > 0.85f) NxDanger else NxAccent))
    }
}

@Composable
private fun Spark(values: List<Float>, ceiling: Float?, modifier: Modifier = Modifier) {
    val line = NxAccent
    val sample = values.toList()
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        if (sample.size < 2) return@Canvas
        val top = ceiling ?: maxOf(sample.max(), 1f)
        val step = size.width / (sample.size - 1)
        val path = Path()
        sample.forEachIndexed { i, v ->
            val x = i * step
            val y = size.height - (v / top).coerceIn(0f, 1f) * (size.height - 4f) - 2f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fill = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, line.copy(alpha = 0.12f))
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(NxLine, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}

@Composable
private fun Fact(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(key, color = NxDim, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Text(value, Modifier.weight(1f), fontFamily = FontPrefs.mono.family, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Big(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontFamily = FontPrefs.mono.family, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(unit, Modifier.padding(start = 4.dp, bottom = 5.dp), color = NxDim, fontSize = 13.sp)
    }
}

@Composable
private fun ProcList(rows: List<Proc>, byMem: Boolean) {
    for (p in rows) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(p.pid.toString(), Modifier.width(54.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
            Text(p.name, Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (byMem) size(p.mem) else "%.1f%%".format(p.cpu),
                fontFamily = FontPrefs.mono.family, fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun SystemPane(vps: Vps) {
    var snap by remember { mutableStateOf<Snap?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val cpuHist = remember { mutableStateListOf<Float>() }
    val memHist = remember { mutableStateListOf<Float>() }
    val rxHist = remember { mutableStateListOf<Float>() }
    val txHist = remember { mutableStateListOf<Float>() }

    fun push(list: MutableList<Float>, v: Float) {
        list.add(v)
        if (list.size > 90) list.removeAt(0)
    }

    LaunchedEffect(vps) {
        val sampler = Sampler()
        while (true) {
            try {
                val raw = withContext(Dispatchers.IO) { vps.exec(PROBE) }
                val s = sampler.read(raw)
                snap = s
                failure = null
                push(cpuHist, s.cpu)
                push(memHist, if (s.memTotal > 0) s.memUsed * 100f / s.memTotal else 0f)
                push(rxHist, s.rx.toFloat())
                push(txHist, s.tx.toFloat())
            } catch (e: Throwable) {
                failure = e.message ?: e.toString()
            }
            delay(2000)
        }
    }

    val s = snap
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(failure ?: tr("Reading system..."), color = if (failure != null) NxDanger else NxDim)
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Panel(tr("System"), NxIcon.Server, span(s.uptime)) {
            Fact(tr("Host"), s.host)
            Fact(tr("OS"), s.os)
            Fact(tr("Kernel"), s.kernel)
            Fact(tr("Arch"), s.arch)
            if (s.ips.isNotEmpty()) Fact(tr("IP"), s.ips)
        }

        Panel("CPU", NxIcon.Chip, tr("{0} cores", s.cores)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Big("%.0f".format(s.cpu), "%")
                Text(
                    s.cpuModel, Modifier.weight(1f).padding(start = 14.dp, bottom = 4.dp),
                    fontSize = 11.sp, color = NxDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spark(cpuHist, 100f, Modifier.padding(vertical = 10.dp))
            Fact(tr("Load"), s.load)
            s.mhz?.let { Fact(tr("Clock"), "%.0f MHz".format(it)) }
            s.temp?.let { Fact(tr("Temp"), "%.0f °C".format(it)) }
            Fact(tr("Processes"), s.procCount.toString())
            if (s.perCore.size > 1) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.perCore.forEachIndexed { i, v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("C$i", Modifier.width(30.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim)
                            Meter(v / 100f, Modifier.weight(1f))
                            Text("%.0f%%".format(v), Modifier.width(44.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Panel(tr("Memory"), NxIcon.Memory, "${size(s.memUsed)} / ${size(s.memTotal)}") {
            val frac = if (s.memTotal > 0) s.memUsed.toFloat() / s.memTotal else 0f
            Big("%.0f".format(frac * 100), "%")
            Spark(memHist, 100f, Modifier.padding(vertical = 10.dp))
            Meter(frac)
            Column(Modifier.padding(top = 10.dp)) {
                Fact(tr("Free"), size(s.memFree))
                Fact(tr("Buffers"), size(s.buffers))
                Fact(tr("Cached"), size(s.cached))
                Fact(tr("Swap"), if (s.swapTotal > 0) "${size(s.swapUsed)} / ${size(s.swapTotal)}" else tr("none"))
            }
            if (s.swapTotal > 0) Meter(s.swapUsed.toFloat() / s.swapTotal, Modifier.fillMaxWidth().padding(top = 6.dp))
        }

        Panel("GPU", NxIcon.Gpu) {
            if (s.gpu.isEmpty()) {
                Text(tr("No GPU detected"), color = NxDim, fontSize = 13.sp)
            } else {
                for (g in s.gpu) Text(g, Modifier.padding(vertical = 3.dp), fontFamily = FontPrefs.mono.family, fontSize = 12.sp)
            }
        }

        Panel(tr("Storage"), NxIcon.Disk) {
            if (s.disks.isEmpty()) Text(tr("No disks found"), color = NxDim, fontSize = 13.sp)
            s.disks.forEachIndexed { i, d ->
                if (i > 0) Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(NxLine))
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text(d.mount, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%.0f%%".format(d.used * 100f / d.total), fontFamily = FontPrefs.mono.family, fontSize = 13.sp)
                }
                Meter(d.used.toFloat() / d.total)
                Text(
                    "${size(d.used)} / ${size(d.total)}  ·  ${d.type}  ·  ${d.dev}",
                    Modifier.padding(top = 6.dp), fontFamily = FontPrefs.mono.family, fontSize = 11.sp, color = NxDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Panel(tr("Network"), NxIcon.Globe) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Download"), color = NxDim, fontSize = 12.sp)
                    Text(rate(s.rx), fontFamily = FontPrefs.mono.family, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(tr("Upload"), color = NxDim, fontSize = 12.sp)
                    Text(rate(s.tx), fontFamily = FontPrefs.mono.family, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spark(rxHist, null, Modifier.padding(top = 10.dp))
            Spark(txHist, null, Modifier.padding(top = 6.dp, bottom = 8.dp))
            Fact(tr("Received"), size(s.rxTotal))
            Fact(tr("Sent"), size(s.txTotal))
        }

        Panel(tr("Top CPU"), NxIcon.Tasks) { ProcList(s.topCpu, false) }
        Panel(tr("Top memory"), NxIcon.Tasks) { ProcList(s.topMem, true) }
    }
}
