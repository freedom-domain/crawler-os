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
        <MenuNode :item="{ path: '/dashboard', name: 'Dashboard', icon: 'odometer' }" />

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

      <el-main @click="contextMenu.visible = false">
        <el-tabs
          v-model="activeTab"
          type="card"
          class="app-tabs"
          @tab-click="handleTabClick"
        >
          <el-tab-pane
            v-for="tab in tabs"
            :key="tab.key"
            :name="tab.key"
          >
            <template #label>
              <span
                class="tab-label"
                :draggable="tab.path !== '/dashboard'"
                @dragstart="startTabDrag(tab.key, $event)"
                @dragover.prevent
                @drop="dropTab(tab.key)"
                @contextmenu.prevent.stop="openTabMenu(tab.key, $event)"
              >
                <span>{{ tab.title }}</span>
                <button v-if="tab.path !== '/dashboard'" class="tab-close" type="button" @click.stop="closeTab(tab.key)">×</button>
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
        <div
          v-if="contextMenu.visible"
          class="tab-context-menu"
          :style="{ left: `${contextMenu.x}px`, top: `${contextMenu.y}px` }"
          @click.stop
        >
          <button @click="closeContextTab">关闭当前</button>
          <button @click="closeOtherTabs">关闭其他</button>
          <button @click="closeAllTabs">关闭全部</button>
        </div>
        <router-view v-slot="{ Component }">
          <keep-alive :include="cachedRouteNames">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineComponent, h, watch } from 'vue'
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
const tabs = ref<Array<{ key: string; path: string; title: string; name?: string }>>([
  { key: '/dashboard', path: '/dashboard', title: '首页', name: 'Dashboard' }
])
const TAB_STORAGE_KEY = 'crawler-open-tabs'
const draggedTab = ref('')
const contextMenu = ref({ visible: false, x: 0, y: 0, key: '' })
const activeTab = computed({
  get: () => route.fullPath,
  set: (value: string) => router.push(value)
})
const cachedRouteNames = computed(() => tabs.value.map(tab => tab.name).filter(Boolean) as string[])

const restoreTabs = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(TAB_STORAGE_KEY) || '[]')
    if (!Array.isArray(saved)) return
    const restored = saved.filter((tab): tab is { key: string; path: string; title: string; name?: string } =>
      tab && typeof tab.key === 'string' && typeof tab.path === 'string' && typeof tab.title === 'string' && tab.path !== '/login'
    )
    if (restored.length) {
      tabs.value = restored.some(tab => tab.path === '/dashboard')
        ? restored
        : [{ key: '/dashboard', path: '/dashboard', title: '首页', name: 'Dashboard' }, ...restored]
    }
  } catch {
    localStorage.removeItem(TAB_STORAGE_KEY)
  }
}

const saveTabs = () => {
  localStorage.setItem(TAB_STORAGE_KEY, JSON.stringify(tabs.value))
}

restoreTabs()
watch(tabs, saveTabs, { deep: true })

const onResize = () => {
  mobile.value = window.innerWidth < 768
  if (!mobile.value) drawerOpen.value = false
}
window.addEventListener('resize', onResize)

// 路由切换后自动收起移动端抽屉
router.afterEach(() => {
  drawerOpen.value = false
})

const titleForPath = (path: string) => {
  const findTitle = (items: any[]): string => {
    for (const item of items) {
      if (item.path === path) return item.name
      if (item.children?.length) {
        const found = findTitle(item.children)
        if (found) return found
      }
    }
    return ''
  }
  return findTitle(menuList.value) || ({
    '/dashboard': '首页',
    '/profile': '个人信息'
  }[path] || String(path).replace('/', ''))
}

const openCurrentTab = () => {
  const current = route
  if (!current.name || current.path === '/login') return
  if (!tabs.value.some(tab => tab.key === current.fullPath)) {
    tabs.value.push({
      key: current.fullPath,
      path: current.path,
      title: titleForPath(current.path),
      name: String(current.name)
    })
  }
}

watch(() => route.fullPath, openCurrentTab, { immediate: true })

const handleTabClick = (pane: any) => {
  const target = tabs.value.find(tab => tab.key === pane.paneName)
  if (target && target.key !== route.fullPath) router.push(target.key)
}

const closeTab = (key: string) => {
  const index = tabs.value.findIndex(tab => tab.key === key)
  if (index < 0 || tabs.value[index].path === '/dashboard') return
  const wasActive = key === route.fullPath
  tabs.value.splice(index, 1)
  if (wasActive) {
    const next = tabs.value[index] || tabs.value[index - 1] || tabs.value[0]
    if (next) router.push(next.key)
  }
}

const startTabDrag = (key: string, event: DragEvent) => {
  if (key === '/dashboard') return
  draggedTab.value = key
  event.dataTransfer?.setData('text/plain', key)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

const dropTab = (targetKey: string) => {
  const sourceKey = draggedTab.value
  draggedTab.value = ''
  if (!sourceKey || sourceKey === targetKey || sourceKey === '/dashboard' || targetKey === '/dashboard') return
  const sourceIndex = tabs.value.findIndex(tab => tab.key === sourceKey)
  const targetIndex = tabs.value.findIndex(tab => tab.key === targetKey)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [tab] = tabs.value.splice(sourceIndex, 1)
  tabs.value.splice(targetIndex, 0, tab)
}

const openTabMenu = (key: string, event: MouseEvent) => {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  contextMenu.value = { visible: true, x: rect.left, y: rect.bottom + 4, key }
}

const closeContextTab = () => {
  const key = contextMenu.value.key
  contextMenu.value.visible = false
  closeTab(key)
}

const closeOtherTabs = () => {
  const key = contextMenu.value.key
  const current = tabs.value.find(tab => tab.key === key)
  tabs.value = tabs.value.filter(tab => tab.path === '/dashboard' || tab.key === key)
  contextMenu.value.visible = false
  if (current && current.key !== route.fullPath) router.push(current.key)
}

const closeAllTabs = () => {
  tabs.value = tabs.value.filter(tab => tab.path === '/dashboard')
  contextMenu.value.visible = false
  if (route.path !== '/dashboard') router.push('/dashboard')
}

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
  odometer: Odometer,
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
    tabs.value.forEach(tab => {
      tab.title = titleForPath(tab.path)
    })
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
      router.replace('/login')
    }).catch(() => {})
  }
}
</script>

<style scoped>
.layout { height: 100vh; min-width: 960px; }
.aside {
  background: linear-gradient(180deg, #142a4c 0%, #193556 100%);
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
  background: rgba(8, 25, 52, .32);
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
:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) { background: rgba(114, 224, 200, .09); }
:deep(.el-menu-item.is-active) { background: #0f9f9a; box-shadow: none; }
:deep(.el-sub-menu.is-active > .el-sub-menu__title) { color: #fff; background: rgba(15, 159, 154, .32); }
:deep(.el-menu--collapse .el-sub-menu.is-active > .el-sub-menu__title) { background: #0f9f9a; box-shadow: none; }
:deep(.el-menu-item .el-icon), :deep(.el-sub-menu .el-icon) { color: #72e0c8; }
.el-menu--collapse { padding: 14px 10px; }
.aside.is-collapsed :deep(.el-menu) { width: 72px; }
.aside.is-collapsed .logo { justify-content: center; padding: 0; }
.app-tabs { margin: -12px -8px 22px; padding: 5px 6px 0; overflow: hidden; border-bottom: 1px solid #dfe7ee; background: #edf3f4; }
:deep(.app-tabs .el-tabs__header) { margin: 0; border-bottom: 0; }
:deep(.app-tabs .el-tabs__nav-wrap) { overflow: visible; }
:deep(.app-tabs .el-tabs__nav-wrap::after) { display: none; }
:deep(.app-tabs .el-tabs__nav) { gap: 4px; border: 0; }
:deep(.app-tabs .el-tabs__item) { position: relative; height: 34px; padding: 0 14px; border: 0; border-radius: 7px 7px 0 0; color: #6b8195; background: transparent; font-size: 12px; transition: color .18s ease, background .18s ease, box-shadow .18s ease; }
:deep(.app-tabs .el-tabs__item::before) { content: ''; position: absolute; left: 12px; right: 12px; bottom: 0; height: 3px; border-radius: 3px 3px 0 0; background: transparent; transition: background .18s ease; }
:deep(.app-tabs .el-tabs__item.is-active) { color: #0f817e; background: #fff; box-shadow: 0 -1px 0 rgba(255, 255, 255, .8), 0 2px 8px rgba(23, 43, 77, .06); font-weight: 700; }
:deep(.app-tabs .el-tabs__item.is-active::before) { background: #0f9f9a; }
:deep(.app-tabs .el-tabs__item:hover) { color: #0f9f9a; background: rgba(255, 255, 255, .72); }
:deep(.app-tabs.el-tabs--card > .el-tabs__header .el-tabs__item.is-closable:hover) { padding-left: 14px; padding-right: 14px; }
:deep(.app-tabs.el-tabs--card > .el-tabs__header .el-tabs__item.is-active.is-closable) { padding-left: 14px; padding-right: 14px; }
:deep(.app-tabs.el-tabs--card > .el-tabs__header .el-tabs__item) { transition: color .18s ease, background .18s ease, box-shadow .18s ease; }
:deep(.app-tabs.el-tabs--card > .el-tabs__header .el-tabs__item .is-icon-close) { width: 16px; height: 16px; overflow: visible; }
:deep(.app-tabs .el-tabs__item .el-icon) { width: 16px; height: 16px; margin-left: 7px; border-radius: 50%; color: #9fb3c8; transition: color .18s ease, background .18s ease; }
:deep(.app-tabs .el-tabs__item .el-icon:hover) { color: #fff; background: #ed765f; }
:deep(.app-tabs .el-tabs__active-bar) { display: none; }
.tab-label { display: inline-flex; align-items: center; gap: 7px; cursor: grab; user-select: none; }
.tab-label:active { cursor: grabbing; }
.tab-close { width: 16px; height: 16px; padding: 0; border: 0; border-radius: 50%; color: #9fb3c8; background: transparent; font: inherit; font-size: 15px; line-height: 14px; cursor: pointer; }
.tab-close:hover { color: #fff; background: #ed765f; }
.tab-context-menu { position: fixed; z-index: 3000; display: flex; flex-direction: column; min-width: 118px; padding: 5px; border: 1px solid #dfe7ee; border-radius: 8px; background: #fff; box-shadow: 0 10px 24px rgba(23, 43, 77, .16); }
.tab-context-menu button { padding: 8px 12px; border: 0; border-radius: 5px; color: #486581; background: transparent; text-align: left; font: inherit; font-size: 12px; cursor: pointer; }
.tab-context-menu button:hover { color: #0f817e; background: #eaf7f5; }
@media (max-width: 767px) { .app-tabs { margin: -6px -4px 16px; padding-left: 4px; overflow-x: auto; } :deep(.app-tabs .el-tabs__nav) { min-width: max-content; } }
.header {
  height: 72px;
  background: rgba(255, 255, 255, .94);
  display: flex;
  align-items: center;
  border-bottom: 1px solid #e6e9ef;
  box-shadow: 0 1px 14px rgba(23, 43, 77, .06);
  backdrop-filter: blur(12px);
}
.collapse-btn { font-size: 20px; cursor: pointer; margin: 0 22px; color: #486581; }
.breadcrumb { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.breadcrumb span, .breadcrumb b { color: #9fb3c8; font-weight: 500; }
.breadcrumb strong { color: #102a43; font-size: 16px; font-weight: 800; }
.user-info { margin-left: auto; margin-right: 24px; }
.user-name { cursor: pointer; display: flex; align-items: center; gap: 9px; color: #486581; }
.user-avatar { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 50%; color: #087f7d; background: #d9f5ef; }
.user-label { color: #243b53; font-size: 14px; font-weight: 600; }
:deep(.el-main) { padding: 32px; background: transparent; overflow: auto; }
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
