<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatLineRound, Delete, Plus, Promotion, Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getAgentList, type AgentListResponse } from '@/api/agent'
import {
  archiveChatSession,
  getChatMessages,
  getChatSessions,
  sendChatMessage,
  streamChatMessage,
  type ChatMessageResponse,
  type ChatSessionResponse,
} from '@/api/chat'

const userId = ref('local-user')
const agents = ref<AgentListResponse[]>([])
const selectedAgentId = ref<number | null>(null)
const sessions = ref<ChatSessionResponse[]>([])
const activeSession = ref<ChatSessionResponse | null>(null)
const messages = ref<ChatMessageResponse[]>([])
const input = ref('')
const useStream = ref(true)
const loadingAgents = ref(false)
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)

const selectedAgent = computed(() =>
  agents.value.find((agent) => agent.id === selectedAgentId.value) || null,
)

const canSend = computed(() =>
  input.value.trim().length > 0 && userId.value.trim().length > 0 && selectedAgentId.value != null && !sending.value,
)

onMounted(async () => {
  await loadAgents()
  await refreshSessions()
})

async function loadAgents() {
  loadingAgents.value = true
  try {
    const result = await getAgentList({ enabled: 1, page: 1, size: 100 })
    agents.value = result.records
    if (selectedAgentId.value == null && result.records.length > 0) {
      selectedAgentId.value = result.records[0].id
    }
  } finally {
    loadingAgents.value = false
  }
}

async function refreshSessions() {
  if (!userId.value.trim()) {
    sessions.value = []
    activeSession.value = null
    messages.value = []
    return
  }
  loadingSessions.value = true
  try {
    const result = await getChatSessions({
      userId: userId.value.trim(),
      status: 'active',
      page: 1,
      size: 30,
    })
    sessions.value = result.records
    if (activeSession.value) {
      const stillActive = sessions.value.find((session) => session.id === activeSession.value?.id)
      if (stillActive) {
        activeSession.value = stillActive
        return
      }
    }
    if (sessions.value.length > 0) {
      await selectSession(sessions.value[0])
    } else {
      newChat()
    }
  } finally {
    loadingSessions.value = false
  }
}

async function selectSession(session: ChatSessionResponse) {
  activeSession.value = session
  selectedAgentId.value = session.agentId
  loadingMessages.value = true
  try {
    const history = await getChatMessages(session.id, { size: 50 })
    messages.value = history.slice().reverse()
  } finally {
    loadingMessages.value = false
  }
}

function newChat() {
  activeSession.value = null
  messages.value = []
}

async function send() {
  const content = input.value.trim()
  if (!canSend.value || selectedAgentId.value == null) {
    return
  }
  input.value = ''
  sending.value = true

  const request = {
    agentId: selectedAgentId.value,
    sessionId: activeSession.value?.id,
    userId: userId.value.trim(),
    content,
  }

  try {
    if (useStream.value) {
      await sendStream(request)
    } else {
      const completion = await sendChatMessage(request)
      activeSession.value = completion.session
      upsertSession(completion.session)
      messages.value.push(completion.userMessage, completion.assistantMessage)
    }
    await refreshSessionListOnly()
  } catch (e: any) {
    ElMessage.error(e.message || '发送失败')
    input.value = content
  } finally {
    sending.value = false
  }
}

async function sendStream(request: {
  agentId: number
  sessionId?: number
  userId: string
  content: string
}) {
  const userMessage = tempMessage('user', request.content)
  const assistantMessage = tempMessage('assistant', '')
  messages.value.push(userMessage, assistantMessage)

  await streamChatMessage(request, {
    onSession(session) {
      activeSession.value = session
      upsertSession(session)
    },
    onDelta(chunk) {
      if (chunk.content) {
        assistantMessage.content += chunk.content
      }
    },
    onComplete(completion) {
      activeSession.value = completion.session
      upsertSession(completion.session)
      const userIndex = messages.value.findIndex((message) => message.id === userMessage.id)
      const assistantIndex = messages.value.findIndex((message) => message.id === assistantMessage.id)
      if (userIndex >= 0) {
        messages.value[userIndex] = completion.userMessage
      }
      if (assistantIndex >= 0) {
        messages.value[assistantIndex] = completion.assistantMessage
      }
    },
    onError(message) {
      ElMessage.error(message || '流式响应失败')
    },
  })
}

async function refreshSessionListOnly() {
  const result = await getChatSessions({
    userId: userId.value.trim(),
    status: 'active',
    page: 1,
    size: 30,
  })
  sessions.value = result.records
}

async function archiveActiveSession() {
  if (!activeSession.value) {
    return
  }
  await archiveChatSession(activeSession.value.id)
  ElMessage.success('会话已归档')
  newChat()
  await refreshSessions()
}

function upsertSession(session: ChatSessionResponse) {
  const index = sessions.value.findIndex((item) => item.id === session.id)
  if (index >= 0) {
    sessions.value[index] = session
  } else {
    sessions.value.unshift(session)
  }
}

function tempMessage(role: 'user' | 'assistant', content: string): ChatMessageResponse {
  return {
    id: -Date.now() - Math.floor(Math.random() * 1000),
    sessionId: activeSession.value?.id || 0,
    role,
    content,
    tokenCount: null,
    toolCalls: null,
    metadata: null,
    createdAt: new Date().toISOString(),
  }
}

function agentLabel(agent: AgentListResponse) {
  return `${agent.name} · ${agent.providerName || '-'} / ${agent.modelName || '-'}`
}

function sessionTime(session: ChatSessionResponse) {
  return new Date(session.updatedAt || session.createdAt).toLocaleString()
}
</script>

<template>
  <PageHeader title="对话" description="多轮对话、历史记录与 SSE 流式响应">
    <template #actions>
      <button class="btn btn-secondary" @click="refreshSessions">
        <el-icon><Refresh /></el-icon>
        刷新
      </button>
      <button class="btn btn-primary" @click="newChat">
        <el-icon><Plus /></el-icon>
        新建对话
      </button>
    </template>
  </PageHeader>

  <div class="chat-workspace">
    <aside class="session-panel">
      <div class="panel-controls">
        <el-input v-model="userId" size="small" placeholder="用户标识" @change="refreshSessions" />
        <el-select
          v-model="selectedAgentId"
          size="small"
          filterable
          placeholder="选择 Agent"
          :loading="loadingAgents"
        >
          <el-option
            v-for="agent in agents"
            :key="agent.id"
            :label="agentLabel(agent)"
            :value="agent.id"
          />
        </el-select>
      </div>

      <div v-loading="loadingSessions" class="session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: activeSession?.id === session.id }"
          @click="selectSession(session)"
        >
          <span>{{ session.title || '未命名会话' }}</span>
          <small>{{ sessionTime(session) }}</small>
        </button>
        <div v-if="sessions.length === 0" class="empty-state">
          <el-icon><ChatLineRound /></el-icon>
          <span>暂无历史会话</span>
        </div>
      </div>
    </aside>

    <section class="conversation-panel">
      <div class="conversation-header">
        <div>
          <h2>{{ activeSession?.title || '新对话' }}</h2>
          <p>{{ selectedAgent ? agentLabel(selectedAgent) : '请选择 Agent' }}</p>
        </div>
        <div class="conversation-actions">
          <el-switch v-model="useStream" active-text="SSE" inactive-text="普通" />
          <el-button
            :icon="Delete"
            size="small"
            :disabled="!activeSession"
            @click="archiveActiveSession"
          />
        </div>
      </div>

      <div v-loading="loadingMessages" class="message-list">
        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="message-bubble">
            <strong>{{ message.role === 'user' ? 'User' : 'Assistant' }}</strong>
            <p>{{ message.content }}</p>
            <small v-if="message.tokenCount">tokens {{ message.tokenCount }}</small>
          </div>
        </div>
        <div v-if="messages.length === 0" class="empty-conversation">
          <el-icon><ChatLineRound /></el-icon>
          <span>选择历史会话或发送第一条消息</span>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入消息"
          :disabled="sending"
          @keydown.meta.enter.prevent="send"
          @keydown.ctrl.enter.prevent="send"
        />
        <button class="btn btn-primary send-button" :disabled="!canSend" @click="send">
          <el-icon><Promotion /></el-icon>
          发送
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chat-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: var(--space-4);
  min-height: calc(100vh - 180px);
}

.session-panel,
.conversation-panel {
  min-height: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.session-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-controls {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border-bottom: 1px solid var(--border-light);
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--space-2);
}

.session-item {
  width: 100%;
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-primary);
  text-align: left;
  cursor: pointer;
}

.session-item:hover {
  background: var(--bg-surface-hover);
}

.session-item.active {
  background: var(--color-primary-light);
  border-color: var(--hify-color-primary-200);
}

.session-item span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-weight: 600;
}

.session-item small,
.conversation-header p,
.message-bubble small {
  color: var(--text-tertiary);
}

.conversation-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  overflow: hidden;
}

.conversation-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4);
  border-bottom: 1px solid var(--border-light);
}

.conversation-header h2 {
  font-size: var(--text-lg);
  line-height: var(--leading-tight);
}

.conversation-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.message-list {
  overflow-y: auto;
  padding: var(--space-4);
  background: var(--hify-gray-50);
}

.message-row {
  display: flex;
  margin-bottom: var(--space-3);
}

.message-row.user {
  justify-content: flex-end;
}

.message-bubble {
  max-width: min(720px, 78%);
  padding: var(--space-3);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
  box-shadow: var(--shadow-xs);
}

.message-row.user .message-bubble {
  color: #fff;
  background: var(--color-primary);
  border-color: var(--color-primary);
}

.message-row.user .message-bubble small {
  color: rgba(255, 255, 255, 0.7);
}

.message-bubble strong {
  display: block;
  margin-bottom: 4px;
  font-size: var(--text-xs);
  letter-spacing: 0;
}

.message-bubble p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--space-3);
  align-items: end;
  padding: var(--space-4);
  border-top: 1px solid var(--border-light);
}

.send-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.empty-state,
.empty-conversation {
  display: grid;
  place-items: center;
  align-content: center;
  gap: var(--space-2);
  min-height: 180px;
  color: var(--text-tertiary);
}

.empty-conversation {
  height: 100%;
}

@media (max-width: 900px) {
  .chat-workspace {
    grid-template-columns: 1fr;
  }

  .session-panel {
    max-height: 300px;
  }

  .composer {
    grid-template-columns: 1fr;
  }

  .send-button {
    width: 100%;
  }
}
</style>
