<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '220px'" class="aside">
      <div class="logo">
        <span v-if="!collapsed">爬虫平台</span>
        <span v-else>爬</span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        router
        background-color="#001529"
        text-color="#fff"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>Dashboard</template>
        </el-menu-item>

        <el-sub-menu index="system" v-if="hasPerm('user') || hasPerm('role') || hasPerm('permission')">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/user" v-if="hasPerm('user')">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
          <el-menu-item index="/role" v-if="hasPerm('role')">
            <el-icon><Avatar /></el-icon>
            <template #title>角色管理</template>
          </el-menu-item>
          <el-menu-item index="/permission" v-if="hasPerm('permission')">
            <el-icon><Lock /></el-icon>
            <template #title>权限管理</template>
          </el-menu-item>
          <el-menu-item index="/dict" v-if="hasPerm('dict')">
            <el-icon><PriceTag /></el-icon>
            <template #title>字典管理</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="function" v-if="hasPerm('spider') || hasPerm('task') || hasPerm('search') || hasPerm('file')">
          <template #title>
            <el-icon><Operation /></el-icon>
            <span>功能管理</span>
          </template>
          <el-menu-item index="/spider" v-if="hasPerm('spider')">
            <el-icon><Connection /></el-icon>
            <template #title>爬虫管理</template>
          </el-menu-item>
          <el-menu-item index="/task" v-if="hasPerm('task')">
            <el-icon><List /></el-icon>
            <template #title>任务管理</template>
          </el-menu-item>
          <el-menu-item index="/search" v-if="hasPerm('search')">
            <el-icon><Search /></el-icon>
            <template #title>数据搜索</template>
          </el-menu-item>
          <el-menu-item index="/file" v-if="hasPerm('file')">
            <el-icon><Folder /></el-icon>
            <template #title>文件管理</template>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-icon class="collapse-btn" @click="collapsed = !collapsed">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
        <span class="page-title">{{ currentTitle }}</span>
        <div class="user-info">
          <el-dropdown @command="handleCommand">
            <span class="user-name">
              <el-icon><UserFilled /></el-icon>
              {{ userStore.nickname || userStore.username }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Odometer, Connection, List, Search, User, Fold, Expand, UserFilled, ArrowDown, PriceTag, Setting, Operation, Avatar, Lock, Folder } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const collapsed = ref(false)

// 管理员拥有全部权限
const hasPerm = (code: string) => {
  if (userStore.role === '管理员' || userStore.role === 'admin') {
    return true
  }
  return userStore.permissions.includes(code)
}

const currentTitle = computed(() => {
  const map: Record<string, string> = {
    '/dashboard': 'Dashboard',
    '/spider': '爬虫管理',
    '/task': '任务管理',
    '/search': '数据搜索',
    '/file': '文件管理',
    '/dict': '字典管理',
    '/user': '用户管理',
    '/role': '角色管理',
    '/permission': '权限管理'
  }
  return map[route.path] || ''
})

const handleCommand = (cmd: string) => {
  if (cmd === 'logout') {
    userStore.logout()
  }
}
</script>

<style scoped>
.layout { height: 100vh; }
.aside {
  background: #001529;
  transition: width 0.3s;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background: #002140;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  box-shadow: 0 1px 4px rgba(0,21,41,0.08);
}
.collapse-btn { font-size: 20px; cursor: pointer; margin-right: 20px; }
.page-title { font-size: 16px; font-weight: 500; flex: 1; }
.user-info { margin-right: 20px; }
.user-name { cursor: pointer; display: flex; align-items: center; gap: 6px; }
</style>
