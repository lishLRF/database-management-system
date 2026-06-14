import axios from 'axios'

export interface MdDocumentInfo {
  id: number
  fileName: string
  fileSize: number
  status: string
  aiSummary?: string
  createdAt: string
}

export interface MdChunkInfo {
  id: number
  documentId: number
  chunkIndex: number
  chunkType: 'structured_data' | 'semantic_content'
  chunkTitle: string
  contentStartPos: number
  contentEndPos: number
  chunkContent: string
  classificationReason: string
  humanModified: boolean
}

// ── SSE helpers ──

function parseSSE<T>(
  url: string, body: any,
  onEvent: (type: string, data: any) => void,
  onError: (err: Error) => void,
  onComplete: (result: T) => void
): AbortController {
  const ctrl = new AbortController()
  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal: ctrl.signal
  }).then(async resp => {
    if (!resp.ok) { onError(new Error(`HTTP ${resp.status}`)); return }
    const reader = resp.body?.getReader()
    if (!reader) { onError(new Error('No reader')); return }
    const decoder = new TextDecoder()
    let buffer = '', eventType = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('event: ')) eventType = line.slice(7).trim()
        else if (line.startsWith('data: ') && eventType) {
          try {
            const data = JSON.parse(line.slice(6))
            if (eventType === 'done') { onComplete(data as T); return }
            if (eventType === 'error') { onError(new Error(data?.message || data)); return }
            onEvent(eventType, data)
          } catch { /* skip parse errors */ }
          eventType = ''
        }
      }
    }
    onError(new Error('Stream ended without result'))
  }).catch(onError)
  return ctrl
}

// ── API ──

export const markdownAPI = {
  upload: (file: File, onProgress?: (p: number) => void): Promise<MdDocumentInfo> => {
    const fd = new FormData()
    fd.append('file', file)
    return axios.post('/api/markdown/upload', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e: any) => { if (e.total && onProgress) onProgress(Math.round((e.loaded / e.total) * 100)) },
      timeout: 30000
    }).then(r => r.data)
  },

  getDocuments: (): Promise<{ documents: MdDocumentInfo[] }> =>
    axios.get('/api/markdown/documents').then(r => r.data),

  getDocument: (id: number): Promise<MdDocumentInfo & { fullContent: string }> =>
    axios.get(`/api/markdown/documents/${id}`).then(r => r.data),

  deleteDocument: (id: number): Promise<any> =>
    axios.delete(`/api/markdown/documents/${id}`),

  // SSE: summarize
  summarize: (id: number, onEvent: (t: string, d: any) => void, onError: (e: Error) => void, onComplete: (r: any) => void) =>
    parseSSE('/api/markdown/summarize', { id }, onEvent, onError, onComplete),

  // SSE: chunk
  chunk: (id: number, tableName: string | null, onEvent: (t: string, d: any) => void, onError: (e: Error) => void, onComplete: (r: any) => void) =>
    parseSSE('/api/markdown/chunk', { id, tableName }, onEvent, onError, onComplete),

  slice: (id: number, strategy: any[]): Promise<{ structuredFile: string; semanticFile: string; structuredCount: number; semanticCount: number }> =>
    axios.post('/api/markdown/slice', { id, strategy }).then(r => r.data),

  confirm: (id: number, strategy: any[]): Promise<{ chunks: any[] }> =>
    axios.post('/api/markdown/confirm', { id, strategy }).then(r => r.data),

  feedback: (id: number, tableName: string | null, strategy: any[], feedbackText: string,
    onEvent: (t: string, d: any) => void, onError: (e: Error) => void, onComplete: (r: any) => void) =>
    parseSSE('/api/markdown/feedback', { id, tableName, strategy, feedback: feedbackText }, onEvent, onError, onComplete),

  export: (id: number): Promise<{ structuredFile: string; semanticFile: string; structuredFileName: string; semanticFileName: string }> =>
    axios.post('/api/markdown/export', { id }).then(r => r.data),

  getChunks: (docId: number): Promise<{ chunks: MdChunkInfo[] }> =>
    axios.get(`/api/markdown/chunks/${docId}`).then(r => r.data),

  updateChunk: (chunkId: number, chunkType: string, chunkTitle: string): Promise<any> =>
    axios.put(`/api/markdown/chunks/${chunkId}`, { chunkType, chunkTitle })
}
