import http from './index'

export interface KnowledgeBaseResponse {
  id: number
  name: string
  description: string
  embeddingModelConfigId: number
  embeddingModelName: string | null
  embeddingDimension: number
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
  status: string
  documentCount: number | null
  createdAt: string
  updatedAt: string
}

export interface KnowledgeBaseRequest {
  name: string
  description: string
  embeddingModelConfigId: number
  embeddingDimension: number
  chunkSize: number
  chunkOverlap: number
  topK: number
  similarityThreshold: number
  status: string
}

export interface KnowledgeDocumentResponse {
  id: number
  knowledgeBaseId: number
  fileName: string
  fileType: string
  fileSize: number
  title: string
  processStatus: string
  chunkCount: number
  errorMessage: string | null
  processedAt: string | null
  createdAt: string
}

export interface RetrievedChunkDto {
  chunkId: number
  knowledgeBaseId: number
  documentId: number
  documentTitle: string | null
  fileName: string | null
  chunkIndex: number
  content: string
  tokenCount: number
  pageNumber: number | null
  sectionTitle: string | null
  distance: number
  similarity: number
}

export async function getKnowledgeBase(id: number): Promise<KnowledgeBaseResponse> {
  const res: any = await http.get(`/knowledge-bases/${id}`)
  return res.data
}

export async function getKnowledgeBaseList(params: {
  name?: string
  status?: string
  page: number
  size: number
}): Promise<{ records: KnowledgeBaseResponse[]; total: number; page: number; size: number }> {
  const res: any = await http.get('/knowledge-bases', { params })
  return {
    records: res.data,
    total: res.total,
    page: res.page,
    size: res.size,
  }
}

export async function createKnowledgeBase(data: KnowledgeBaseRequest): Promise<KnowledgeBaseResponse> {
  const res: any = await http.post('/knowledge-bases', data)
  return res.data
}

export async function updateKnowledgeBase(id: number, data: KnowledgeBaseRequest): Promise<KnowledgeBaseResponse> {
  const res: any = await http.put(`/knowledge-bases/${id}`, data)
  return res.data
}

export function deleteKnowledgeBase(id: number): Promise<void> {
  return http.delete(`/knowledge-bases/${id}`)
}

export async function listKnowledgeDocuments(knowledgeBaseId: number): Promise<KnowledgeDocumentResponse[]> {
  const res: any = await http.get(`/knowledge-bases/${knowledgeBaseId}/documents`)
  return res.data
}

export async function getKnowledgeDocument(documentId: number): Promise<KnowledgeDocumentResponse> {
  const res: any = await http.get(`/documents/${documentId}`)
  return res.data
}

export async function uploadKnowledgeDocument(knowledgeBaseId: number, file: File): Promise<KnowledgeDocumentResponse> {
  const form = new FormData()
  form.append('file', file)
  const res: any = await http.post(`/knowledge-bases/${knowledgeBaseId}/documents`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
  })
  return res.data
}

export function deleteKnowledgeDocument(documentId: number): Promise<void> {
  return http.delete(`/documents/${documentId}`)
}

export async function listKnowledgeDocumentChunks(documentId: number): Promise<RetrievedChunkDto[]> {
  const res: any = await http.get(`/documents/${documentId}/chunks`)
  return res.data
}
