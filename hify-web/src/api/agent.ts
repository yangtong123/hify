import http from './index'

export interface AgentConfig {
  openingMessage?: string
  suggestedQuestions?: string[]
  maxIterations?: number
}

export interface AgentListResponse {
  id: number
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number
  modelName: string
  providerName: string
  temperature: number
  maxTokens: number
  topP: number
  maxContextTurns: number
  configJson: AgentConfig | null
  enabled: number
  mcpServerCount: number
  createdAt: string
  updatedAt: string
}

export interface AgentModelInfo {
  id: number
  name: string
  providerName: string
  contextSize: number
}

export interface AgentMcpServerInfo {
  id: number
  name: string
  serverType: string
  isEnabled: boolean
}

export interface AgentDetailResponse extends AgentListResponse {
  model: AgentModelInfo | null
  mcpServers: AgentMcpServerInfo[]
}

export interface AgentRequest {
  name: string
  description: string
  systemPrompt: string
  modelConfigId: number
  temperature: number
  maxTokens: number
  topP: number
  maxContextTurns: number
  configJson: AgentConfig | null
  enabled: number
  mcpServerIds: number[]
}

export interface AgentToolsRequest {
  mcpServerIds: number[]
}

export async function getAgentList(params: {
  name?: string
  enabled?: number
  page: number
  size: number
}): Promise<{ records: AgentListResponse[]; total: number; page: number; size: number }> {
  const res: any = await http.get('/agents', { params })
  return {
    records: res.data,
    total: res.total,
    page: res.page,
    size: res.size,
  }
}

export async function getAgentDetail(id: number): Promise<AgentDetailResponse> {
  const res: any = await http.get(`/agents/${id}`)
  return res.data
}

export async function createAgent(data: AgentRequest): Promise<AgentDetailResponse> {
  const res: any = await http.post('/agents', data)
  return res.data
}

export async function updateAgent(id: number, data: AgentRequest): Promise<AgentDetailResponse> {
  const res: any = await http.put(`/agents/${id}`, data)
  return res.data
}

export function deleteAgent(id: number): Promise<void> {
  return http.delete(`/agents/${id}`)
}

export function updateAgentTools(id: number, data: AgentToolsRequest): Promise<void> {
  return http.put(`/agents/${id}/tools`, data)
}
