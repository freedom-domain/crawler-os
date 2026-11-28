import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { hasValidToken, resolveAuthRedirect } from '@/utils/authRedirect'

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
      path: '/ai-chat-window',
      name: 'AiChatWindow',
      component: () => import('@/views/AiChat.vue')
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
        { path: 'favorite', name: 'Favorite', component: () => import('@/views/Favorite.vue') },
        { path: 'image-history', name: 'ImageHistory', component: () => import('@/views/ImageHistory.vue') },
        { path: 'investment', redirect: '/investment/stock-management' },
        { path: 'investment/overview', redirect: '/investment/stock-management' },
        { path: 'investment/stock', redirect: '/investment/stock-management' },
        { path: 'investment/stock-management', name: 'StockManagement', component: () => import('@/views/StockManagement.vue') },
        { path: 'investment/onshore-fund', redirect: '/investment/fund-management' },
        { path: 'investment/offshore-fund', redirect: '/investment/fund-management' },
        { path: 'investment/fund-management', name: 'FundManagement', component: () => import('@/views/FundManagement.vue') },
        { path: 'file', name: 'File', component: () => import('@/views/File.vue') },
        { path: 'dict', name: 'Dict', component: () => import('@/views/Dict.vue') },
        { path: 'user', name: 'User', component: () => import('@/views/User.vue') },
        { path: 'role', name: 'Role', component: () => import('@/views/Role.vue') },
        { path: 'permission', name: 'Permission', component: () => import('@/views/Permission.vue') },
        { path: 'ai-config', redirect: '/ai-knowledge' },
        { path: 'ai-knowledge', name: 'AiKnowledgeBase', component: () => import('@/views/AiKnowledgeBase.vue') },
        { path: 'ai-knowledge-documents', name: 'AiKnowledgeDocuments', component: () => import('@/views/AiKnowledgeDocuments.vue') },
        { path: 'ai-knowledge-documents/knowledge/:knowledgeBaseId/:objectName(.*)', name: 'AiKnowledgeDocumentFile', component: () => import('@/views/AiKnowledgeDocuments.vue') },
        { path: 'ai-knowledge-documents/:knowledgeBaseId', name: 'AiKnowledgeDocumentsById', component: () => import('@/views/AiKnowledgeDocuments.vue') },
        { path: 'ai-models', name: 'AiModels', component: () => import('@/views/AiModels.vue') },
        { path: 'ai-vector-models', redirect: '/ai-models?tab=vector' },
        { path: 'ai-chat-models', redirect: '/ai-models?tab=chat' },
        { path: 'ai-assistants', name: 'AiAssistants', component: () => import('@/views/AiAssistants.vue') },
        { path: 'ai-assistant-roles', name: 'AiAssistantRoles', component: () => import('@/views/AiAssistantRoles.vue') },
        { path: 'ai-chat', name: 'AiChat', component: () => import('@/views/AiChat.vue') },
        { path: 'ai-chat/:assistantId', name: 'AiChatAssistant', component: () => import('@/views/AiChat.vue') },
        { path: 'cf-ip-management', name: 'CfIpManagement', component: () => import('@/views/CfIpManagement.vue') },
        { path: 'profile', name: 'Profile', component: () => import('@/views/Profile.vue') }
      ]
    }
  ]
})

// 无需登录即可访问的公开页面
const PUBLIC_PATHS = ['/login', '/image-preview', '/public/search']

router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  // 已登录用户访问登录页：渲染表单前直接跳转目标页
  if (to.path === '/login' && userStore.token && hasValidToken()) {
    next(resolveAuthRedirect(router, to.query.redirect))
    return
  }
  if (!PUBLIC_PATHS.includes(to.path) && !userStore.token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
