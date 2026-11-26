package app.mittsu.domain.model

import java.time.Instant

data class WaniKaniUser(
    val username: String,
    val level: Int,
    val maxLevelGranted: Int,
    val subscriptionType: String,
    val subscriptionActive: Boolean,
    val vacationStartedAt: Instant?,
    val profileUrl: String?,
    val startedAt: Instant?,
) {
    val isLifetime: Boolean get() = subscriptionType == "lifetime"
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connected(val user: WaniKaniUser?) : ConnectionState
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class Running(val step: String) : SyncStatus
    data class Done(val at: Instant) : SyncStatus
    data class Failed(val message: String, val needsReconnect: Boolean) : SyncStatus
}

data class Meaning(val text: String, val primary: Boolean, val accepted: Boolean)
data class Reading(val text: String, val primary: Boolean, val accepted: Boolean, val type: String?)
data class ContextSentence(val en: String, val ja: String)
