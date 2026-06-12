import request from './request'

export interface ConversationInfo {
  conversationId: string
  title: string
  messageCount: number
  createdAt: string
}

export interface ChatMessage {
  id: number
  conversationId: string
  userId: string
  connectionId: number
  messageType: 'user' | 'ai'
  messageContent: string
  createdAt: string
}

export const conversationAPI = {
  listConversations: () =>
    request.get<{ success: boolean; conversations: ConversationInfo[] }>('/ai/conversations'),

  getMessages: (conversationId: string) =>
    request.get<{ success: boolean; messages: ChatMessage[] }>(`/ai/conversations/${conversationId}`),

  chat: (conversationId: string, connectionId: number, message: string) =>
    request.post<{ success: boolean; sql: string }>(`/ai/conversations/${conversationId}/chat`, {
      connectionId,
      message
    }),

  deleteConversation: (conversationId: string) =>
    request.delete(`/ai/conversations/${conversationId}`)
}
