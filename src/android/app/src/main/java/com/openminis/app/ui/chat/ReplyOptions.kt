package com.openminis.app.ui.chat

/**
 * [local #362] Reads a reply's own closing question and turns it into tappable
 * options.
 *
 * The point of the feature is that answering a "which of these?" turn should
 * not mean re-typing what the assistant just wrote. The cheapest correct way to
 * get the options is to read them OUT of the reply rather than ask a model to
 * restate them: no extra call, no latency added to the answer, and the options
 * are guaranteed to be the ones that were actually offered — a summarizer pass
 * can only paraphrase them into something the user then has to reconcile.
 *
 * Only the TAIL of the reply is considered, and only when it looks like a
 * choice being offered. Three shapes cover what models actually write:
 *
 *   "1. Do X\n2. Do Y\n3. Do Z"      -> numbered
 *   "- Do X\n- Do Y\n- Do Z"          -> bulleted
 *   "**A)** Do X\n**B)** Do Y"        -> lettered
 *
 * Guards, each of which exists because something plausible failed it:
 *  - at least 2 and at most 4 options — one option is not a choice, and a list
 *    of eight is a document, not a decision;
 *  - each option must be reasonably short — a paragraph is a section heading,
 *    not something to tap;
 *  - the run must be CONTIGUOUS and reach the end of the reply, so a numbered
 *    example embedded mid-answer never becomes a phantom menu;
 *  - a fenced code block or a table disqualifies the reply entirely: inside
 *    either, line-leading punctuation is structural, not a list.
 */
internal object ReplyOptions {

    data class Parsed(val options: List<String>)

    private const val MAX_OPTIONS = 4
    private const val MIN_OPTIONS = 2
    private const val MAX_OPTION_CHARS = 90
    /** How far back from the end the option run may start. */
    private const val MAX_TAIL_LINES = 24

    private val NUMBERED = Regex("^\\s*(\\d{1,2})[.)]\\s+(.+)$")
    private val BULLETED = Regex("^\\s*[-*•]\\s+(.+)$")
    private val LETTERED = Regex("^\\s*\\*?\\(?([A-Da-d])\\)?[.)]?\\*?\\s+(.+)$")

    /** True when the reply is code- or table-shaped; such replies never offer options. */
    private fun hasStructuralMarkup(text: String): Boolean {
        var inFence = false
        for (line in text.lines()) {
            val t = line.trimStart()
            if (t.startsWith("```") || t.startsWith("~~~")) inFence = !inFence
            if (inFence) return true
            // A Markdown table row is `| a | b |` or a `---` separator.
            if (t.startsWith("|") && t.endsWith("|") && t.length > 2) return true
        }
        return false
    }

    private fun matchLine(line: String): Pair<String, String>? {
        NUMBERED.matchEntire(line)?.let { return it.groupValues[2] to it.groupValues[1] }
        BULLETED.matchEntire(line)?.let { return it.groupValues[1] to "" }
        LETTERED.matchEntire(line)?.let { return it.groupValues[2] to it.groupValues[1] }
        return null
    }

    fun parse(reply: String): Parsed? {
        if (reply.isBlank()) return null
        if (hasStructuralMarkup(reply)) return null

        val lines = reply.lines()
        val tailStart = (lines.size - MAX_TAIL_LINES).coerceAtLeast(0)
        val tail = lines.subList(tailStart, lines.size)

        // Walk backwards from the final non-blank line, collecting the run of
        // option-shaped lines that REACHES it, then keep walking across the
        // single blank line that may separate them from the lead-in
        // ("Three ways to fix it:\n\n1. …\n2. …").
        //
        // The first cut stopped at the first blank line AND required the run to
        // be the very last thing in the reply. Both are too strict for how
        // replies are actually written — a lead-in line and a blank separator
        // is the common shape, so almost nothing ever matched.
        var i = tail.lastIndex
        while (i >= 0 && tail[i].isBlank()) i--
        if (i < 0) return null

        val collected = mutableListOf<String>()
        var sawSeparator = false
        while (i >= 0) {
            val line = tail[i]
            if (line.isBlank()) {
                // One blank line may sit INSIDE the options block (between the
                // lead-in and the list). A second one ends the search — at that
                // point the list is buried under prose, not closing the reply.
                if (collected.isEmpty() || sawSeparator) break
                sawSeparator = true
                i--
                continue
            }
            val m = matchLine(line) ?: break
            collected.add(0, m.first.trim())
            sawSeparator = false
            i--
        }
        if (collected.size < MIN_OPTIONS || collected.size > MAX_OPTIONS) return null

        // Every option must be tap-sized and none may repeat.
        if (collected.any { it.isEmpty() || it.length > MAX_OPTION_CHARS }) return null
        if (collected.distinct().size != collected.size) return null

        return Parsed(collected)
    }
}