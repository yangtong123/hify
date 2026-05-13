import http from './index'

export interface McpServerResponse {
  id: number
  name: string
  description: string
  serverType: string
  enabled: number
}

export async function getMcpServers(params?: { enabled?: number }): Promise<McpServerResponse[]> {
  const res: any = await http.get('/mcp/servers', { params })
  return res.data
}
