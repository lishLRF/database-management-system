import request from './request'

export interface ExecuteSQLParams {
  connectionId: number
  sql: string
  confirmed?: boolean
  page?: number
  pageSize?: number
}

export const executeSQL = (params: ExecuteSQLParams) => {
  return request.post('/sql/execute', params)
}
