import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/Login.vue')
    },
    {
      path: '/image-preview',
      name: 'ImagePreview',
      component: () => import('@/views/ImagePreview.vue')
    },
    {
      path: '/public/search',
      name: 'PublicSearch',
      component: () => import('@/views/PublicSearch.vue')
    },
    {
      path: '/',
      component: () => import('@/views/Layout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'spider', name: 'Spider', component: () => import('@/views/Spider.vue') },
        { path: 'task', name: 'Task', component: () => import('@/views/Task.vue') },
        { path: 'search', name: 'Search', component: () => import('@/views/Search.vue') },
        { path: 'file', name: 'File', component: () => import('@/views/File.vue') },
        { path: 'dict', name: 'Dict', component: () => import('@/views/Dict.vue') },
        { path: 'user', name: 'User', component: () => import('@/views/User.vue') },
        { path: 'role', name: 'Role', component: () => import('@/views/Role.vue') },
        { path: 'permission', name: 'Permission', component: () => import('@/views/Permission.vue') },
        { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue') }
      ]
    }
  ]
})

// 无需登录即可访问的公开页面
const PUBLIC_PATHS = ['/login', '/image-preview', '/public/search']

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (!PUBLIC_PATHS.includes(to.path) && !userStore.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
