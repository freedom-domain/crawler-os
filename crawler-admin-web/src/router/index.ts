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
      path: '/',
      component: () => import('@/views/Layout.vue'),
      children: [
        { path: '', redirect: '/dashboard' },
        { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/Dashboard.vue') },
        { path: 'spider', name: 'Spider', component: () => import('@/views/Spider.vue') },
        { path: 'task', name: 'Task', component: () => import('@/views/Task.vue') },
        { path: 'search', name: 'Search', component: () => import('@/views/Search.vue') },
        { path: 'user', name: 'User', component: () => import('@/views/User.vue') }
      ]
    }
  ]
})

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.path !== '/login' && !userStore.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
