import request from './request'

export interface ExecuteSQLParams {
  connectionId: number
  sql: string
  confirmed?: boolean
  page?: number
  pageSize?: number
  source?: 'manual' | 'ai_generated' | 'human_ai_collab'
}

export const executeSQL = (params: ExecuteSQLParams) => {
  return request.post('/sql/execute', params)
}

export interface SqlHistoryItem {
  id: number
  sqlText: string
  sqlType: string
  targetTable: string
  source: string
  executionTime: number
  rowsAffected: number
  status: string
  errorMessage: string
  executedAt: string
}

export const getSqlHistory = (params?: { tableName?: string; sqlType?: string; limit?: number }) => {
  return request.get<SqlHistoryItem[]>('/sql/history', { params })
}

export const deleteSqlHistory = (id: number) => {
  return request.delete(`/sql/history/${id}`)
}

export const clearSqlHistory = () => {
  return request.delete('/sql/history/clear')
}

// ── Table Row Browsing (for 智能填表 → 行编辑 mode) ──

export const getColumnValues = (tableName: string, column: string, connectionId: number) => {
  return request.get<{ success: boolean; values: string[] }>(`/sql/table/${tableName}/column-values`, {
    params: { column, connectionId }
  })
}

export const searchRows = (tableName: string, connectionId: number, filters: { column: string; value: string }[]) => {
  return request.post<{ success: boolean; rows: Record<string,any>[]; columns: any[]; pkColumns: string[] }>(
    `/sql/table/${tableName}/rows`, { connectionId, filters }
  )
}

export const getRowByPk = (tableName: string, pkValue: string, connectionId: number) => {
  return request.get<{ success: boolean; row: Record<string,any> | null; pkColumn: string }>(
    `/sql/table/${tableName}/rows/${encodeURIComponent(pkValue)}`, { params: { connectionId } }
  )
}

export const explainSQL = (connectionId: number, sql: string) => {
  return request.post('/sql/explain', { connectionId, sql })
}

// ── SQL Snippets ──

export interface SqlSnippet {
  id: number
  userId: string
  title: string
  sqlContent: string
  description?: string
  tags?: string
  createdAt: string
  updatedAt: string
}

export const getSnippets = (search?: string) => {
  return request.get<SqlSnippet[]>('/sql-snippets', { params: search ? { search } : {} })
}

export const createSnippet = (data: { title: string; sqlContent: string; description?: string; tags?: string }) => {
  return request.post('/sql-snippets', data)
}

export const updateSnippet = (id: number, data: { title?: string; sqlContent?: string; description?: string; tags?: string }) => {
  return request.put(`/sql-snippets/${id}`, data)
}

export const deleteSnippet = (id: number) => {
  return request.delete(`/sql-snippets/${id}`)
}
