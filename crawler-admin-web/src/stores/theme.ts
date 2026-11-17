import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ThemeMode, Theme, getThemeMode, effectiveTheme, setThemeMode, onThemeChange } from '@/utils/theme'

/**
 * 主题 store：驱动顶部「默认 / 白天 / 黑夜 / 纯黑」切换控件
 * - mode: 用户选择的模式（default = 跟随系统）
 * - theme: 当前实际生效的主题（default 模式下随系统变化）
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(getThemeMode())
  const theme = ref<Theme>(effectiveTheme())

  // setup store 的初始化函数只执行一次（单例），在此订阅模块级事件
  onThemeChange(t => { theme.value = t })

  // 纯黑也属于暗色系（图表/暗色分支统一按 isDark 处理）
  const isDark = computed(() => theme.value !== 'light')

  const choose = (m: ThemeMode) => {
    mode.value = m
    setThemeMode(m)
  }

  return { mode, theme, isDark, choose }
})
