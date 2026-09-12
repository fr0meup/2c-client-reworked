package com.twocents.mobile.ui.messages

private val explicitInviteCode = Regex("\\bgc-[a-z0-9]+\\b", RegexOption.IGNORE_CASE)
private val generatedGroupDescription = Regex("^group chat\\s+([a-z0-9]+)$", RegexOption.IGNORE_CASE)

/** Recover our generated code from server-persisted metadata after local data loss.
 * Do not infer from 'Group <suffix>' alone: ordinary DMs also use that name.
 * Explicit server/cached codes take precedence and retain their exact casing.
 */
internal fun resolveRoomInviteCode(code: String?, name: String, description: String): String? {
    code?.takeIf { it.isNotBlank() && !it.equals("null", true) }?.let { return it }
    val metadata = listOf(name, description)
    metadata.firstNotNullOfOrNull { explicitInviteCode.find(it)?.value }?.let { return it }
    val suffix = metadata.firstNotNullOfOrNull {
        generatedGroupDescription.matchEntire(it.trim())?.groupValues?.get(1)
    }
    return suffix?.let { "gc-${it.lowercase(java.util.Locale.ROOT)}" }
}
