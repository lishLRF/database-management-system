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
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ConnectionSelector from './components/ConnectionSelector.vue'

const route = useRoute()
const activeRoute = computed(() => route.path)
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
}

.nav-menu .el-menu-item {
  height: 60px;
  line-height: 60px;
}

.header-right {
  margin-left: auto;
}

.app-main {
  height: calc(100% - 60px);
  overflow: auto;
}
</style>
