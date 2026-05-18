import http from './index'

export interface McpServerResponse {
  id: number
  name: string
  endpoint: string
  enabled: number
  toolCount: number
  createdAt: string
  updatedAt: string
}

export interface McpServerDto {
  id: number
  name: string
  description?: string
  serverType?: string
  enabled: number
}

export interface McpToolResponse {
  id: number
  mcpServerId: number
  name: string
  description: string | null
  inputSchema: Record<string, unknown>
  createdAt: string
  updatedAt: string
}

export interface McpServerDetailResponse extends McpServerResponse {
  tools: McpToolResponse[]
}

export interface McpServerRequest {
  name: string
  endpoint: string
  enabled: number
}

export interface McpConnectionTestResult {
  success: boolean
  message: string
  latencyMs: number
  toolCount: number
  tools: string[]
}

export interface McpToolDebugRequest {
  toolName: string
  arguments: Record<string, unknown>
}

export interface McpToolDebugResponse {
  result: string
  elapsedMs: number
}

export async function getMcpServerList(params: {
  name?: string
  enabled?: number
  page: number
  size: number
}): Promise<{ records: McpServerResponse[]; total: number; page: number; size: number }> {
  const res: any = await http.get('/mcp-servers', { params })
  return {
    records: res.data,
    total: res.total,
    page: res.page,
    size: res.size,
  }
}

export async function getMcpServerDetail(id: number): Promise<McpServerDetailResponse> {
  const res: any = await http.get(`/mcp-servers/${id}`)
  return res.data
}

export async function createMcpServer(data: McpServerRequest): Promise<McpServerResponse> {
  const res: any = await http.post('/mcp-servers', data)
  return res.data
}

export async function updateMcpServer(id: number, data: McpServerRequest): Promise<McpServerResponse> {
  const res: any = await http.put(`/mcp-servers/${id}`, data)
  return res.data
}

export function deleteMcpServer(id: number): Promise<void> {
  return http.delete(`/mcp-servers/${id}`)
}

export async function testMcpServer(id: number): Promise<McpConnectionTestResult> {
  const res: any = await http.post(`/mcp-servers/${id}/test`)
  return res.data
}

export async function debugMcpTool(id: number, data: McpToolDebugRequest): Promise<McpToolDebugResponse> {
  const res: any = await http.post(`/mcp-servers/${id}/debug`, data)
  return res.data
}

export async function getMcpServers(params?: { enabled?: number }): Promise<McpServerDto[]> {
  const res: any = await http.get('/mcp-servers/available', { params })
  return res.data
}
