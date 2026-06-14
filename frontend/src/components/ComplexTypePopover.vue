<template>
  <el-popover placement="right" :width="480" trigger="click" :hide-after="0">
    <template #reference>
      <span class="complex-cell" :class="'cell-'+detectedType">{{ truncated }}</span>
    </template>
    <div class="complex-popover">
      <div class="popover-header">
        <el-tag :type="typeColor" size="small">{{ detectedType.toUpperCase() }}</el-tag>
        <span class="col-name">{{ columnName }}</span>
        <el-button link size="small" @click="copyToClipboard">📋 复制</el-button>
        <el-button link size="small" type="primary" @click="openInEditor">📝 在编辑器中打开</el-button>
      </div>
      <pre class="formatted-content">{{ formattedValue }}</pre>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useFormatStore } from '@/stores/formatStore'
import { ElMessage } from 'element-plus'

const props = defineProps<{ value: string; columnName: string }>()

const router = useRouter()
const formatStore = useFormatStore()

const detectedType = computed<'json' | 'xml' | 'yaml' | 'text'>(() => {
  const v = (props.value || '').trim()
  if (!v) return 'text'
  if (v.startsWith('{') || v.startsWith('[')) return 'json'
  if (v.startsWith('<')) return 'xml'
  if (v.includes(':') && v.includes('\n')) return 'yaml'
  return 'text'
})

const typeColor = computed(() => ({ json: 'success', xml: 'warning', yaml: 'info', text: '' }[detectedType.value]))

const formattedValue = computed(() => {
  const v = (props.value || '').trim()
  if (detectedType.value === 'json') {
    try { return JSON.stringify(JSON.parse(v), null, 2) } catch { return v }
  }
  return v
})

const truncated = computed(() => {
  const f = formattedValue.value
  return f.length > 60 ? f.substring(0, 60) + '...' : f
})

function copyToClipboard() {
  navigator.clipboard.writeText(formattedValue.value)
  ElMessage.success('已复制')
}

function openInEditor() {
  formatStore.setPending(formattedValue.value, detectedType.value as any)
  router.push('/format-editor')
}
</script>

<style scoped>
.complex-cell {
  cursor: pointer;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  padding: 2px 6px;
  border-radius: 3px;
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background 0.15s;
}
.cell-json { background: #e8f5e9; color: #2e7d32; }
.cell-json:hover { background: #c8e6c9; }
.cell-xml { background: #fff3e0; color: #e65100; }
.cell-xml:hover { background: #ffe0b2; }
.cell-yaml { background: #e3f2fd; color: #1565c0; }
.cell-yaml:hover { background: #bbdefb; }

.complex-popover { display: flex; flex-direction: column; }
.popover-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.col-name { font-size: 12px; color: #909399; margin-right: auto; }
.formatted-content { font-family: 'JetBrains Mono', monospace; font-size: 12px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; max-height: 400px; overflow-y: auto; background: #fafbfc; padding: 10px; border-radius: 6px; margin: 0; }
</style>
