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

  saveMessage: (conversationId: string, connectionId: number, messageType: string, content: string) =>
    request.post<{ success: boolean }>(`/ai/conversations/${conversationId}/save`, {
      connectionId,
      messageType,
      content
    }),

  deleteConversation: (conversationId: string) =>
    request.delete(`/ai/conversations/${conversationId}`)
}
