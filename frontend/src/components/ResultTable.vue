<template>
  <div class="result-table">
    <div v-if="result" class="result-info">
      <span>执行时间: {{ result.executionTime }}ms</span>
      <span v-if="result.total">总行数: {{ result.total }}</span>
      <span v-if="result.affectedRows !== undefined">影响行数: {{ result.affectedRows }}</span>
    </div>

    <el-table v-if="result?.columns" :data="result.rows" border stripe max-height="400">
      <el-table-column
        v-for="column in result.columns"
        :key="column"
        :prop="column"
        :label="column"
        min-width="120"
      />
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

const props = defineProps<{
  result: any
  showPagination?: boolean
}>()

const emit = defineEmits<{
  pageChange: [page: number, pageSize: number]
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
