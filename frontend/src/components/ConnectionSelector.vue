<template>
  <el-select v-model="selectedId" placeholder="选择连接" @change="handleChange" style="width: 250px">
    <el-option
      v-for="conn in connections"
      :key="conn.id"
      :label="conn.name"
      :value="conn.id"
    >
      <span>{{ conn.name }}</span>
      <el-tag v-if="conn.isActive" size="small" type="success" style="margin-left: 10px">当前</el-tag>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useConnectionStore } from '@/stores/connection'

const store = useConnectionStore()
const selectedId = ref<number | null>(null)

const connections = computed(() => store.connections)

const handleChange = async (id: number) => {
  try {
    await store.switchConnection(id)
    ElMessage.success('切换成功')
  } catch (error) {
    ElMessage.error('切换失败')
  }
}

onMounted(async () => {
  await store.fetchConnections()
  const active = connections.value.find(c => c.isActive)
  if (active) selectedId.value = active.id
})
</script>
