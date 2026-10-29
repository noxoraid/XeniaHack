package com.uplink.app

fun agentGuard(
    fs: FsBackend, base: String, op: AgentOp, path: String, to: String, content: String, replace: Boolean = false,
): Pair<String?, Boolean> {
    val root = base.trimEnd('/')
    if (isLocal(fs)) {
        if (path != root && !path.startsWith("$root/")) return tr("outside storage") to false
        if (to.isNotEmpty() && !to.startsWith("$root/")) return tr("outside storage") to false
    }
    val destructive = op == AgentOp.Delete || op == AgentOp.Rename
    if (destructive && (path == "/" || path == root || (!isLocal(fs) && parentOf(path) == "/"))) {
        return tr("protected path") to false
    }
    if (isLocal(fs) && destructive) DeletePolicy.refusal(path)?.let { return refusalText(it, path) to false }
    val here = fs.kind(path)
    return when (op) {
        AgentOp.CreateFile -> when {
            content.length > 1_000_000 -> tr("content too large") to false
            here == 2 -> tr("a folder with this name exists") to false
            else -> null to (here == 1)
        }
        AgentOp.MakeFolder -> if (here != 0) tr("already exists") to false else null to false
        AgentOp.Delete -> if (here == 0) tr("not found") to false else null to false
        AgentOp.Rename, AgentOp.Copy -> when {
            to.isEmpty() -> tr("missing target") to false
            here == 0 -> tr("not found") to false
            fs.kind(to) != 0 && !replace -> tr("target already exists") to false
            else -> null to (replace && fs.kind(to) == 1)
        }
        AgentOp.Zip -> when {
            here == 0 -> tr("not found") to false
            fs.kind(to) != 0 -> tr("target already exists") to false
            else -> null to false
        }
        AgentOp.Unzip -> when {
            here != 1 -> tr("not found") to false
            !path.lowercase().endsWith(".zip") -> tr("Not a zip file") to false
            else -> null to false
        }
    }
}

fun refusalText(why: DeleteRefusal, path: String): String = when (why) {
    DeleteRefusal.FilesystemRoot -> tr("Refused: the filesystem root cannot be deleted or moved")
    DeleteRefusal.StorageRoot -> tr("Refused: the storage root cannot be deleted or moved")
    DeleteRefusal.MediaFolder -> tr("Refused: top-level folder {0} cannot be deleted or moved", path.trimEnd('/').substringAfterLast('/'))
}

fun agentPerform(fs: FsBackend, a: AgentAction) {
    when (a.op) {
        AgentOp.CreateFile -> {
            val here = fs.kind(a.path)
            if (here == 2 || (here == 1 && !a.replaces)) throw Exception(tr("Name already exists"))
            fs.create(a.path).use { it.write(a.content.toByteArray(Charsets.UTF_8)) }
        }
        AgentOp.MakeFolder -> fs.mkdirs(a.path)
        AgentOp.Rename -> {
            if (fs.kind(a.to) != 0) throw Exception(tr("Name already exists"))
            fs.mkdirs(parentOf(a.to))
            fs.rename(a.path, a.to)
        }
        AgentOp.Copy -> {
            if (fs.kind(a.to) != 0) throw Exception(tr("Name already exists"))
            copyTree(fs, a.path, fs, a.to) { }
        }
        AgentOp.Delete -> fs.delete(a.path)
        AgentOp.Zip -> zipItem(fs, parentOf(a.path), a.path.substringAfterLast('/'))
        AgentOp.Unzip -> unzipItem(fs, parentOf(a.path), a.path.substringAfterLast('/'))
    }
}
