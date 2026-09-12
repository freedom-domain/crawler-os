import request from './request'

export const login = (data: { username: string; password: string }) =>
  request.post('/user/login', data)

export const userPage = (params: any) => request.get('/user/page', { params })
export const roles = () => request.get('/user/roles')
export const assignRole = (data: any) => request.put('/user/assign-role', data)

export const spiderPage = (params: any) => request.get('/spider/page', { params })
export const spiderDetail = (id: number) => request.get(`/spider/${id}`)
export const spiderCreate = (data: any) => request.post('/spider', data)
export const spiderUpdate = (id: number, data: any) => request.put(`/spider/${id}`, data)
export const spiderDelete = (id: number) => request.delete(`/spider/${id}`)
export const spiderStart = (id: number) => request.put(`/spider/${id}/start`)
export const spiderStop = (id: number) => request.put(`/spider/${id}/stop`)
export const spiderRun = (id: number) => request.post(`/spider/${id}/run`)

export const taskPage = (params: any) => request.get('/spider/task/page', { params })
export const taskDetail = (id: number) => request.get(`/spider/task/${id}`)
export const taskCancel = (id: number) => request.put(`/spider/task/${id}/cancel`)

export const searchContent = (params: any) => request.get('/search', { params })

export const filePage = (params: any) => request.get('/file/page', { params })
