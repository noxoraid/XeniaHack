package com.uplink.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun stroked(name: String, paths: List<String>, width: Float = 1.8f): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in paths) {
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

private const val FOLDER = "M3 7a2 2 0 0 1 2-2h4l2 2.5h8a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
private const val SHEET = "M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"
private const val ARCHIVE_BODY = "M6 8v11a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V8"
private const val ARCHIVE_LID = "M5 4h14v4H5z"

object NxIcon {
    val Terminal = stroked(
        "terminal",
        listOf("M4 5h16a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z", "M7 10l3 2.5L7 15", "M13 15h4"),
    )
    val Folder = stroked("folder", listOf(FOLDER))
    val File = stroked("file", listOf(SHEET, "M14 3v5h5"))
    val Home = stroked("home", listOf("M4 11l8-7 8 7", "M6 10v9h12v-9", "M10 19v-5h4v5"))
    val Refresh = stroked("refresh", listOf("M20 11a8 8 0 1 0-2.3 5.7", "M20 5v6h-6"))
    val FolderPlus = stroked("folder_plus", listOf(FOLDER, "M12 11v5", "M9.5 13.5h5"))
    val FilePlus = stroked("file_plus", listOf(SHEET, "M14 3v5h5", "M12 12v5", "M9.5 14.5h5"))
    val Upload = stroked("upload", listOf("M12 16V5", "M7.5 9.5L12 5l4.5 4.5", "M5 19h14"))
    val Download = stroked("download", listOf("M12 5v11", "M7.5 11.5L12 16l4.5-4.5", "M5 19h14"))
    val FolderUp = stroked("folder_up", listOf(FOLDER, "M12 16v-5", "M9.5 13l2.5-2.5 2.5 2.5"))
    val Settings = stroked(
        "settings",
        listOf("M4 7h9", "M13 7a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M17 7h3", "M4 17h1", "M5 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M9 17h11"),
    )
    val Logout = stroked("logout", listOf("M10 5H6a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h4", "M14 8l4 4-4 4", "M18 12H9"))
    val Send = stroked("send", listOf("M20 4L10.5 13.5", "M20 4l-6 16-3.5-6.5L4 10z"))
    val Paste = stroked(
        "paste",
        listOf("M9 4h6v3H9z", "M8 5.5H6a1 1 0 0 0-1 1V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V6.5a1 1 0 0 0-1-1h-2"),
    )
    val Copy = stroked(
        "copy",
        listOf("M9 9h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V10a1 1 0 0 1 1-1z", "M16 9V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3"),
    )
    val Menu = stroked("menu", listOf("M4 7h16", "M4 12h16", "M4 17h10"))
    val Close = stroked("close", listOf("M6 6l12 12", "M18 6L6 18"))
    val Plus = stroked("plus", listOf("M12 5v14", "M5 12h14"))
    val Back = stroked("back", listOf("M19 12H6", "M11 6l-6 6 6 6"))
    val Check = stroked("check", listOf("M5 12.5l4.5 4.5L19 7"))
    val Trash = stroked("trash", listOf("M5 7h14", "M9 7V5h6v2", "M7 7l1 12h8l1-12", "M10 11v5", "M14 11v5"))
    val Edit = stroked("edit", listOf("M4 20l1-4L16 5a2 2 0 0 1 3 3L8 19z", "M14 7l3 3"))
    val Rename = stroked("rename", listOf("M9 4h6", "M12 4v16", "M9 20h6"))
    val More = stroked("more", listOf("M12 5v.01", "M12 12v.01", "M12 19v.01"), 2.8f)
    val Archive = stroked("archive", listOf(ARCHIVE_LID, ARCHIVE_BODY, "M10 12h4"))
    val Unarchive = stroked("unarchive", listOf(ARCHIVE_LID, ARCHIVE_BODY, "M12 11v5", "M9.5 13.5L12 16l2.5-2.5"))
    val Save = stroked("save", listOf("M5 4h11l3 3v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1z", "M8 4v5h7V4", "M8 20v-6h8v6"))
    val Undo = stroked("undo", listOf("M9 7l-4 4 4 4", "M5 11h8a5 5 0 0 1 0 10h-3"))
    val Up = stroked("up", listOf("M12 19V5", "M6.5 10.5L12 5l5.5 5.5"))
    val Down = stroked("down", listOf("M12 5v14", "M6.5 13.5L12 19l5.5-5.5"))
    val Left = stroked("left", listOf("M19 12H5", "M10.5 6.5L5 12l5.5 5.5"))
    val Right = stroked("right", listOf("M5 12h14", "M13.5 6.5L19 12l-5.5 5.5"))
    val Sun = stroked(
        "sun",
        listOf(
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M12 3v2", "M12 19v2", "M3 12h2", "M19 12h2",
            "M5.6 5.6L7 7", "M17 17l1.4 1.4", "M5.6 18.4L7 17", "M17 7l1.4-1.4",
        ),
    )
    val Moon = stroked("moon", listOf("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"))
    val Chip = stroked(
        "chip",
        listOf("M7 7h10v10H7z", "M10 10h4v4h-4z", "M9 3v4", "M15 3v4", "M9 17v4", "M15 17v4", "M3 9h4", "M3 15h4", "M17 9h4", "M17 15h4"),
    )
    val Memory = stroked(
        "memory",
        listOf("M3 7h18v9H3z", "M7 7v9", "M12 7v9", "M17 7v9", "M6 16v2", "M10 16v2", "M14 16v2", "M18 16v2"),
    )
    val Gpu = stroked("gpu", listOf("M3 7h15v10H3z", "M18 10h3v4h-3z", "M8 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "M6 20h9"))
    val Disk = stroked(
        "disk",
        listOf(
            "M4 7c0-1.7 3.6-3 8-3s8 1.3 8 3-3.6 3-8 3-8-1.3-8-3z", "M4 7v10c0 1.7 3.6 3 8 3s8-1.3 8-3V7",
            "M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3",
        ),
    )
    val Globe = stroked(
        "globe",
        listOf("M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0", "M4 12h16", "M12 4c2.5 2.3 2.5 13.7 0 16", "M12 4c-2.5 2.3-2.5 13.7 0 16"),
    )
    val Pulse = stroked("pulse", listOf("M3 12h4l2-6 4 12 2-6h6"))
    val Server = stroked("server", listOf("M4 5h16v6H4z", "M4 13h16v6H4z", "M8 8v.01", "M8 16v.01"), 2.4f)
    val Bot = stroked(
        "bot",
        listOf("M5 9h14a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z", "M12 9V5", "M12 4v.01", "M9 13.5v.01", "M15 13.5v.01", "M9.5 16h5", "M2 13v3", "M22 13v3"),
    )
    val Tasks = stroked("tasks", listOf("M8 6h12", "M8 12h12", "M8 18h12", "M4 6v.01", "M4 12v.01", "M4 18v.01"), 2.4f)
    val Cut = stroked(
        "cut",
        listOf("M3 7a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M3 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M8.5 8.5L20 18", "M8.5 15.5L20 6"),
    )
    val Shield = stroked("shield", listOf("M12 3l7 3v5c0 5-3 8-7 10-4-2-7-5-7-10V6z", "M9 12l2 2 4-4"))
    val Phone = stroked("phone", listOf("M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M11 18h2"))
    val Grid = stroked("grid", listOf("M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z"))
    val Info = stroked("info", listOf("M4 12a8 8 0 1 0 16 0a8 8 0 1 0-16 0", "M12 11v5", "M12 8v.01"), 2.2f)
    val Bolt = stroked("bolt", listOf("M13 3L5 14h6l-1 7 8-11h-6z"))
    val Gear = stroked(
        "gear",
        listOf(
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            "M12 3v3", "M12 18v3", "M3 12h3", "M18 12h3", "M5.6 5.6l2.1 2.1", "M16.3 16.3l2.1 2.1", "M5.6 18.4l2.1-2.1", "M16.3 7.7l2.1-2.1",
        ),
    )
    val Swap = stroked("swap", listOf("M4 8h14", "M14 4l4 4-4 4", "M20 16H6", "M10 12l-4 4 4 4"))
    val Play = stroked("play", listOf("M8 5l11 7-11 7z"))
    val Pause = stroked("pause", listOf("M8 5v14", "M16 5v14"), 2.6f)
    val Image = stroked("image", listOf("M4 5h16v14H4z", "M4 16l5-5 4 4 3-3 4 4", "M15 9v.01"))
    val Music = stroked("music", listOf("M9 18V5l11-2v13", "M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M14 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0"))
    val Video = stroked("video", listOf("M3 6h13v12H3z", "M16 10l5-3v10l-5-3"))
    val Wallet = stroked("wallet", listOf("M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", "M4 7V6a2 2 0 0 1 2-2h10", "M15.5 13.5v.01"), 2.4f)
    val History = stroked("history", listOf("M4 12a8 8 0 1 0 2.5-5.8", "M4 4v5h5", "M12 8v4l3 2"))
    val Git = stroked(
        "git",
        listOf("M6 4v10", "M3 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M15 7a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M18 10v1a4 4 0 0 1-4 4h-2.5"),
    )
    val Monitor = stroked("monitor", listOf("M3 5h18v11H3z", "M8 20h8", "M12 16v4"))
    val Database = stroked("database", listOf("M4 6a8 3 0 1 0 16 0a8 3 0 1 0-16 0", "M4 6v6c0 1.7 3.6 3 8 3s8-1.3 8-3V6", "M4 12v6c0 1.7 3.6 3 8 3s8-1.3 8-3v-6"))
    val Flame = stroked("flame", listOf("M12 3c1 4 5 5.5 5 10a5 5 0 0 1-10 0c0-2 1-3 2-4 .3 1.3 1 2 2 2-.5-3 0-6 1-8z"))
    val Triangle = stroked("triangle", listOf("M12 4l9 16H3z"))
    val Search = stroked("search", listOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M20 20l-4-4"))
    val Bell = stroked("bell", listOf("M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 21h4"))
}
