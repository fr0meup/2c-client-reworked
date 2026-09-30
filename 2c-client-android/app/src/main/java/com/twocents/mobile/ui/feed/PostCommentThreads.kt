package com.twocents.mobile.ui.feed

internal const val CommentThreadLevels = 5

internal data class CommentThreadPage(
    val rows: List<FlatPostComment>,
    val moreReplies: Map<String, Int>,
    val ancestors: List<PostComment>,
)

/** Index subtree boundaries once so opening a deeper branch never scans unrelated threads. */
internal class CommentThreadIndex(private val rows: List<FlatPostComment>) {
    private val positions = rows.withIndex().associate { it.value.comment.uuid to it.index }
    private val ends = IntArray(rows.size) { rows.size }

    init {
        val stack = java.util.ArrayDeque<Int>()
        rows.forEachIndexed { index, row ->
            while (!stack.isEmpty()) {
                val previous = stack.peekLast() ?: break
                if (rows[previous].depth < row.depth) break
                ends[stack.removeLast()] = index
            }
            stack.addLast(index)
        }
    }

    fun page(anchorUuid: String? = null): CommentThreadPage {
        val start = anchorUuid?.let(positions::get) ?: if (anchorUuid == null) 0 else return CommentThreadPage(emptyList(), emptyMap(), emptyList())
        if (rows.isEmpty()) return CommentThreadPage(emptyList(), emptyMap(), emptyList())
        val baseDepth = if (anchorUuid == null) 0 else rows[start].depth
        val end = if (anchorUuid == null) rows.size else ends[start]
        val visible = mutableListOf<FlatPostComment>()
        val more = mutableMapOf<String, Int>()
        for (index in start until end) {
            val row = rows[index]
            val depth = row.depth - baseDepth
            if (depth >= CommentThreadLevels) continue
            visible += row.copy(depth = depth, ancestorContinuations = row.ancestorContinuations.takeLast(depth))
            if (depth == CommentThreadLevels - 1 && ends[index] > index + 1) {
                more[row.comment.uuid] = ends[index] - index - 1
            }
        }
        val parent = if (anchorUuid == null) null else rows[start].parent
        return CommentThreadPage(visible, more, listOfNotNull(parent))
    }

    /** Chunk boundaries overlap on the parent: each new sheet starts with that parent at depth zero. */
    fun pathTo(commentUuid: String): List<String> {
        var row = positions[commentUuid]?.let(rows::get) ?: return emptyList()
        val path = mutableListOf<String>()
        while (row.depth >= CommentThreadLevels) {
            val anchorDepth = ((row.depth - 1) / (CommentThreadLevels - 1)) * (CommentThreadLevels - 1)
            while (row.depth > anchorDepth) {
                row = row.parent?.uuid?.let(positions::get)?.let(rows::get) ?: return path.asReversed()
            }
            path += row.comment.uuid
        }
        return path.asReversed()
    }
}
