<template>
  <el-container class="layout">
    <el-overlay v-if="mobile && drawerOpen" class="drawer-mask" @click="drawerOpen = false" />
    <el-aside :width="collapsed ? '72px' : '236px'" :class="['aside', { 'is-collapsed': collapsed, 'is-mobile': mobile, 'is-open': drawerOpen }]">
      <div class="logo">
        <img class="logo-mark" src="/crawleros-icon.svg" alt="" />
        <span v-if="!collapsed" class="logo-copy">Crawler<span>OS</span></span>
      </div>
      <div class="menu-scroll">
        <el-menu
          :default-active="route.path"
          :collapse="collapsed"
          :unique-opened="true"
          @select="handleMenuSelect"
          background-color="transparent"
        >
          <MenuNode :item="{ path: '/dashboard', name: 'Dashboard', icon: 'odometer' }" />

          <template v-for="item in visibleMenuList" :key="item.id">
            <MenuNode :item="item" />
          </template>
        </el-menu>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header" @click="contextMenu.visible = false">
        <el-icon v-if="mobile" class="collapse-btn" @click="drawerOpen = true"><MenuIcon /></el-icon>
        <el-icon v-else class="collapse-btn" @click="collapsed = !collapsed">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
        <div class="tab-strip">
          <button
            type="button"
            class="tab-scroll-button"
            aria-label="向左滚动标签"
            title="向左滚动标签"
            :disabled="!canScrollTabsLeft"
            @click="scrollTabs(-1)"
          >
            <el-icon><ArrowLeft /></el-icon>
          </button>
          <div ref="tabsViewport" class="app-tabs" role="tablist" @scroll="updateTabScrollState">
            <div
              v-for="tab in tabs"
              :key="tab.key"
              :ref="element => setTabElement(tab.key, element)"
              class="app-tab"
              :class="{ 'is-active': tab.path === route.path }"
              role="tab"
              :aria-selected="tab.path === route.path"
              :tabindex="tab.path === route.path ? 0 : -1"
              :draggable="tab.path !== '/dashboard'"
              @click="handleTabClick(tab)"
              @keydown.enter="handleTabClick(tab)"
              @keydown.space.prevent="handleTabClick(tab)"
              @dragstart="startTabDrag(tab.key, $event)"
              @dragover.prevent
              @drop="dropTab(tab.key)"
              @contextmenu.prevent.stop="openTabMenu(tab.key, $event)"
            >
              <span class="tab-label">{{ tab.title }}</span>
              <button v-if="tab.path !== '/dashboard'" class="tab-close" type="button" :aria-label="`关闭${tab.title}`" @click.stop="closeTab(tab.key)">×</button>
            </div>
          </div>
          <button
            type="button"
            class="tab-scroll-button"
            aria-label="向右滚动标签"
            title="向右滚动标签"
            :disabled="!canScrollTabsRight"
            @click="scrollTabs(1)"
          >
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <button type="button" class="theme-switch theme-btn" :title="themeModeTitle" @click="toggleTheme">
          <el-icon :size="18"><component :is="themeIcon" /></el-icon>
        </button>
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
import { ref, computed, onMounted, onBeforeUnmount, defineComponent, h, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { getMenu } from '@/api'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import { ElIcon, ElSubMenu, ElMenuItem } from 'element-plus'
import { confirm } from '@/utils/confirm'
import { menuIconLibrary } from '@/utils/menuIcons'
import { Odometer, Connection, List, Search, User, Fold, Expand, UserFilled, ArrowDown, ArrowLeft, ArrowRight, PriceTag, Setting, Operation, Avatar, Lock, Folder, Picture, TrendCharts, Coin, DataLine, Menu as MenuIcon, Sunny, Moon, ChatDotRound, Cpu } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const themeStore = useThemeStore()
// 主题切换：浅色 / 纯黑
const themeIcon = computed(() => (themeStore.mode === 'light' ? Moon : Sunny))
const themeModeTitle = computed(() =>
  themeStore.mode === 'light' ? '浅色，点击切换为纯黑' : '纯黑，点击切换为浅色'
)
const toggleTheme = () => themeStore.choose(themeStore.mode === 'light' ? 'black' : 'light')
const SIDEBAR_COLLAPSED_STORAGE_KEY = 'crawler-sidebar-collapsed'
const collapsed = ref(localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === 'true')
watch(collapsed, value => {
  localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, String(value))
})
const menuList = ref<any[]>([])
const visibleMenuList = computed(() => {
  const filterVisible = (items: any[]): any[] => items
    .filter(item => item.showInMenu !== 0)
    .map(item => ({ ...item, children: filterVisible(item.children || []) }))
  return filterVisible(menuList.value)
})
const pwdDialogVisible = ref(false)
// 移动端判断：宽度 < 768，或小屏幕（短边 < 768）横屏——横屏时宽度过阈值但仍是手机，
// 侧边菜单需按抽屉隐藏
const isMobileViewport = () =>
  window.innerWidth < 768 ||
  (window.matchMedia('(orientation: landscape)').matches && Math.min(window.innerWidth, window.innerHeight) < 768)
const mobile = ref(isMobileViewport())
const drawerOpen = ref(false)
const tabsViewport = ref<HTMLElement | null>(null)
const tabElements = new Map<string, HTMLElement>()
const canScrollTabsLeft = ref(false)
const canScrollTabsRight = ref(false)
const tabs = ref<Array<{ key: string; path: string; title: string; name?: string }>>([
  { key: '/dashboard', path: '/dashboard', title: '首页', name: 'Dashboard' }
])
const TAB_STORAGE_KEY = 'crawler-open-tabs'
const MENU_CACHE_TTL = 5 * 60 * 1000
const draggedTab = ref('')
const contextMenu = ref({ visible: false, x: 0, y: 0, key: '' })
const cachedRouteNames = computed(() => tabs.value.map(tab => tab.name).filter(Boolean) as string[])

const restoreTabs = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(TAB_STORAGE_KEY) || '[]')
    if (!Array.isArray(saved)) return
    const restored = saved.filter((tab): tab is { key: string; path: string; title: string; name?: string } =>
      tab && typeof tab.key === 'string' && typeof tab.path === 'string' && typeof tab.title === 'string' && tab.path !== '/login'
    ).map(tab => {
      const isLegacyInvestmentPath = ['/investment', '/investment/overview'].includes(tab.path)
      const path = isLegacyInvestmentPath ? '/investment/stock-management' : tab.path
      const name = isLegacyInvestmentPath ? 'StockManagement' : tab.name
      return { ...tab, key: path, path, name }
    })
    const uniqueRestored = [...new Map(restored.map(tab => [tab.path, tab])).values()]
    if (uniqueRestored.length) {
      tabs.value = uniqueRestored.some(tab => tab.path === '/dashboard')
        ? uniqueRestored
        : [{ key: '/dashboard', path: '/dashboard', title: '首页', name: 'Dashboard' }, ...uniqueRestored]
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
  mobile.value = isMobileViewport()
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
      if (item.path === path
          || (item.path === '/ai-knowledge-documents' && path.startsWith(`${item.path}/`))) {
        return item.name
      }
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

const handleMenuSelect = (path: string) => {
  if (!path) return
  const resolved = router.resolve(path)
  if (!resolved.matched.length) return
  if (route.fullPath !== resolved.fullPath) {
    router.push(resolved.fullPath)
  }
  if (mobile.value) {
    drawerOpen.value = false
  }
}

const openCurrentTab = () => {
  const current = route
  if (!current.name || current.path === '/login') return
  if (!tabs.value.some(tab => tab.path === current.path)) {
    tabs.value.push({
      key: current.path,
      path: current.path,
      title: titleForPath(current.path),
      name: String(current.name)
    })
  }
  void nextTick(() => {
    tabElements.get(current.path)?.scrollIntoView({
      behavior: 'auto',
      block: 'nearest',
      inline: 'nearest'
    })
    updateTabScrollState()
  })
}

const updateTabScrollState = () => {
  const viewport = tabsViewport.value
  if (!viewport) {
    canScrollTabsLeft.value = false
    canScrollTabsRight.value = false
    return
  }
  canScrollTabsLeft.value = viewport.scrollLeft > 1
  canScrollTabsRight.value = viewport.scrollLeft + viewport.clientWidth < viewport.scrollWidth - 1
}

const scrollTabs = (direction: -1 | 1) => {
  const viewport = tabsViewport.value
  if (!viewport) return
  viewport.scrollBy({ left: direction * Math.max(160, viewport.clientWidth * 0.7), behavior: 'smooth' })
}

const setTabElement = (key: string, element: unknown) => {
  if (element instanceof HTMLElement) tabElements.set(key, element)
  else tabElements.delete(key)
}

watch(() => route.fullPath, openCurrentTab, { immediate: true, flush: 'post' })
watch(tabs, () => {
  void nextTick(updateTabScrollState)
}, { deep: true, flush: 'post' })
watch(collapsed, () => {
  void nextTick(updateTabScrollState)
})

const handleTabClick = (tab: { path: string }) => {
  if (tab.path !== route.path) router.push(tab.path)
}

const closeTab = (key: string) => {
  const index = tabs.value.findIndex(tab => tab.key === key)
  if (index < 0 || tabs.value[index].path === '/dashboard') return
  const wasActive = tabs.value[index].path === route.path
  tabs.value.splice(index, 1)
  if (wasActive) {
    const next = tabs.value[index] || tabs.value[index - 1] || tabs.value[0]
    if (next) router.push(next.path)
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
  if (current && current.path !== route.path) router.push(current.path)
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
      const icon = h(ElIcon, null, { default: () => h(getIcon(item)) })
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
  ...menuIconLibrary,
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
  Picture,
  TrendCharts,
  Coin,
  DataLine,
  system: Setting,
  'ai:config': Setting,
  'ai:knowledge': DataLine,
  'ai:models': Cpu,
  'ai:vector-models': Cpu,
  'ai:chat-models': ChatDotRound,
  'ai:chat': ChatDotRound,
  Cpu,
  Setting
}

const getIcon = (item: any) => {
  return iconMap[item.icon] || iconMap[item.code] || MenuIcon
}

const loadMenu = async () => {
  const cacheKey = `crawler-menu:v4:${userStore.userId}`
  let shouldRefresh = true
  try {
    const cached = JSON.parse(sessionStorage.getItem(cacheKey) || 'null')
    if (cached && Array.isArray(cached.data)) {
      menuList.value = cached.data
      tabs.value.forEach(tab => {
        tab.title = titleForPath(tab.path)
      })
      shouldRefresh = Date.now() - Number(cached.cachedAt) > MENU_CACHE_TTL
    }
  } catch {
    sessionStorage.removeItem(cacheKey)
  }
  if (!shouldRefresh) return

  try {
    const res: any = await getMenu()
    menuList.value = res.data || []
    sessionStorage.setItem(cacheKey, JSON.stringify({
      cachedAt: Date.now(),
      data: menuList.value
    }))
    tabs.value.forEach(tab => {
      tab.title = titleForPath(tab.path)
    })
  } catch {
    menuList.value = []
  }
}

onMounted(() => {
  window.addEventListener('crawler-menu-cache-invalidated', loadMenu)
  window.addEventListener('resize', updateTabScrollState)
  void nextTick(updateTabScrollState)
  loadMenu()
})

onBeforeUnmount(() => {
  window.removeEventListener('crawler-menu-cache-invalidated', loadMenu)
  window.removeEventListener('resize', updateTabScrollState)
})

const handleCommand = async (cmd: string) => {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'password') {
    pwdDialogVisible.value = true
  } else if (cmd === 'logout') {
    if (await confirm('确定要退出登录吗？')) {
      userStore.logout()
      router.replace('/login')
    }
  }
}
</script>

<style scoped>
.layout { width: 100%; height: 100%; min-width: 960px; }
.layout > :deep(.el-container) { height: 100%; min-height: 0; overflow: hidden; }
.aside {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--chrome-bg);
  border-right: 1px solid var(--chrome-line);
  transition: width 0.25s ease;
  overflow: hidden;
}
.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: var(--layout-mask);
}
.logo {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 22px;
  color: var(--chrome-text-strong);
  font-size: 19px;
  font-weight: 800;
  letter-spacing: .5px;
  background: var(--chrome-bg-soft);
}
.logo-mark {
  width: 30px;
  height: 30px;
  display: block;
  flex: none;
  border-radius: 9px;
}
.logo-copy span { color: var(--chrome-icon); }
.menu-scroll {
  flex: 1 1 auto;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-y;
}
:deep(.el-menu) {
  border-right: 0;
  padding: 14px 10px;
}
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
:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) { background: var(--chrome-hover); }
:deep(.el-menu-item.is-active) { background: var(--teal); box-shadow: 0 4px 14px rgba(var(--el-color-primary-rgb), .3); }
:deep(.el-menu-item.is-active .el-icon),
:deep(.el-sub-menu.is-active > .el-sub-menu__title .el-icon) { color: var(--on-teal); }
:deep(.el-sub-menu.is-active > .el-sub-menu__title) { color: #fff; background: rgba(var(--el-color-primary-rgb), .32); }
:deep(.el-menu--collapse .el-sub-menu.is-active > .el-sub-menu__title) { background: var(--teal); box-shadow: none; }
:deep(.el-menu-item .el-icon), :deep(.el-sub-menu .el-icon) { color: var(--chrome-icon); }
.el-menu--collapse { padding: 14px 10px; }
.aside.is-collapsed :deep(.el-menu) { width: 72px; }
.aside.is-collapsed .logo { justify-content: center; padding: 0; }
.tab-strip { display: flex; flex: 1 1 auto; align-items: center; min-width: 0; margin: 0 22px 0 28px; }
.app-tabs { display: flex; flex: 1 1 auto; min-width: 0; gap: 7px; overflow-x: auto; overflow-y: hidden; scrollbar-width: none; }
.app-tabs::-webkit-scrollbar { display: none; }
.tab-scroll-button { display: grid; flex: 0 0 26px; place-items: center; width: 26px; height: 34px; padding: 0; border: 0; border-radius: 7px; color: var(--chrome-icon); background: transparent; cursor: pointer; }
.tab-scroll-button:hover:not(:disabled) { background: var(--chrome-hover); }
.tab-scroll-button:disabled { color: var(--chrome-muted); opacity: .35; cursor: default; }
.app-tab { position: relative; display: inline-flex; flex: 0 0 auto; align-items: center; justify-content: center; height: 38px; padding: 0 14px; border: 1px solid transparent; border-radius: 10px; color: var(--chrome-muted); background: transparent; font-size: 12px; line-height: normal; cursor: pointer; transition: color .18s ease, background .18s ease, box-shadow .18s ease, border-color .18s ease; }
.app-tab.is-active { color: var(--tab-active-color); border-color: var(--tab-active-border); background: var(--tab-active-bg); box-shadow: 0 0 14px var(--tab-active-glow); font-weight: 700; }
.app-tab:hover { color: var(--chrome-text-strong); background: var(--chrome-hover); }
.tab-label { display: inline-flex; align-items: center; justify-content: center; gap: 7px; line-height: 1; cursor: grab; user-select: none; }
.tab-label:active { cursor: grabbing; }
.tab-close { width: 17px; height: 17px; padding: 0; border: 0; border-radius: 50%; color: var(--chrome-muted); background: transparent; font: inherit; font-size: 15px; line-height: 15px; cursor: pointer; transition: color .18s ease, background .18s ease; }
.tab-close:hover { color: #fff; background: #e87968; }
.tab-context-menu { position: fixed; z-index: 3000; display: flex; flex-direction: column; min-width: 118px; padding: 5px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); box-shadow: var(--shadow); }
.tab-context-menu button { padding: 8px 12px; border: 0; border-radius: 5px; color: var(--ink-700); background: transparent; text-align: left; font: inherit; font-size: 12px; cursor: pointer; }
.tab-context-menu button:hover { color: var(--teal-dark); background: var(--tint); }
.header {
  height: 72px;
  background: var(--header-bg);
  display: flex;
  align-items: center;
  border-bottom: 1px solid var(--chrome-line);
  box-shadow: var(--header-shadow);
  backdrop-filter: blur(12px);
}
.collapse-btn { flex: 0 0 auto; font-size: 20px; cursor: pointer; margin: 0 22px; color: var(--chrome-muted); transition: color .18s ease; }
.collapse-btn:hover { color: var(--chrome-icon); }
.theme-switch { flex: 0 0 auto; margin-right: 16px; }
.theme-btn {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--chrome-line);
  border-radius: 10px;
  color: var(--chrome-icon);
  background: var(--chrome-hover);
  cursor: pointer;
  transition: border-color .18s ease, box-shadow .18s ease;
}
.theme-btn:hover { border-color: var(--chrome-icon); box-shadow: 0 0 10px var(--tab-active-glow); }
.theme-item-icon { margin-right: 6px; vertical-align: -2px; }
.theme-item-on { margin-left: 10px; font-weight: 700; color: var(--teal); }
.user-info { flex: 0 0 auto; margin-left: 0; margin-right: 24px; }
.user-name { cursor: pointer; display: flex; align-items: center; gap: 9px; color: var(--chrome-text); }
:deep(.user-info .el-tooltip__trigger:hover) { outline: none; border-color: transparent; box-shadow: none; }
:deep(.user-info .el-tooltip__trigger:focus-visible) { outline: 2px solid var(--teal); outline-offset: 3px; }
.user-avatar { width: 32px; height: 32px; display: grid; place-items: center; border-radius: 50%; color: var(--chrome-icon); background: var(--chrome-hover); box-shadow: 0 0 12px var(--tab-active-glow); }
.user-label { color: var(--chrome-text-strong); font-size: 14px; font-weight: 600; }
:deep(.el-main) { flex: 1 1 auto; min-height: 0; padding: 32px; background: transparent; overflow: auto; }
:deep(.el-main)::-webkit-scrollbar-thumb { background: var(--scroll-thumb); background-clip: padding-box; }
:deep(.el-main)::-webkit-scrollbar-thumb:hover { background: var(--scroll-thumb-hover); background-clip: padding-box; }
@media (max-width: 1100px) {
  .layout { min-width: 0; }
  :deep(.el-main) { padding: 20px; }
  .user-label { display: none; }
}
@media (max-width: 767px) {
  .layout { min-width: 0; height: 100dvh; }
  .aside {
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    width: 236px !important;
    display: flex;
    flex-direction: column;
    z-index: 2100;
    transform: translateX(-100%);
    transition: transform 0.25s ease;
  }
  .aside.is-open { transform: translateX(0); }
  .header { height: 56px; }
  .collapse-btn { margin: 0 14px; }
  .tab-strip { margin-left: 14px; margin-right: 12px; }
  .user-info { margin-right: 12px; }
  :deep(.el-main) { padding: 14px; }
}
</style>
