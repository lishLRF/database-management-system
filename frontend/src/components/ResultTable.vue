<template>
  <div class="result-table">
    <div v-if="result" class="result-info">
      <span>执行时间: {{ result.executionTime }}ms</span>
      <span v-if="result.total">总行数: {{ result.total }}</span>
      <span v-if="result.affectedRows !== undefined">影响行数: {{ result.affectedRows }}</span>
      <span v-if="result?.columns && result?.rows?.length" style="margin-left:auto;display:flex;gap:6px">
        <el-button size="small" @click="exportCSV">📥 CSV</el-button>
        <el-button size="small" @click="exportExcel">📊 Excel</el-button>
        <el-button size="small" @click="exportAll" :loading="exportingAll" v-if="showPagination && result.total > result.rows.length">📦 导出全部</el-button>
      </span>
    </div>

    <el-table v-if="result?.columns" :data="result.rows" border stripe max-height="400">
      <el-table-column
        v-for="column in result.columns"
        :key="column"
        :label="column"
        min-width="120"
      >
        <template #default="{ row }">
          <ComplexTypePopover v-if="isComplexType(row[column])" :value="String(row[column])" :column-name="column" />
          <span v-else>{{ formatCell(row[column]) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="showPagination && result?.total"
      v-model:current-page="currentPage"
      v-model:page-size="currentPageSize"
      :page-sizes="[10, 50, 100, 500, 1000]"
      :total="result.total"
      layout="total, sizes, prev, pager, next"
      @size-change="handleSizeChange"
      @current-change="handlePageChange"
      style="margin-top: 16px"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import * as XLSX from 'xlsx'
import ComplexTypePopover from './ComplexTypePopover.vue'

function isComplexType(v: any): boolean {
  if (v === null || v === undefined) return false
  const s = String(v).trim()
  if (!s) return false
  return (s.startsWith('{') || s.startsWith('[') || s.startsWith('<') || (s.includes(':') && s.includes('\n')))
}

function formatCell(v: any): string {
  if (v === null || v === undefined) return '(NULL)'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function toSheetData(columns: string[], rows: Record<string, any>[]): any[][] {
  const data: any[][] = [columns]
  for (const row of rows) {
    data.push(columns.map(c => {
      const v = row[c]
      if (v === null || v === undefined) return ''
      if (typeof v === 'object') return JSON.stringify(v)
      return String(v)
    }))
  }
  return data
}

function exportCSV() {
  if (!props.result?.columns || !props.result?.rows?.length) return
  const data = toSheetData(props.result.columns, props.result.rows)
  const ws = XLSX.utils.aoa_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  XLSX.writeFile(wb, `query_export_${Date.now()}.csv`, { bookType: 'csv' })
}

function exportExcel() {
  if (!props.result?.columns || !props.result?.rows?.length) return
  const data = toSheetData(props.result.columns, props.result.rows)
  const ws = XLSX.utils.aoa_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  XLSX.writeFile(wb, `query_export_${Date.now()}.xlsx`)
}

function exportAll() {
  emit('exportAll')
}

const props = defineProps<{
  result: any
  showPagination?: boolean
  exportingAll?: boolean
}>()

const emit = defineEmits<{
  pageChange: [page: number, pageSize: number]
  exportAll: []
}>()

const currentPage = ref(1)
const currentPageSize = ref(100)

watch(() => props.result, () => {
  currentPage.value = 1
})

function handlePageChange(page: number) {
  emit('pageChange', page, currentPageSize.value)
}

function handleSizeChange(pageSize: number) {
  currentPage.value = 1
  emit('pageChange', 1, pageSize)
}
</script>

<style scoped>
.result-table {
  width: 100%;
}

.result-info {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
}
</style>
