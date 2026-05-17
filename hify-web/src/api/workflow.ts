import http from './index'

export interface WorkflowNodeRequest {
  nodeId: string
  nodeType: 'start' | 'llm' | 'knowledge' | 'condition' | 'tool' | 'end'
  name: string
  config?: Record<string, unknown> | null
  position?: Record<string, unknown> | null
}

export interface WorkflowEdgeRequest {
  sourceNodeId: string
  targetNodeId: string
  edgeType?: 'normal' | 'condition' | 'error'
  conditionExpression?: string | null
  priority?: number
}

export interface WorkflowRequest {
  name: string
  description?: string
  status?: string
  version?: number
  startNodeId: string
  config?: Record<string, unknown> | null
  nodes: WorkflowNodeRequest[]
  edges?: WorkflowEdgeRequest[]
}

export interface WorkflowListResponse {
  id: number
  name: string
  description: string
  status: string
  version: number
  startNodeId: string
  config: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export async function getWorkflowList(params: {
  name?: string
  status?: string
  page: number
  size: number
}): Promise<{ records: WorkflowListResponse[]; total: number; page: number; size: number }> {
  const res: any = await http.get('/workflows', { params })
  return {
    records: res.data,
    total: res.total,
    page: res.page,
    size: res.size,
  }
}

export async function createWorkflow(data: WorkflowRequest): Promise<WorkflowListResponse> {
  const res: any = await http.post('/workflows', data)
  return res.data
}

export function deleteWorkflow(id: number): Promise<void> {
  return http.delete(`/workflows/${id}`)
}
