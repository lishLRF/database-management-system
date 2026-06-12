export interface Connection {
  id: number
  name: string
  dbType: 'postgresql' | 'sqlite'
  host?: string
  port?: number
  database: string
  username?: string
  isActive: boolean
  createdAt: string
}

export interface QueryResult {
  columns: string[]
  rows: any[][]
  total: number
  executionTime: number
  rowsAffected?: number
}

export interface AIConfig {
  apiProvider: string
  apiBaseUrl: string
  modelName: string
  temperature: number
  maxTokens: number
}
