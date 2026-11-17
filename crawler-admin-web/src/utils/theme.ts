/**
 * 主题模式管理：default（跟随系统）/ light（白天）/ dark（黑夜·深蓝）/ black（纯黑·AMOLED）
 *
 * data-theme 在页面首帧前由 index.html 内联脚本写入（避免主题闪烁），
 * 本模块负责运行时切换、持久化（localStorage）与系统主题变化的同步。
 */
export type ThemeMode = 'default' | 'light' | 'dark' | 'black'
export type Theme = 'light' | 'dark' | 'black'

export const THEME_MODE_KEY = 'crawler-theme-mode'

type Listener = (theme: Theme) => void
const listeners = new Set<Listener>()

const mql = window.matchMedia('(prefers-color-scheme: dark)')
const systemTheme = (): Theme => (mql.matches ? 'dark' : 'light')

export const getThemeMode = (): ThemeMode => {
  const v = localStorage.getItem(THEME_MODE_KEY)
  if (v === 'light' || v === 'dark' || v === 'black') return v
  return 'default'
}

export const effectiveTheme = (): Theme => {
  const mode = getThemeMode()
  return mode === 'default' ? systemTheme() : mode
}

const apply = (theme: Theme) => {
  document.documentElement.dataset.theme = theme
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', theme === 'black' ? '#000000' : theme === 'dark' ? '#070d17' : '#f3f7f8')
  listeners.forEach(fn => {
    try { fn(theme) } catch { /* 监听器异常不影响其余监听器 */ }
  })
}

/** 切换主题模式；default = 跟随系统 */
export const setThemeMode = (mode: ThemeMode) => {
  if (mode === 'default') localStorage.removeItem(THEME_MODE_KEY)
  else localStorage.setItem(THEME_MODE_KEY, mode)
  apply(effectiveTheme())
}

/** 订阅主题变化，返回取消函数 */
export const onThemeChange = (fn: Listener) => {
  listeners.add(fn)
  return () => { listeners.delete(fn) }
}

/** 应用启动时调用：确保 data-theme 已写入，并注册系统主题变化监听（仅 default 模式生效） */
export const initTheme = () => {
  apply(effectiveTheme())
  const sync = () => { if (getThemeMode() === 'default') apply(systemTheme()) }
  if (typeof mql.addEventListener === 'function') mql.addEventListener('change', sync)
  else mql.addListener(sync)
}
