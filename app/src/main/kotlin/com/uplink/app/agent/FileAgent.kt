package com.uplink.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import org.json.JSONArray
import org.json.JSONObject

enum class AgentOp(val key: String, private val label: String, val icon: ImageVector) {
    CreateFile("create_file", "Create file", NxIcon.FilePlus),
    MakeFolder("mkdir", "New folder", NxIcon.FolderPlus),
    Rename("rename", "Rename", NxIcon.Rename),
    Copy("copy", "Copy", NxIcon.Copy),
    Delete("delete", "Delete", NxIcon.Trash),
    Zip("zip", "Zip", NxIcon.Archive),
    Unzip("unzip", "Unzip", NxIcon.Unarchive);

    val title: String get() = tr(label)

    companion object {
        fun of(key: String): AgentOp? = values().firstOrNull { it.key == key }
    }
}

enum class AgentStep { Waiting, Running, Done, Failed }

enum class AgentVerdict { None, Pending, Applied, Rejected }

enum class AgentRole { You, Ai, Note }

enum class AgentPhase(private val key: String, val icon: ImageVector) {
    Thinking("Thinking", NxIcon.Bot),
    Structure("Designing structure", NxIcon.Grid),
    Code("Writing code", NxIcon.Edit),
    Search("Searching files", NxIcon.Terminal);

    val label: String get() = tr(key)
}

class ConsoleLine(val command: String) {
    var output by mutableStateOf("")
    var state by mutableStateOf(AgentStep.Running)
    var open by mutableStateOf(false)
}

class AgentAction(
    val op: AgentOp,
    val path: String,
    val to: String,
    val content: String,
    val replaces: Boolean,
    val blocked: String?,
    val command: String = "",
) {
    var on by mutableStateOf(blocked == null)
    var step by mutableStateOf(AgentStep.Waiting)
    var error by mutableStateOf<String?>(null)

    val title: String
        get() = when {
            op == AgentOp.CreateFile && replaces -> tr("Overwrite file")
            op == AgentOp.Rename && to.isNotEmpty() && parentOf(path) != parentOf(to) -> tr("Move")
            else -> op.title
        }
    val risky: Boolean get() = op == AgentOp.Delete || replaces
}

class AgentTurn(val role: AgentRole, text: String) {
    var text by mutableStateOf(text)
    var thinking by mutableStateOf(false)
    var phase by mutableStateOf(AgentPhase.Thinking)
    var failed by mutableStateOf(false)
    var verdict by mutableStateOf(AgentVerdict.None)
    val actions = mutableStateListOf<AgentAction>()
    val console = mutableStateListOf<ConsoleLine>()
    val media = mutableStateListOf<AgentMedia>()
    val trail = ArrayList<AiMessage>()
    var raw = ""
    var thought by mutableStateOf("")
    var status by mutableStateOf("")
    var liveSay by mutableStateOf("")
    var liveOut by mutableStateOf("")
}

fun AiProfile.withSystem(text: String): AiProfile =
    AiProfile(id, name, api, baseUrl, apiKey, model, text, headers, body, models, pinned)
