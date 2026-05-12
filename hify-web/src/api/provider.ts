import http from './index'

/* ============================================================
   Types
   ============================================================ */

export interface AuthConfig {
  authType: string
  apiKey: string
  apiVersion?: string
  customHeaders?: Record<string, string>
}

export interface ProviderResponse {
  id: number
  name: string
  type: string
  baseUrl: string
  enabled: number
  authConfig: AuthConfig
  apiKey: string
  createdAt: string
  updatedAt: string
  healthStatus: string
  healthLatencyMs: number | null
  modelCount: number
}

export interface ProviderRequest {
  name: string
  type: string
  baseUrl: string
  enabled: number
  authConfig: AuthConfig | null
}

export interface ModelConfigResponse {
  id: number
  name: string
  modelId: string
  contextSize: number
  enabled: number
  extraParams: unknown
}

export interface HealthSummary {
  status: string
  lastCheckAt: string
  lastSuccessAt: string | null
  latencyMs: number | null
  failCount: number
}

export interface ProviderDetailResponse extends ProviderResponse {
  models: ModelConfigResponse[]
  health: HealthSummary
}

export interface ConnectionTestResult {
  success: boolean
  latencyMs: number
  modelCount: number
  errorMessage: string | null
}

/* ============================================================
   API methods — use async/await because the interceptor
   unwraps AxiosResponse, so .then() sees the raw body.
   ============================================================ */

export async function getProviderList(params: {
  type?: string
  enabled?: number
  page: number
  size: number
}): Promise<{ records: ProviderResponse[]; total: number; page: number; size: number }> {
  const res: any = await http.get('/providers', { params })
  return {
    records: res.data,
    total: res.total,
    page: res.page,
    size: res.size,
  }
}

export async function getProviderDetail(id: number): Promise<ProviderDetailResponse> {
  const res: any = await http.get(`/providers/${id}`)
  return res.data
}

export async function createProvider(data: ProviderRequest): Promise<ProviderResponse> {
  const res: any = await http.post('/providers', data)
  return res.data
}

export async function updateProvider(id: number, data: ProviderRequest): Promise<ProviderResponse> {
  const res: any = await http.put(`/providers/${id}`, data)
  return res.data
}

export function deleteProvider(id: number): Promise<void> {
  return http.delete(`/providers/${id}`)
}

export async function testConnection(id: number): Promise<ConnectionTestResult> {
  const res: any = await http.post(`/providers/${id}/test-connection`)
  return res.data
}
