import request from './request'

export interface HistoryQueryParams {
  page?: number
  pageSize?: number
  search?: string
  sqlType?: string
  targetTable?: string
}

export const sqlHistoryAPI = {
  getHistory: (params?: HistoryQueryParams) =>
    request.get('/sql/history', { params }),

  deleteHistory: (id: number) =>
    request.delete(`/sql/history/${id}`),

  clearHistory: () =>
    request.delete('/sql/history/clear'),
}
