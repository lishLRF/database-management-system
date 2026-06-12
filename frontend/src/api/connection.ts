import request from './request'
import type { Connection } from '@/types'

export interface CreateConnectionRequest {
  name: string
  dbType: 'postgresql' | 'sqlite'
  host?: string
  port?: number
  database: string
  username?: string
  password?: string
}

export const connectionAPI = {
  createConnection: (data: CreateConnectionRequest) =>
    request.post('/connections', data),

  getConnections: () =>
    request.get<{ connections: Connection[] }>('/connections'),

  testConnection: (id: number) =>
    request.post(`/connections/${id}/test`),

  deleteConnection: (id: number) =>
    request.delete(`/connections/${id}`),

  switchConnection: (id: number) =>
    request.post(`/connections/${id}/switch`)
}
