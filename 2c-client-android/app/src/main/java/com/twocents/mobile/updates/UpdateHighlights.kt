package com.twocents.mobile.updates

/** Extract short change bullets, excluding release checksums and installation instructions. */
internal fun updateHighlights(body: String): String {
    val lines = body.lines()
    val explicit = lines.any { it.trim().equals("## In-app highlights", true) }
    var include = !explicit
    var fenced = false
    val highlights = mutableListOf<String>()
    for (raw in lines) {
        val line = raw.trim()
        if (line.startsWith(96.toChar().toString().repeat(3))) { fenced = !fenced; continue }
        if (fenced) continue
        if (line.startsWith("#")) {
            val heading = line.trimStart('#').trim().lowercase()
            include = if (explicit) heading == "in-app highlights" else
                listOf("what changed", "what's new", "features", "bug fixes", "fixes", "improvements", "changes")
                    .any { heading == it || heading.startsWith("$it ") }
            continue
        }
        if (!include || raw.startsWith("  ") || !line.matches(Regex("(?:[-*+] |\\d+[.)] ).+"))) continue
        var text = line.replace(Regex("^(?:[-*+] |\\d+[.)] )"), "")
            .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
            .replace("**", "").replace(96.toChar().toString(), "").trim()
        if (Regex("^(?:sha-?256|package|version|install|download|checksum|commit|full changelog)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) continue
        text = text.split(Regex("\\.\\s+|\\s+[—–]\\s+|;\\s+"), limit = 2).first().trimEnd('.')
        if (text.length > 115) text = text.take(112).substringBeforeLast(' ') + "…"
        if (text.isNotBlank() && text !in highlights) highlights += text
        if (highlights.size == 8) break
    }
    return highlights.joinToString("\n") { "• $it" }
}
