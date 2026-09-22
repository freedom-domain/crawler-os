<template>
  <el-container class="layout">
    <el-overlay v-if="mobile && drawerOpen" class="drawer-mask" @click="drawerOpen = false" />
    <el-aside :width="collapsed ? '72px' : '236px'" :class="['aside', { 'is-collapsed': collapsed, 'is-mobile': mobile, 'is-open': drawerOpen }]">
      <div class="logo">
        <span class="logo-mark">C</span>
        <span v-if="!collapsed" class="logo-copy">Crawler<span>OS</span></span>
      </div>
      <el-menu
        :default-active="route.path"
        :collapse="collapsed"
        router
        background-color="transparent"
        text-color="#b7c9d9"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/dashboard" class="dashboard-menu-item">
          <Odometer class="dashboard-menu-icon" />
          <template #title>Dashboard</template>
        </el-menu-item>

        <template v-for="item in menuList" :key="item.id">
          <MenuNode :item="item" />
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <el-icon v-if="mobile" class="collapse-btn" @click="drawerOpen = true"><MenuIcon /></el-icon>
        <el-icon v-else class="collapse-btn" @click="collapsed = !collapsed">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
        <div class="breadcrumb"><span>工作台</span><b>/</b><strong>{{ currentTitle || '概览' }}</strong></div>
        <div class="user-info">
          <el-dropdown @command="handleCommand">
            <span class="user-name">
              <span class="user-avatar"><el-icon><UserFilled /></el-icon></span>
              <span class="user-label">{{ userStore.nickname || userStore.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <ChangePasswordDialog v-model="pwdDialogVisible" />

      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineComponent, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMenu } from '@/api'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { ElIcon, ElSubMenu, ElMenuItem, ElMessageBox } from 'element-plus'
import { Odometer, Connection, List, Search, User, Fold, Expand, UserFilled, ArrowDown, PriceTag, Setting, Operation, Avatar, Lock, Folder, Menu as MenuIcon } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const menuList = ref<any[]>([])
const pwdDialogVisible = ref(false)
const mobile = ref(window.innerWidth < 768)
const drawerOpen = ref(false)

const onResize = () => {
  mobile.value = window.innerWidth < 768
  if (!mobile.value) drawerOpen.value = false
}
window.addEventListener('resize', onResize)

// 路由切换后自动收起移动端抽屉
router.afterEach(() => {
  drawerOpen.value = false
})

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
      const icon = h(ElIcon, null, [h(getIcon(item))])
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
  User,
  role: Avatar,
  Avatar,
  permission: Lock,
  Lock,
  dict: PriceTag,
  PriceTag,
  spider: Connection,
  Connection,
  task: List,
  List,
  search: Search,
  Search,
  file: Folder,
  Folder,
  system: Setting,
  Setting
}

const getIcon = (item: any) => {
  return iconMap[item.icon] || iconMap[item.code] || MenuIcon
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
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'password') {
    pwdDialogVisible.value = true
  } else if (cmd === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout { height: 100vh; min-width: 960px; }
.aside {
  background: #172b4d;
  transition: width 0.25s ease;
  overflow: hidden;
  box-shadow: 5px 0 18px rgba(23, 43, 77, .08);
}
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(16, 42, 67, .45);
}
.logo {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 22px;
  color: #fff;
  font-size: 19px;
  font-weight: 800;
  letter-spacing: .5px;
  background: #122442;
}
.logo-mark {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: #172b4d;
  background: #72e0c8;
  font-size: 17px;
  font-weight: 900;
}
.logo-copy span { color: #72e0c8; }
:deep(.el-menu) { border-right: 0; padding: 14px 10px; }
:deep(.el-menu-item), :deep(.el-sub-menu__title) { height: 46px; line-height: 46px; margin: 4px 0; border-radius: 8px; }
:deep(.el-menu--collapse .el-menu-item), :deep(.el-menu--collapse .el-sub-menu__title) {
  width: 52px;
  margin-left: 0 !important;
  padding: 0 !important;
  justify-content: center;
  display: flex;
  align-items: center;
  text-align: center;
}
:deep(.el-menu--collapse .el-menu-item .el-icon), :deep(.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin: 0;
  flex-shrink: 0;
}
:deep(.el-menu.el-menu--collapse > .el-menu-item.dashboard-menu-item) {
  width: 52px !important;
  height: 46px;
  margin-left: 0 !important;
  margin-right: 0 !important;
  padding: 0 !important;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dashboard-menu-icon {
  width: 24px;
  height: 24px;
  margin-right: 5px;
  flex-shrink: 0;
  vertical-align: middle;
}
:deep(.el-menu.el-menu--collapse > .el-menu-item.dashboard-menu-item > .dashboard-menu-icon) {
  position: static;
  width: 24px !important;
  height: 24px !important;
  display: block;
  margin: 0 0 0 -10px !important;
  padding: 0;
  transform: none;
}
:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) { background: rgba(114, 224, 200, .09); }
:deep(.el-menu-item.is-active) { background: #0f9f9a; box-shadow: none; }
:deep(.el-sub-menu.is-active > .el-sub-menu__title) { color: #fff; background: rgba(15, 159, 154, .32); }
:deep(.el-menu--collapse .el-sub-menu.is-active > .el-sub-menu__title) { background: #0f9f9a; box-shadow: none; }
:deep(.el-menu-item .el-icon), :deep(.el-sub-menu .el-icon) { color: #72e0c8; }
.el-menu--collapse { padding: 14px 10px; }
.aside.is-collapsed :deep(.el-menu) { width: 72px; }
.aside.is-collapsed .logo { justify-content: center; padding: 0; }
.header {
  height: 72px;
  background: #fff;
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e6e9ef;
  box-shadow: 0 1px 8px rgba(23, 43, 77, .035);
}
.collapse-btn { font-size: 20px; cursor: pointer; margin: 0 22px; color: #486581; }
.breadcrumb { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.breadcrumb span, .breadcrumb b { color: #9fb3c8; font-weight: 500; }
.breadcrumb strong { color: #102a43; font-size: 16px; }
.user-info { margin-left: auto; margin-right: 24px; }
.user-name { cursor: pointer; display: flex; align-items: center; gap: 9px; color: #486581; }
.user-avatar { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 50%; color: #087f7d; background: #d9f5ef; }
.user-label { color: #243b53; font-size: 14px; font-weight: 600; }
:deep(.el-main) { padding: 30px; background: var(--canvas); overflow: auto; }
@media (max-width: 1100px) {
  .layout { min-width: 0; }
  :deep(.el-main) { padding: 20px; }
  .user-label { display: none; }
}
@media (max-width: 767px) {
  .layout { min-width: 0; }
  .aside {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 236px !important;
    z-index: 2100;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .aside.is-open { transform: translateX(0); }
  .header { height: 56px; }
  .collapse-btn { margin: 0 14px; }
  .breadcrumb { font-size: 13px; }
  .breadcrumb strong { font-size: 14px; }
  .user-info { margin-right: 12px; }
  :deep(.el-main) { padding: 14px; }
}
</style>
