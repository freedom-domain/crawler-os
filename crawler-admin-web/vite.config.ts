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
    // 分包：第三方库拆出独立 chunk，利用浏览器缓存
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia']
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
