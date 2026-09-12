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

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: 0,
    username: '',
    nickname: '',
    role: '',
    permissions: [] as string[]
  }),
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
