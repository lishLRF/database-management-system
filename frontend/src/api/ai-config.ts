import request from './request'
import type { AiConfig } from '@/types'

export const aiConfigAPI = {
  getConfig: () =>
    request.get<{ success: boolean; config: AiConfig }>('/ai/config'),

  saveConfig: (data: {
    apiProvider: string
    apiBaseUrl: string
    modelName: string
    apiKey: string
    temperature: number
    maxTokens: number
  }) =>
    request.post('/ai/config', data),

  testConnection: () =>
    request.post('/ai/config/test'),

  generateSQL: (connectionId: number, naturalLanguage: string) =>
    request.post<{ success: boolean; sql: string }>('/ai/generate-sql', {
      connectionId,
      naturalLanguage
    }),

  fixSQL: (connectionId: number, originalSQL: string, errorMessage: string) =>
    request.post<{ success: boolean; sql: string }>('/ai/fix-sql', {
      connectionId,
      originalSQL,
      errorMessage
    }),

  explainSQL: (connectionId: number, sql: string) =>
    request.post<{ success: boolean; outputEffect: string; codeStructure: string }>('/ai/explain-sql', {
      connectionId,
      sql
    }),

  generateInsert: (connectionId: number, tableName: string, description: string) =>
    request.post<{ success: boolean; sql: string; columns: any[] }>('/ai/generate-insert', {
      connectionId,
      tableName,
      description
    }),

  generateUpdate: (connectionId: number, tableName: string, description: string, whereCondition?: string) =>
    request.post<{ success: boolean; sql: string; columns: any[] }>('/ai/generate-update', {
      connectionId,
      tableName,
      description,
      whereCondition
    }),

  generateUpdateRow: (connectionId: number, tableName: string, currentRow: Record<string,any>, pkColumn: string, pkValue: any, userIntent?: string) =>
    request.post<{ success: boolean; sql: string; setClause: string; tableName: string; pkColumn: string; pkValue: any; columns: any[] }>('/ai/generate-update-row', {
      connectionId,
      tableName,
      currentRow,
      pkColumn,
      pkValue,
      userIntent
    }),

  chat: (connectionId: number, message: string, tableName?: string, conversationId?: string) =>
    request.post<{ success: boolean; sql: string }>('/ai/chat', {
      connectionId,
      message,
      tableName,
      conversationId
    }),

  getProjectBackground: () =>
    request.get<{ success: boolean; background: string }>('/ai/project-background'),

  saveProjectBackground: (background: string) =>
    request.post('/ai/project-background', { background }),

  agentChat: (
    connectionId: number, message: string, tableName: string | undefined,
    conversationId: string | undefined,
    operationType?: string, intentType?: string,
    onEvent?: (type: string, data: any) => void,
    onError?: (err: Error) => void,
    onComplete?: (sql: string) => void
  ) => {
    const controller = new AbortController()
    fetch('/api/ai/agent-chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ connectionId, message, tableName, conversationId, operationType: operationType || 'normal', intentType: intentType || 'general' }),
      signal: controller.signal
    }).then(async (resp) => {
      if (!resp.ok) { onError(new Error(`HTTP ${resp.status}`)); return }
      const reader = resp.body?.getReader()
      if (!reader) { onError(new Error('No reader')); return }
      const decoder = new TextDecoder()
      let buffer = ''
      let eventType = ''
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
              if (eventType === 'agent_result') { onComplete(data.sql || ''); return }
              if (eventType === 'error') { onError(new Error(data.message || 'Agent error')); return }
              onEvent(eventType, data)
            } catch (e) {
              console.warn('[SSE parse]', eventType, line.slice(6).substring(0, 200), e)
            }
            eventType = ''
          }
        }
      }
      onError(new Error('Stream ended without result'))
    }).catch(onError)
    return controller
  },

  // ── Format repair & generation ──

  repairFormat: (
    format: string, content: string,
    onEvent: (t: string, d: any) => void, onError: (e: Error) => void, onComplete: () => void
  ) => {
    const ctrl = new AbortController()
    fetch('/api/ai/repair-format', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ format, content }),
      signal: ctrl.signal
    }).then(async (resp) => {
      if (!resp.ok) { onError(new Error(`HTTP ${resp.status}`)); return }
      const reader = resp.body?.getReader()
      if (!reader) { onError(new Error('No reader')); return }
      const decoder = new TextDecoder()
      let buffer = '', eventType = '', result = ''
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
              if (eventType === 'chunk') result += data.chunk || ''
              if (eventType === 'done') { onComplete(); return }
              if (eventType === 'error') { onError(new Error(data.message || 'Error')); return }
              onEvent(eventType, data)
            } catch {}
            eventType = ''
          }
        }
      }
    }).catch(onError)
    return ctrl
  },

  generateFormat: (
    format: string, description: string,
    onEvent: (t: string, d: any) => void, onError: (e: Error) => void, onComplete: () => void
  ) => {
    const ctrl = new AbortController()
    fetch('/api/ai/generate-format', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ format, description }),
      signal: ctrl.signal
    }).then(async (resp) => {
      if (!resp.ok) { onError(new Error(`HTTP ${resp.status}`)); return }
      const reader = resp.body?.getReader()
      if (!reader) { onError(new Error('No reader')); return }
      const decoder = new TextDecoder()
      let buffer = '', eventType = '', result = ''
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
              if (eventType === 'chunk') result += data.chunk || ''
              if (eventType === 'done') { onComplete(); return }
              if (eventType === 'error') { onError(new Error(data.message || 'Error')); return }
              onEvent(eventType, data)
            } catch {}
            eventType = ''
          }
        }
      }
      // done event already triggered onComplete above
    }).catch(onError)
    return ctrl
  },

  streamChat: (
    connectionId: number, message: string, tableName: string | undefined,
    conversationId: string | undefined,
    onEvent: (event: { type: string; data: any }) => void,
    onError: (err: Error) => void,
    onComplete: () => void
  ) => {
    const controller = new AbortController()
    fetch('/api/ai/stream-chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ connectionId, message, tableName, conversationId }),
      signal: controller.signal
    }).then(async (resp) => {
      if (!resp.ok) { onError(new Error(`HTTP ${resp.status}`)); return }
      const reader = resp.body?.getReader()
      if (!reader) { onError(new Error('No reader')); return }
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        let eventType = ''
        for (const line of lines) {
          if (line.startsWith('event: ')) eventType = line.slice(7).trim()
          else if (line.startsWith('data: ') && eventType) {
            try { onEvent({ type: eventType, data: JSON.parse(line.slice(6)) }) } catch {}
            eventType = ''
          }
        }
      }
      onComplete()
    }).catch(onError)
    return controller
  }
}
