import { defineStore } from 'pinia'
import { ref } from 'vue'

/** Lightweight store for passing structured data between pages */
export const useFormatStore = defineStore('format', () => {
  const pendingData = ref<string | null>(null)
  const pendingFormat = ref<'json' | 'xml' | 'yaml' | null>(null)

  function setPending(data: string, format: 'json' | 'xml' | 'yaml') {
    pendingData.value = data
    pendingFormat.value = format
  }

  function takePending(): { data: string; format: 'json' | 'xml' | 'yaml' } | null {
    if (!pendingData.value) return null
    const r = { data: pendingData.value, format: pendingFormat.value! }
    pendingData.value = null
    pendingFormat.value = null
    return r
  }

  return { pendingData, pendingFormat, setPending, takePending }
})
