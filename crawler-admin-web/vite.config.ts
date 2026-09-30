import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 组件按需引入：模板中用到的组件自动导入及其样式
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })]
    }),
    // ElMessage / ElMessageBox 等 API 自动导入
    AutoImport({
      resolvers: [ElementPlusResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  css: {
    preprocessorOptions: {
      // Element Plus 按需引入的组件样式基于 sass 变量编译
      sass: {
        additionalData: `@use "element-plus/theme-chalk/src/common/var.scss" as * with ($colors: (
          "primary": ("base": "#0f9f9a")
        ));`
      }
    }
  },
  build: {
    // 分包策略：
    // 1. 核心框架（vue/vue-router/pinia）— 每个页面都要，优先加载
    // 2. Element Plus — 体积最大，单独拆出，按需加载时不阻塞首屏
    // 3. 各路由视图 — 按页面拆 chunk，只加载当前页
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          // node_modules 下的第三方库
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) {
              return 'vendor-element-plus'
            }
            if (id.includes('vue') || id.includes('pinia') || id.includes('vue-router')) {
              return 'vendor-vue'
            }
            return 'vendor'
          }
          // 路由视图按页面拆 chunk
          if (id.includes('/views/')) {
            const viewName = id.split('/views/')[1].split(/\.|\/)[0]
            return `view-${viewName.toLowerCase()}`
          }
          return undefined
        }
      }
    }
  },
  server: {
    host: true,
    port: 58080,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
})
