import { defineStore } from 'pinia'
import request from '@/api/request'

interface LoginResp {
  token: string
  userId: number
  username: string
  nickname: string
  role: string
  permissions: string[]
}

/** 从 JWT payload 中解析用户信息（刷新页面后恢复 role / permissions） */
function decodeJwt(token: string) {
  try {
    const payload = token.split('.')[1]
    // atob 返回 Latin-1 二进制串，需转为 UTF-8 才能正确显示中文
    const binary = atob(payload)
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
    const json = new TextDecoder('utf-8').decode(bytes)
    const decoded = JSON.parse(json)
    return {
      userId: Number(decoded.sub) || 0,
      username: decoded.username || '',
      nickname: decoded.nickname || '',
      role: decoded.role || '',
      permissions: Array.isArray(decoded.permissions) ? decoded.permissions : []
    }
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => {
    const token = localStorage.getItem('token') || ''
    const info = token ? decodeJwt(token) : null
    return {
      token,
      userId: info?.userId || 0,
      username: info?.username || '',
      nickname: info?.nickname || '',
      role: info?.role || '',
      permissions: info?.permissions || [] as string[]
    }
  },
  actions: {
    async login(username: string, password: string) {
      const res: any = await request.post('/user/login', { username, password })
      const data: LoginResp = res.data
      this.token = data.token
      this.userId = data.userId
      this.username = data.username
      this.nickname = data.nickname
      this.role = data.role
      this.permissions = data.permissions
      localStorage.setItem('token', data.token)
    },
    logout() {
      this.token = ''
      this.userId = 0
      this.username = ''
      this.nickname = ''
      this.role = ''
      this.permissions = []
      localStorage.removeItem('token')
    }
  }
})
