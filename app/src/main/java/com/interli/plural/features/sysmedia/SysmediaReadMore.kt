package com.interli.plural.features.sysmedia

object SysmediaReadMore {
    const val MARKER = "<!--INTERLI_READ_MORE-->"

    fun split(content: String): Pair<String, String>? {
        val markerIndex = content.indexOf(MARKER)
        if (markerIndex == -1) return null

        val before = content.substring(0, markerIndex)
            .trimEnd('\n', ' ', '\t')

        val after = content.substring(markerIndex + MARKER.length)
            .trimStart('\n', ' ', '\t')

        if (after.isBlank()) return null
        return before to after
    }

    fun removeMarker(content: String): String =
        content.replace(MARKER, "").trim()
}