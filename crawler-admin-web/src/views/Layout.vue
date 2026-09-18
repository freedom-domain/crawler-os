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

        <template v-for="item in menuList" :key="item.id">
          <MenuNode :item="item" />
        </template>
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
import { ref, computed, onMounted, defineComponent, h } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMenu } from '@/api'
import { ElSubMenu, ElMenuItem } from 'element-plus'
import { Odometer, Connection, List, Search, User, Fold, Expand, UserFilled, ArrowDown, PriceTag, Setting, Operation, Avatar, Lock, Folder, Menu as MenuIcon } from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStore()
const collapsed = ref(false)
const menuList = ref<any[]>([])

// 递归菜单节点：目录/有子项渲染为子菜单，叶子渲染为菜单项
const MenuNode = defineComponent({
  name: 'MenuNode',
  props: {
    item: { type: Object as any, required: true }
  },
  setup(props) {
    return () => {
      const item = props.item
      const hasChildren = item.children && item.children.length > 0
      const icon = h('el-icon', null, [h(getIcon(item.code))])
      if (hasChildren) {
        return h(ElSubMenu, { index: item.path || String(item.id) }, {
          title: () => [icon, h('span', item.name)],
          default: () => item.children.map((c: any) => h(MenuNode, { item: c, key: c.id }))
        })
      }
      return h(ElMenuItem, { index: item.path }, {
        default: () => [icon, h('span', item.name)]
      })
    }
  }
})

// 根据权限 code 映射图标
const iconMap: Record<string, any> = {
  user: User,
  role: Avatar,
  permission: Lock,
  dict: PriceTag,
  spider: Connection,
  task: List,
  search: Search,
  file: Folder
}

const getIcon = (code: string) => {
  return iconMap[code] || MenuIcon
}

const loadMenu = async () => {
  try {
    const res: any = await getMenu()
    menuList.value = res.data || []
  } catch {
    menuList.value = []
  }
}

onMounted(() => {
  loadMenu()
})

const currentTitle = computed(() => {
  const findTitle = (items: any[]): string => {
    for (const item of items) {
      if (item.path === route.path) return item.name
      if (item.children?.length) {
        const found = findTitle(item.children)
        if (found) return found
      }
    }
    return ''
  }
  return findTitle(menuList.value) || ''
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
