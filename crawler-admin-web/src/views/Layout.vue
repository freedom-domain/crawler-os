<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '72px' : '236px'" :class="['aside', { 'is-collapsed': collapsed }]">
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
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>Dashboard</span>
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
                <el-dropdown-item command="profile">
                  <span class="dropdown-item-label">用户信息</span>
                </el-dropdown-item>
                <el-dropdown-item command="changePassword">
                  <span class="dropdown-item-label">修改密码</span>
                </el-dropdown-item>
                <el-dropdown-item command="logout" divided>
                  <span class="dropdown-item-label">退出登录</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main>
        <router-view />
      </el-main>
    </el-container>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px">
      <el-form :model="passwordForm" label-width="100px">
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入当前密码" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" placeholder="请输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="请再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineComponent, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMenu, userChangePassword } from '@/api'
import { ElIcon, ElSubMenu, ElMenuItem, ElMessageBox, ElMessage } from 'element-plus'
import { Odometer, Connection, List, Search, User, Fold, Expand, UserFilled, ArrowDown, PriceTag, Setting, Operation, Avatar, Lock, Folder, Menu as MenuIcon } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
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

const sortMenu = (items: any[]): any[] => {
  return [...items].sort((a, b) => (a.sort || 0) - (b.sort || 0))
}

const loadMenu = async () => {
  try {
    const res: any = await getMenu()
    const data = res.data || []
    menuList.value = sortMenu(data).map(item => ({
      ...item,
      children: item.children ? sortMenu(item.children) : []
    }))
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
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      router.push('/login')
    }).catch(() => {})
  } else if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'changePassword') {
    passwordDialogVisible.value = true
  }
}

// 修改密码
const passwordDialogVisible = ref(false)
const passwordLoading = ref(false)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const handleChangePassword = async () => {
  if (!passwordForm.value.oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (!passwordForm.value.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于6位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }
  passwordLoading.value = true
  try {
    await userChangePassword({
      userId: userStore.userId,
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码修改成功')
    passwordDialogVisible.value = false
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e: any) {
    ElMessage.error(e.message || '密码修改失败')
  } finally {
    passwordLoading.value = false
  }
}
</script>

<style scoped>
.layout { height: 100vh; min-width: 960px; }
.aside {
  position: relative;
  background: linear-gradient(180deg, #132b47 0%, #10243d 56%, #0d2037 100%);
  transition: width 0.25s ease;
  overflow: hidden;
  box-shadow: 8px 0 24px rgba(16, 42, 67, .12);
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
  border-bottom: 1px solid rgba(255, 255, 255, .08);
  background: rgba(8, 25, 44, .24);
}
.logo-mark {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, .2);
  border-radius: 9px;
  color: #172b4d;
  background: #72e0c8;
  font-size: 17px;
  font-weight: 900;
  box-shadow: 0 5px 12px rgba(114, 224, 200, .18);
}
.logo-copy span { color: #72e0c8; }
:deep(.el-menu) { border-right: 0; padding: 18px 12px; }
:deep(.el-menu-item), :deep(.el-sub-menu__title) {
  height: 46px;
  line-height: 46px;
  margin: 5px 0;
  border: 1px solid transparent;
  border-radius: 10px;
  transition: color .2s ease, background .2s ease, border-color .2s ease, transform .2s ease;
}
:deep(.el-menu--collapse .el-menu-item), :deep(.el-menu--collapse .el-sub-menu__title) {
  width: 52px;
  padding: 0 !important;
  justify-content: center;
}
:deep(.el-menu--collapse .el-menu-item .el-icon), :deep(.el-menu--collapse .el-sub-menu__title .el-icon) {
  margin: 0;
}
:deep(.el-menu-item:not(.is-active):hover), :deep(.el-sub-menu__title:hover) {
  color: #fff !important;
  border-color: rgba(114, 224, 200, .12);
  background: rgba(114, 224, 200, .08);
  transform: translateX(2px);
}
:deep(.el-menu-item.is-active) {
  color: #fff;
  border-color: rgba(114, 224, 200, .2);
  background: #109f9a;
  box-shadow: 0 7px 16px rgba(8, 123, 120, .2);
  transform: none;
}
:deep(.el-menu-item.is-active:hover) {
  color: #fff !important;
  border-color: rgba(114, 224, 200, .2);
  background: #109f9a;
  transform: none;
}
:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #fff;
  border-color: rgba(114, 224, 200, .14);
  background: rgba(15, 159, 154, .24);
}
:deep(.el-menu--collapse .el-sub-menu.is-active > .el-sub-menu__title) {
  background: rgba(15, 159, 154, .78);
  box-shadow: 0 7px 16px rgba(8, 123, 120, .18);
}
:deep(.el-menu-item .el-icon), :deep(.el-sub-menu .el-icon) {
  color: #72e0c8;
  transition: color .2s ease;
}
:deep(.el-menu-item.is-active .el-icon), :deep(.el-sub-menu__title:hover .el-icon) { color: #fff; }
.el-menu--collapse { padding: 18px 10px; }
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
.user-dropdown-info { padding: 12px 16px; border-bottom: 1px solid #ebeef5; }
.user-dropdown-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; font-size: 13px; }
.user-dropdown-row .label { color: #909399; }
.user-dropdown-row .value { color: #303133; font-weight: 500; }
:deep(.el-main) { padding: 30px; background: var(--canvas); overflow: auto; }
@media (max-width: 1100px) {
  .layout { min-width: 0; }
  :deep(.el-main) { padding: 20px; }
  .user-label { display: none; }
}
</style>
