import type { Router } from 'vue-router'

/** 未指定 redirect 或 redirect 非法时的默认目标页 */
export const DEFAULT_AUTH_REDIRECT = '/dashboard'

/** 根据 JWT 的 exp 判断本地 token 是否仍在有效期内 */
export const hasValidToken = (): boolean => {
  const token = localStorage.getItem('token')
  if (!token) return false
  try {
    const payload = token.split('.')[1]
    const binary = atob(payload)
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
    const decoded = JSON.parse(new TextDecoder('utf-8').decode(bytes))
    return Number(decoded.exp) * 1000 > Date.now()
  } catch {
    return false
  }
}

/**
 * 解析登录后的目标页：优先 redirect 查询参数，仅接受站内路径且不能回到登录页，否则跳默认页。
 * 登录页与路由守卫共用，避免两处判断逻辑分叉。
 */
export const resolveAuthRedirect = (router: Router, redirect: unknown): string => {
  if (typeof redirect !== 'string' || !redirect.startsWith('/') || redirect.startsWith('//')) {
    return DEFAULT_AUTH_REDIRECT
  }
  const resolved = router.resolve(redirect)
  return resolved.matched.length && resolved.path !== '/login'
    ? resolved.fullPath
    : DEFAULT_AUTH_REDIRECT
}
