import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ThemeMode, Theme, getThemeMode, effectiveTheme, setThemeMode, onThemeChange } from '@/utils/theme'

/**
 * 主题 store：仅驱动浅色 / 纯黑切换控件。
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(getThemeMode())
  const theme = ref<Theme>(effectiveTheme())

  // setup store 的初始化函数只执行一次（单例），在此订阅模块级事件
  onThemeChange(t => { theme.value = t })

  const isDark = computed(() => theme.value !== 'light')

  const choose = (m: ThemeMode) => {
    mode.value = m
    setThemeMode(m)
  }

  return { mode, theme, isDark, choose }
})
