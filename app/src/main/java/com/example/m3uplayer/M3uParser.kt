package com.example.m3uplayer

data class Channel(val name: String, val url: String, val group: String)

object M3uParser {
    private fun attr(line: String, key: String): String =
        Regex("$key=\"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: ""

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var group = "Diğer"
        for (raw in text.lineSequence()) {
            val l = raw.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF")) {
                name = l.substringAfter(',', "").trim()
                group = attr(l, "group-title").ifEmpty { "Diğer" }
            } else if (!l.startsWith("#")) {
                out.add(Channel(name?.ifEmpty { l } ?: l, l, group))
                name = null
                group = "Diğer"
            }
        }
        return out
    }
}
