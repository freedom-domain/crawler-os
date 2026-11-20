import request from './request'
import { useUserStore } from '@/stores/user'

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
export const getRagConfig = () => request.get('/ai/rag/config')
export const updateRagConfig = (data: any) => request.put('/ai/rag/config', data)
export const getVectorModels = () => request.get('/ai/rag/vector-models')
export const createVectorModel = (data: any) => request.post('/ai/rag/vector-models', data)
export const updateVectorModel = (id: number, data: any) => request.put(`/ai/rag/vector-models/${id}`, data)
export const deleteVectorModel = (id: number) => request.delete(`/ai/rag/vector-models/${id}`)
export const getChatModels = () => request.get('/ai/rag/chat-models')
export const getChatModelApiKey = (id: number) => request.get(`/ai/rag/chat-models/${id}/api-key`)
export const updateChatModelApiKey = (id: number, apiKey: string) =>
  request.put(`/ai/rag/chat-models/${id}/api-key`, { apiKey })
export const createChatModel = (data: any) => request.post('/ai/rag/chat-models', data)
export const updateChatModel = (id: number, data: any) => request.put(`/ai/rag/chat-models/${id}`, data)
export const deleteChatModel = (id: number) => request.delete(`/ai/rag/chat-models/${id}`)
export const queryRag = (data: { question: string; topK: number }) => request.post('/ai/rag/query', data)
export const chatConversationList = () => request.get('/ai/chat/conversations')
export const chatContextWindow = () => request.get('/ai/chat/conversations/context-window')
export const chatConversationCreate = () => request.post('/ai/chat/conversations')
export const chatConversationDelete = (id: number) => request.delete(`/ai/chat/conversations/${id}`)
export const chatMessages = (id: number) => request.get(`/ai/chat/conversations/${id}/messages`)
export const clearChatMessages = (id: number) => request.delete(`/ai/chat/conversations/${id}/messages`)
export const sendChatMessage = (id: number, data: { question: string; topK: number }) =>
  request.post(`/ai/chat/conversations/${id}/messages`, data)
export const streamChatMessage = (id: number, data: { question: string; topK: number }) => {
  const headers: Record<string, string> = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
  const token = useUserStore().token
  if (token) headers.Authorization = `Bearer ${token}`
  return fetch(`/api/ai/chat/conversations/${id}/messages/stream`, {
    method: 'POST',
    headers,
    body: JSON.stringify(data)
  })
}
export const resumeChatMessageStream = (conversationId: number, messageId: number, offset: number) => {
  const headers: Record<string, string> = { Accept: 'text/event-stream' }
  const token = useUserStore().token
  if (token) headers.Authorization = `Bearer ${token}`
  const params = new URLSearchParams({ offset: String(offset) })
  return fetch(
    `/api/ai/chat/conversations/${conversationId}/messages/${messageId}/stream?${params}`,
    { headers }
  )
}
export const roles = () => request.get('/user/roles')
export const assignRole = (data: any) => request.put('/user/assign-role', data)

export const spiderPage = (params: any) => request.get('/spider/page', { params })
export const spiderDetail = (id: number) => request.get(`/spider/${id}`)
export const spiderCreate = (data: any) => request.post('/spider', data)
export const spiderUpdate = (id: number, data: any) => request.put(`/spider/${id}`, data)
export const spiderDelete = (id: number) => request.delete(`/spider/${id}`)
export const spiderClearContent = (id: number) => request.delete(`/search/spiders/${id}/content`)
export const spiderClearContentBatch = (spiderIds: number[]) => request.post('/search/spiders/clear-content/batch', spiderIds)
export const spiderClearEs = (id: number) => request.delete(`/search/spiders/${id}/es`)
export const spiderStart = (id: number) => request.put(`/spider/${id}/start`)
export const spiderStop = (id: number) => request.put(`/spider/${id}/stop`)
export const spiderRun = (id: number) => request.post(`/spider/${id}/run`)
export const spiderRerun = (id: number, url: string) => request.post(`/spider/${id}/rerun`, { url })
export const spiderRunAllScheduled = () => request.post('/spider/run-scheduled-all')
export const spiderScheduledCount = () => request.get('/spider/scheduled-count')
export const spiderExport = () => request.get('/spider/export', { responseType: 'blob' })
export const spiderImport = (file: File) => {
  const data = new FormData()
  data.append('file', file)
  return request.post('/spider/import', data)
}

export const taskPage = (params: any) => request.get('/spider/task/page', { params })
export const taskConcurrency = () => request.get('/spider/task/concurrency')
export const updateTaskConcurrency = (maxConcurrency: number, urlConcurrency: number, retentionDays: number, cleanupTime: string) =>
  request.put('/spider/task/concurrency', null, { params: { maxConcurrency, urlConcurrency, retentionDays, cleanupTime } })
export const cleanupExpiredTasksNow = () => request.post('/spider/task/cleanup')
export const taskStats = () => request.get('/spider/task/stats')
export const taskActiveCount = () => request.get('/spider/task/active-count')
export const taskDetail = (id: number) => request.get(`/spider/task/${id}`)
export const taskLogs = (id: number, params: any) => request.get(`/spider/task/${id}/logs`, { params })
export const taskCancel = (id: number) => request.put(`/spider/task/${id}/cancel`)
export const taskForceCancel = (id: number) => request.put(`/spider/task/${id}/force-cancel`)
export const taskPause = (id: number) => request.put(`/spider/task/${id}/pause`)
export const taskResume = (id: number) => request.put(`/spider/task/${id}/resume`)
export const taskDelete = (id: number) => request.delete(`/spider/task/${id}`)

export const searchContent = (params: any) => request.get('/search', { params })
export const searchHistory = () => request.get('/search/history')
export const recordImagePreview = (data: any) => request.post('/search/image-history', data)
export const imagePreviewHistoryPage = (params: any) => request.get('/search/image-history', { params })
export const deleteImagePreviewHistory = (id: number) => request.delete(`/search/image-history/${id}`)
export const clearImagePreviewHistory = () => request.delete('/search/image-history')
export const investmentAccount = () => request.get('/investment/account')
export const investmentQuote = (code: string) => request.get('/investment/quote', { params: { code } })
export const investmentTrade = (data: { code: string; side: 'BUY' | 'SELL'; quantity: number }) =>
  request.post('/investment/trade', data)
export const investmentTrades = (code?: string) => request.get('/investment/trades', { params: code ? { code } : undefined })
export const fundHistory = (params: {
  code: string
  startDate: string
  endDate: string
  frequency: string
  source: 'api' | 'database'
  provider?: string
}) =>
  request.get('/investment/fund/history', { params })
export const simulateFundDca = (data: {
  code: string
  startDate: string
  endDate: string
  amount: number
  weekday: number
}) => request.post('/investment/fund/dca', data)
export const stockHistory = (params: {
  code: string
  startDate: string
  endDate: string
  source: 'api' | 'database'
  provider?: string
}) => request.get('/investment/stock/history', { params })
export const simulateStockBacktest = (data: {
  code: string
  startDate: string
  endDate: string
  amount: number
  weekday: number
}) => request.post('/investment/stock/backtest', data)
export const offExchangeFundHistory = (params: {
  code: string
  startDate: string
  endDate: string
  source: 'api' | 'database'
}) =>
  request.get('/investment/off-exchange-fund/history', { params })
export const simulateOffExchangeFundDca = (data: {
  code: string
  startDate: string
  endDate: string
  amount: number
  weekday: number
  subscriptionFeeRate: number
  redemptionFeeRate: number
}) => request.post('/investment/off-exchange-fund/dca', data)
export const fundManagementList = () => request.get('/investment/fund-management')
export const fundRefreshScheduleGet = () => request.get('/investment/fund-management/refresh-schedule')
export const fundRefreshScheduleUpdate = (frequency: 'HOURLY' | 'DAILY' | 'WEEKLY', triggerTime: string) =>
  request.put('/investment/fund-management/refresh-schedule', { frequency, triggerTime })
export const fundCatalogPage = (params: {
  keyword?: string
  fundType?: 'ONSHORE' | 'OFFSHORE'
  watched?: boolean
  page: number
  pageSize: number
}) => request.get('/investment/fund-management/catalog', { params })
export const fundCatalogDetail = (params: { fundType: 'ONSHORE' | 'OFFSHORE'; code: string }) =>
  request.get('/investment/fund-management/catalog/detail', { params })
export const fundCatalogProfileRefresh = (params: { fundType: 'ONSHORE' | 'OFFSHORE'; code: string }) =>
  request.post('/investment/fund-management/catalog/profile/refresh', null, { params })
export const fundCatalogSync = () => request.post('/investment/fund-management/catalog/sync')
export const stockCatalogPage = (params: {
  keyword?: string
  market?: '上海' | '深圳' | '北京'
  watched?: boolean
  page: number
  pageSize: number
}) => request.get('/investment/stock-management/catalog', { params })
export const stockCatalogDetail = (code: string) =>
  request.get('/investment/stock-management/catalog/detail', { params: { code } })
export const stockCatalogProfileRefresh = (code: string) =>
  request.post('/investment/stock-management/catalog/profile/refresh', null, { params: { code } })
export const stockCatalogSync = () => request.post('/investment/stock-management/catalog/sync')
export const stockWatchlistList = () => request.get('/investment/stock-management/watchlist')
export const stockWatchlistAdd = (code: string) =>
  request.post('/investment/stock-management/watchlist', null, { params: { code } })
export const stockWatchlistRemove = (code: string) =>
  request.delete('/investment/stock-management/watchlist', { params: { code } })
export const stockWatchlistRefresh = (code: string) =>
  request.post('/investment/stock-management/watchlist/refresh', null, { params: { code } })
export const stockRefreshScheduleGet = () => request.get('/investment/stock-management/refresh-schedule')
export const stockRefreshScheduleUpdate = (frequency: 'HOURLY' | 'DAILY' | 'WEEKLY', triggerTime: string) =>
  request.put('/investment/stock-management/refresh-schedule', { frequency, triggerTime })
export const historySyncTaskStart = (instrumentType: 'FUND' | 'STOCK') =>
  request.post('/investment/history-sync', null, { params: { instrumentType } })
export const historySyncTaskLatest = (instrumentType: 'FUND' | 'STOCK', page = 1, pageSize = 10) =>
  request.get('/investment/history-sync/latest', { params: { instrumentType, page, pageSize } })
export const historySyncTaskGet = (id: number, page = 1, pageSize = 10) =>
  request.get(`/investment/history-sync/${id}`, { params: { page, pageSize } })
export const historySyncTaskCancel = (id: number, page = 1, pageSize = 10) =>
  request.put(`/investment/history-sync/${id}/cancel`, null, { params: { page, pageSize } })
export const historySyncConcurrencyGet = () => request.get('/investment/history-sync/concurrency')
export const historySyncConcurrencyUpdate = (
  concurrency: number,
  providers: string[],
  requestIntervalMillis: number
) => request.put('/investment/history-sync/concurrency', null, {
  params: { concurrency, providers: providers.join(','), requestIntervalMillis }
})
export const fundWatchlistAdd = (data: { fundType: 'ONSHORE' | 'OFFSHORE'; code: string; frequency: string }) =>
  request.post('/investment/fund-management/watchlist', data)
export const fundWatchlistRemove = (params: { fundType: string; code: string; frequency?: string }) =>
  request.delete('/investment/fund-management/watchlist', { params })
export const fundHistoryRefresh = (params: { fundType: string; code: string; frequency: string }) =>
  request.post('/investment/fund-management/refresh', null, { params })
export const fundHistoryCacheClear = (params: { fundType: string; code: string; frequency: string }) =>
  request.delete('/investment/fund-management/cache', { params })
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

export const dictTree = (params?: { includeDisabled?: boolean }) => request.get('/dict', { params })
export const dictChildren = (parentValue: string) => request.get(`/dict/children/${parentValue}`)
export const dictCreate = (data: any) => request.post('/dict', data)
export const dictUpdate = (id: number, data: any) => request.put(`/dict/${id}`, data)
export const dictDelete = (id: number) => request.delete(`/dict/${id}`)

export const filePage = (params: any) => request.get('/file/page', { params })

export const cfIpList = () => request.get('/cf-ip/')
export const cfIpPage = (params: any) => request.get('/cf-ip/page', { params })
export const cfIpSummary = () => request.get('/cf-ip/summary')
export const cfIpFilters = () => request.get('/cf-ip/filters')
export const cfIpCandidateIds = (status?: string) => request.get('/cf-ip/ids', { params: { status } })
export const cfIpAvailableAddresses = () => request.get('/cf-ip/available/ips')
export const cfIpAdd = (ips: string) => request.post('/cf-ip/', { ips })
export const cfIpProbe = (ids: number[], tlsServerName: string) =>
  request.post('/cf-ip/probe', { ids, tlsServerName }, { timeout: 180_000 })
export const cfIpDelete = (id: number) => request.delete(`/cf-ip/${id}`)
export const cfIpDeleteBatch = (ids: number[]) => request.delete('/cf-ip/batch', { data: { ids } })
export const cfIpRanges = () => request.get('/cf-ip/ranges')
export const cfIpRangePage = (params: any) => request.get('/cf-ip/ranges/page', { params })
export const cfIpInitializeRanges = () => request.post('/cf-ip/ranges/initialize')
export const cfIpUpdateRangeRegion = (id: number, region: string) =>
  request.put(`/cf-ip/ranges/${id}/region`, { region })
export const cfIpUpdateRangeEnabled = (id: number, enabled: boolean) =>
  request.put(`/cf-ip/ranges/${id}/enabled`, { enabled })
export const cfIpImportEnabledIpv4 = () =>
  request.post('/cf-ip/ranges/import-enabled-ipv4', {}, { timeout: 600_000 })
export const cfIpProbeRanges = (samplesPerRange: number, tlsServerName: string) =>
  request.post('/cf-ip/ranges/probe', { samplesPerRange, tlsServerName }, { timeout: 180_000 })
export const cfIpRangeProbeSchedule = () => request.get('/cf-ip/ranges/probe-schedule')
export const cfIpUpdateRangeProbeSchedule = (data: {
  enabled: boolean
  frequency: 'EVERY_5_MINUTES' | 'EVERY_15_MINUTES' | 'EVERY_30_MINUTES' | 'HOURLY' | 'DAILY' | 'WEEKLY'
  samplesPerRange: number
  tlsServerName: string
}) => request.put('/cf-ip/ranges/probe-schedule', data)
