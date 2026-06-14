<template>
  <div class="app-layout">
    <el-container style="height: 100%">
      <el-header class="app-header">
        <div class="logo">🗄️ DB Manager</div>
        <el-menu
          mode="horizontal"
          :default-active="activeRoute"
          router
          class="nav-menu"
        >
          <el-menu-item index="/">首页</el-menu-item>
          <el-menu-item index="/connections">连接管理</el-menu-item>
          <el-menu-item index="/sql">SQL工作台</el-menu-item>
          <el-menu-item index="/markdown">Markdown解析</el-menu-item>
          <el-menu-item index="/format-editor">半结构化编辑</el-menu-item>
          <el-menu-item index="/ai-config">AI配置</el-menu-item>
        </el-menu>
        <div class="header-right">
          <span v-if="poolStats" class="pool-badge" :class="poolClass" :title="poolTitle">
            🔌 {{ poolStats.activeConnections }}/{{ poolStats.totalConnections }}
          </span>
          <ConnectionSelector />
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import ConnectionSelector from './components/ConnectionSelector.vue'

const route = useRoute()
const activeRoute = computed(() => route.path)

// Pool monitoring
interface PoolInfo { activeConnections: number; idleConnections: number; totalConnections: number; waitingThreads: number; connectionCacheSize: number }
const poolStats = ref<PoolInfo | null>(null)
let poolTimer: any = null

const poolClass = computed(() => {
  if (!poolStats.value) return ''
  const ratio = poolStats.value.totalConnections > 0 ? poolStats.value.activeConnections / poolStats.value.totalConnections : 0
  if (poolStats.value.waitingThreads > 0) return 'pool-warn'
  if (ratio > 0.8) return 'pool-busy'
  return 'pool-ok'
})

const poolTitle = computed(() => {
  if (!poolStats.value) return ''
  const s = poolStats.value
  return `活跃: ${s.activeConnections} | 空闲: ${s.idleConnections} | 总数: ${s.totalConnections} | 等待: ${s.waitingThreads} | 缓存池: ${s.connectionCacheSize}`
})

async function fetchPoolStats() {
  try {
    const resp = await fetch('/api/health/pool')
    poolStats.value = await resp.json()
  } catch { /* silent */ }
}

onMounted(() => {
  fetchPoolStats()
  poolTimer = setInterval(fetchPoolStats, 30000) // every 30s
})

onUnmounted(() => {
  clearInterval(poolTimer)
})
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

#app {
  width: 100%;
  height: 100%;
}
</style>

<style scoped>
.app-layout {
  height: 100%;
}

.app-header {
  display: flex;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  height: 60px;
  overflow: hidden;
}

.logo {
  font-size: 18px;
  font-weight: bold;
  color: #409eff;
  margin-right: 24px;
  white-space: nowrap;
}

.nav-menu {
  flex: 1;
  border-bottom: none !important;
  overflow: hidden;
}

.nav-menu .el-menu-item {
  height: 60px;
  line-height: 60px;
}

.header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 12px;
}

/* Pool badge */
.pool-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  cursor: default;
  white-space: nowrap;
}
.pool-ok { background: #e8f5e9; color: #2e7d32; }
.pool-busy { background: #fff3e0; color: #e65100; }
.pool-warn { background: #ffebee; color: #c62828; }

.app-main {
  height: calc(100% - 60px);
  overflow: auto;
}

/* ── Mobile responsive ── */
@media (max-width: 768px) {
  .app-header {
    padding: 0 8px;
    height: 48px;
  }
  .logo {
    font-size: 14px;
    margin-right: 8px;
  }
  .nav-menu {
    display: none; /* hide nav on mobile — use hamburger or swipe */
  }
  .pool-badge {
    font-size: 10px;
    padding: 1px 6px;
  }
  .app-main {
    height: calc(100% - 48px);
    overflow: auto;
    padding: 8px;
  }
}

/* Tablet */
@media (min-width: 769px) and (max-width: 1024px) {
  .app-header {
    padding: 0 12px;
  }
  .logo {
    font-size: 15px;
    margin-right: 12px;
  }
  .nav-menu .el-menu-item {
    padding: 0 10px;
    font-size: 13px;
  }
}
</style>
