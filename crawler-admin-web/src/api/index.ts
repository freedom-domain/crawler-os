import request from './request'

export const login = (data: { username: string; password: string }) =>
  request.post('/user/login', data)

export const getMenu = () => request.get('/user/menu')

export const userPage = (params: any) => request.get('/user/page', { params })
export const userDetail = (id: number) => request.get(`/user/${id}`)
export const userCreate = (data: any) => request.post('/user/register', data)
export const userUpdate = (id: number, data: any) => request.put(`/user/${id}`, data)
export const userDelete = (id: number) => request.delete(`/user/${id}`)
export const userChangePassword = (data: { userId: number; oldPassword: string; newPassword: string }) =>
  request.put('/user/password', data)

export const roleList = () => request.get('/role')
export const roleCreate = (data: any) => request.post('/role', data)
export const roleUpdate = (id: number, data: any) => request.put(`/role/${id}`, data)
export const roleDelete = (id: number) => request.delete(`/role/${id}`)
export const rolePermissions = (id: number) => request.get(`/role/${id}/permissions`)
export const roleAssignPermissions = (data: any) => request.put('/role/permissions', data)

export const permissionTree = () => request.get('/permission')
export const permissionCreate = (data: any) => request.post('/permission', data)
export const permissionUpdate = (id: number, data: any) => request.put(`/permission/${id}`, data)
export const permissionDelete = (id: number) => request.delete(`/permission/${id}`)
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
export const spiderRerun = (id: number, url: string) => request.post(`/spider/${id}/rerun`, { url })
export const spiderExport = () => request.get('/spider/export', { responseType: 'blob' })
export const spiderImport = (file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/spider/import', data)
}

export const taskPage = (params: any) => request.get('/spider/task/page', { params })
export const recentTaskList = (size = 5) => request.get('/spider/task/recent', { params: { size } })
export const taskStats = () => request.get('/spider/task/stats')
export const taskDetail = (id: number) => request.get(`/spider/task/${id}`)
export const taskLogs = (id: number, params: any) => request.get(`/spider/task/${id}/logs`, { params })
export const taskCancel = (id: number) => request.put(`/spider/task/${id}/cancel`)
export const taskDelete = (id: number) => request.delete(`/spider/task/${id}`)

export const searchContent = (params: any) => request.get('/search', { params })
export const searchHistory = () => request.get('/search/history')
export const syncSearchHistory = (keywords: string[]) => request.post('/search/history/sync', keywords)
export const clearSearchHistory = () => request.delete('/search/history')
export const deleteSearchHistory = (keyword: string) => request.delete('/search/history/item', { params: { keyword } })
export const searchDetail = (id: string) => request.get(`/search/${id}`)
export const searchDeleteImage = (id: string, objectName: string) =>
  request.delete(`/search/${id}/images`, { params: { objectName } })
export const searchDelete = (id: string) => request.delete(`/search/${id}`)
export const searchUpdateTags = (id: string, tags: string[]) => request.put(`/search/${id}/tags`, tags)
export const favoritePage = (params: any) => request.get('/search/favorites', { params })
export const favoriteDelete = (contentId: string) => request.delete(`/search/favorites/${contentId}`)
export const favoriteAdd = (contentId: string) => request.post(`/search/favorites/${contentId}`)

export const dictTree = () => request.get('/dict')
export const dictChildren = (parentValue: string) => request.get(`/dict/children/${parentValue}`)
export const dictCreate = (data: any) => request.post('/dict', data)
export const dictUpdate = (id: number, data: any) => request.put(`/dict/${id}`, data)
export const dictDelete = (id: number) => request.delete(`/dict/${id}`)

export const filePage = (params: any) => request.get('/file/page', { params })
