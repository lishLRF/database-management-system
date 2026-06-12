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
    })
}
