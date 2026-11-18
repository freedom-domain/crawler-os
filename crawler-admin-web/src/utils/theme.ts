/**
 * 主题模式管理：仅支持 light（浅色）和 black（纯黑）。
 *
 * data-theme 在页面首帧前由 index.html 内联脚本写入（避免主题闪烁），
 * 本模块负责运行时切换和持久化（localStorage）。
 */
export type ThemeMode = 'light' | 'black'
export type Theme = 'light' | 'black'

export const THEME_MODE_KEY = 'crawler-theme-mode'

type Listener = (theme: Theme) => void
const listeners = new Set<Listener>()

export const getThemeMode = (): ThemeMode => {
  const v = localStorage.getItem(THEME_MODE_KEY)
  if (v === 'light' || v === 'black') return v
  // 兼容旧的 dark 存储值：迁移为 black。
  if (v === 'dark') localStorage.setItem(THEME_MODE_KEY, 'black')
  else localStorage.removeItem(THEME_MODE_KEY)
  return v === 'dark' ? 'black' : 'light'
}

export const effectiveTheme = (): Theme => {
  return getThemeMode()
}

const apply = (theme: Theme) => {
  document.documentElement.dataset.theme = theme
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', theme === 'black' ? '#000000' : '#f3f7f8')
  listeners.forEach(fn => {
    try { fn(theme) } catch { /* 监听器异常不影响其余监听器 */ }
  })
}

/** 切换主题模式 */
export const setThemeMode = (mode: ThemeMode) => {
  localStorage.setItem(THEME_MODE_KEY, mode)
  apply(mode)
}

/** 订阅主题变化，返回取消函数 */
export const onThemeChange = (fn: Listener) => {
  listeners.add(fn)
  return () => { listeners.delete(fn) }
}

/** 应用启动时调用：确保 data-theme 已写入 */
export const initTheme = () => {
  apply(effectiveTheme())
}
