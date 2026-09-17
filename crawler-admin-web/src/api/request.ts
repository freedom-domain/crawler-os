import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 后端以 HTTP 200 + body code=401 表示未登录/登录过期，需在此处理跳转
    if (res.code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.error(res.msg || '登录已过期，请重新登录')
        router.push('/login')
      }
      // 已处理跳转，不再 reject，避免调用方出现未捕获的 promise 错误
      return Promise.resolve(null)
    }
    if (res.code !== 200) {
      ElMessage.error(res.msg || '请求失败')
      return Promise.reject(new Error(res.msg))
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
      // 避免并发请求同时触发多次跳转，且已在登录页时不再重复跳转
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login')
      }
      // 已处理跳转，不再 reject，避免调用方出现未捕获的 promise 错误
      return Promise.resolve(null)
    } else {
      ElMessage.error(error.response?.data?.msg || error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
