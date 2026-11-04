package app.duinch.agentremote.core.domain.model

enum class LeaseState {
    ACQUIRED,
    RELEASED,
    VERIFICATION_NEEDED
}

data class WriterLease(
    val writerAgentId: String,
    val revision: Long = 1,
    val state: LeaseState = LeaseState.ACQUIRED
)

data class ChildSessionBinding(
    val agentId: String,
    val engine: AgentType,
    val alias: String,
    val model: String = "",
    val nativeSessionId: String = "",
    val seenSeq: Long = 0,
    val isActiveWriter: Boolean = false
)

data class ConsultBranchModel(
    val branchId: String,
    val agentId: String,
    val engine: AgentType,
    val query: String,
    val response: String = "",
    val status: String = "COMPLETED"
)

data class ParentSession(
    val id: String,
    val title: String,
    val workspaceRoot: String,
    val writerLease: WriterLease,
    val children: List<ChildSessionBinding> = emptyList(),
    val branches: List<ConsultBranchModel> = emptyList(),
    val lastSeq: Long = 0
)
