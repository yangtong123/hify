import http from './index'

export interface ChatSendRequest {
  agentId?: number
  sessionId?: number
  userId: string
  content: string
}

export interface ChatSessionResponse {
  id: number
  agentId: number
  title: string
  userId: string
  status: 'active' | 'archived'
  createdAt: string
  updatedAt: string
}

export interface ChatMessageResponse {
  id: number
  sessionId: number
  role: 'user' | 'assistant' | 'system' | 'tool'
  content: string
  tokenCount: number | null
  toolCalls: unknown
  metadata: Record<string, unknown> | null
  createdAt: string
}

export interface ChatCompletionResponse {
  session: ChatSessionResponse
  userMessage: ChatMessageResponse
  assistantMessage: ChatMessageResponse
}

export interface ChatStreamChunk {
  sessionId: number
  content: string | null
  finishReason: string | null
  done: boolean
}

export interface ChatSessionPage {
  records: ChatSessionResponse[]
  total: number
  page: number
  size: number
}

export async function sendChatMessage(data: ChatSendRequest): Promise<ChatCompletionResponse> {
  const res: any = await http.post('/chats/messages', data)
  return res.data
}

export async function getChatSessions(params: {
  userId: string
  agentId?: number
  status?: string
  page: number
  size: number
}): Promise<ChatSessionPage> {
  const res: any = await http.get('/chats', { params })
  return {
    records: res.data || [],
    total: res.total || 0,
    page: res.page || params.page,
    size: res.size || params.size,
  }
}

export async function getChatMessages(
  sessionId: number,
  params: { size: number; beforeCreatedAt?: string; beforeId?: number },
): Promise<ChatMessageResponse[]> {
  const res: any = await http.get(`/chats/${sessionId}/messages`, { params })
  return res.data || []
}

export function archiveChatSession(sessionId: number): Promise<void> {
  return http.delete(`/chats/${sessionId}`)
}

export async function streamChatMessage(
  data: ChatSendRequest,
  handlers: {
    onSession?: (session: ChatSessionResponse) => void
    onDelta?: (chunk: ChatStreamChunk) => void
    onDone?: (chunk: ChatStreamChunk) => void
    onComplete?: (response: ChatCompletionResponse) => void
    onError?: (message: string) => void
  },
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch('/api/chats/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(data),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`SSE 请求失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''
    for (const raw of events) {
      dispatchEventBlock(raw, handlers)
    }
  }
  if (buffer.trim()) {
    dispatchEventBlock(buffer, handlers)
  }
}

function dispatchEventBlock(
  block: string,
  handlers: {
    onSession?: (session: ChatSessionResponse) => void
    onDelta?: (chunk: ChatStreamChunk) => void
    onDone?: (chunk: ChatStreamChunk) => void
    onComplete?: (response: ChatCompletionResponse) => void
    onError?: (message: string) => void
  },
) {
  const lines = block.split(/\r?\n/)
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
  const dataLines = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trim())
  if (!eventName || dataLines.length === 0) {
    return
  }

  const payload = dataLines.join('\n')
  if (eventName === 'error') {
    handlers.onError?.(payload)
    throw new Error(payload || '流式响应失败')
  }

  const parsed = JSON.parse(payload)
  if (eventName === 'session') {
    handlers.onSession?.(parsed)
  } else if (eventName === 'delta') {
    handlers.onDelta?.(parsed)
    if (parsed.done) {
      handlers.onDone?.(parsed)
    }
  } else if (eventName === 'done') {
    handlers.onDone?.(parsed)
  } else if (eventName === 'complete') {
    handlers.onComplete?.(parsed)
  }
}
