package app.duinch.agentremote.core.data.mapper

import app.duinch.agentremote.core.data.dto.GatewayEventDto
import app.duinch.agentremote.core.domain.model.AgentState
import app.duinch.agentremote.core.domain.model.AgentType
import app.duinch.agentremote.core.domain.model.DomainGatewayEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GatewayEventMapperTest {

    @Test
    fun testMapSessionInit() {
        val dto = GatewayEventDto(
            type = "SESSION_INIT",
            sessionId = "s_123",
            agentType = "ANTIGRAVITY",
            status = "READY"
        )
        val event = GatewayEventMapper.mapToDomain(dto)

        assertNotNull(event)
        assertIs<DomainGatewayEvent.SessionInit>(event)
        assertEquals("s_123", event.sessionId)
        assertEquals(AgentType.ANTIGRAVITY, event.agentType)
    }

    @Test
    fun testMapApprovalRequired() {
        val dto = GatewayEventDto(
            type = "APPROVAL_REQUIRED",
            callId = "c_456",
            toolName = "run_command",
            summary = "Chạy git status",
            dangerous = false
        )
        val event = GatewayEventMapper.mapToDomain(dto)

        assertNotNull(event)
        assertIs<DomainGatewayEvent.ApprovalRequired>(event)
        assertEquals("c_456", event.request.callId)
        assertEquals("run_command", event.request.toolName)
        assertEquals("Chạy git status", event.request.summary)
    }

    @Test
    fun testMapAgentStateChanged() {
        val dto = GatewayEventDto(
            type = "AGENT_STATE",
            state = "WAITING_APPROVAL"
        )
        val event = GatewayEventMapper.mapToDomain(dto)

        assertNotNull(event)
        assertIs<DomainGatewayEvent.AgentStateChanged>(event)
        assertEquals(AgentState.WAITING_APPROVAL, event.state)
    }

    @Test
    fun testMapWriterTransferred() {
        val dto = GatewayEventDto(
            type = "WRITER_TRANSFERRED",
            sessionId = "ps_test",
            newWriter = "claude-agent-1",
            revision = 2L
        )
        val event = GatewayEventMapper.mapToDomain(dto)

        assertNotNull(event)
        assertIs<DomainGatewayEvent.WriterTransferred>(event)
        assertEquals("ps_test", event.sessionId)
        assertEquals("claude-agent-1", event.newWriter)
        assertEquals(2L, event.revision)
    }
}
