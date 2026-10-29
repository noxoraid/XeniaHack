package com.uplink.app

enum class NxNeed(val label: String) {
    Anywhere("works on phone and VPS"),
    Vps("needs a connected VPS"),
    Internet("needs internet"),
    Browser("needs the in-app browser opened once"),
}

class NxEntry(val name: String, val usage: String, val summary: String, val need: NxNeed, val ready: Boolean)

object NxCatalog {
    val all: List<NxEntry> = listOf(
        NxEntry("help", "nx help", "list every nx command and what it needs", NxNeed.Anywhere, true),
        NxEntry("read", "nx read <path> [from] [to]", "file text, optional line range", NxNeed.Anywhere, true),
        NxEntry("ls", "nx ls [path] [-n]", "list a folder, files largest first with the total on top, folders not counted (use nx du), -n sorts by name", NxNeed.Anywhere, true),
        NxEntry("find", "nx find <glob> [path]", "find files by name", NxNeed.Anywhere, true),
        NxEntry("grep", "nx grep <text> [path]", "search text in files", NxNeed.Anywhere, true),
        NxEntry("sh", "nx sh <line>", "run a shell line on the current target", NxNeed.Anywhere, true),
        NxEntry("web", "nx web <url or search>", "open a page in the in-app browser", NxNeed.Internet, true),
        NxEntry("text", "nx text", "visible text of the open page", NxNeed.Browser, true),
        NxEntry("js", "nx js <code>", "run JavaScript on the open page, waits for the user", NxNeed.Browser, true),
        NxEntry("env", "nx env", "what this session can do", NxNeed.Anywhere, true),
        NxEntry("du", "nx du [path]", "recursive folder sizes, largest first", NxNeed.Anywhere, true),
        NxEntry("tree", "nx tree [path] [depth]", "folder tree with limits", NxNeed.Anywhere, true),
        NxEntry("stat", "nx stat <path>", "type, size, time, permissions", NxNeed.Anywhere, true),
        NxEntry("head", "nx head <path> [n]", "first lines of a file", NxNeed.Anywhere, true),
        NxEntry("tail", "nx tail <path> [n]", "last lines of a file", NxNeed.Anywhere, true),
        NxEntry("hash", "nx hash <path>", "SHA-256 of a file", NxNeed.Anywhere, true),
        NxEntry("diff", "nx diff <a> <b>", "compare two files", NxNeed.Anywhere, true),
        NxEntry("json", "nx json <path> [key]", "a value from a JSON file", NxNeed.Anywhere, true),
        NxEntry("write", "nx write <path> <text>", "write a short file from inline text (\\n is a new line), asks before replacing; long files go in the files list", NxNeed.Anywhere, true),
        NxEntry("edit", "nx edit <path> <old> <new>", "replace one exact piece of text, it must appear exactly once", NxNeed.Anywhere, true),
        NxEntry("mkdir", "nx mkdir <path>...", "create a folder", NxNeed.Anywhere, true),
        NxEntry("cp", "nx cp <src> <dst>", "copy, confirms before replacing", NxNeed.Anywhere, true),
        NxEntry("mv", "nx mv <src> <dst>", "move, confirms before replacing", NxNeed.Anywhere, true),
        NxEntry("rm", "nx rm [-r] <path>...", "delete after you confirm each name, no wildcards", NxNeed.Anywhere, true),
        NxEntry("zip", "nx zip <source> <target.zip>", "create a zip", NxNeed.Anywhere, true),
        NxEntry("unzip", "nx unzip <zip> [target]", "extract a zip safely", NxNeed.Anywhere, true),
        NxEntry("fetch", "nx fetch <url> [offset]", "download a public web page as text, a new domain waits for your approval", NxNeed.Internet, true),
        NxEntry("node", "nx node <file> [args]", "run a Node.js file on the server, 10 minute limit", NxNeed.Vps, true),
        NxEntry("py", "nx py <file> [args]", "run a Python file on the server, 10 minute limit", NxNeed.Vps, true),
        NxEntry("install", "nx install [apt|npm|pip] <package>...", "install packages on the server after you approve, apt by default", NxNeed.Vps, true),
    )

    fun readyNames(): List<String> = all.filter { it.ready }.map { it.name }
}
