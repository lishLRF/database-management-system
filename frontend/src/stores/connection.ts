import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Connection } from '@/types'
import { connectionAPI } from '@/api/connection'

export const useConnectionStore = defineStore('connection', () => {
  const connections = ref<Connection[]>([])
  const currentConnection = ref<Connection | null>(null)
  const currentSchema = ref<any>(null)
  const pendingSql = ref<string | null>(null)

  const fetchConnections = async () => {
    const data = await connectionAPI.getConnections()
    connections.value = (data as any).connections
  }

  const switchConnection = async (connectionId: number) => {
    const result = await connectionAPI.switchConnection(connectionId)
    const data = result as any
    // Use connection from switch response (backend now returns it)
    if (data.connection) {
      currentConnection.value = data.connection
    } else {
      currentConnection.value = connections.value.find(c => c.id === connectionId) || null
    }
    currentSchema.value = data.schema
    console.log('switchConnection result:', data)
    console.log('currentConnection set to:', currentConnection.value)
  }

  const deleteConnection = async (connectionId: number) => {
    await connectionAPI.deleteConnection(connectionId)
    connections.value = connections.value.filter(c => c.id !== connectionId)
  }

  const setPendingSql = (sql: string) => {
    pendingSql.value = sql
  }

  const takePendingSql = (): string | null => {
    const sql = pendingSql.value
    pendingSql.value = null
    return sql
  }

  return {
    connections,
    currentConnection,
    currentSchema,
    pendingSql,
    fetchConnections,
    switchConnection,
    deleteConnection,
    setPendingSql,
    takePendingSql,
    activeConnection: currentConnection,
    schema: currentSchema
  }
})
