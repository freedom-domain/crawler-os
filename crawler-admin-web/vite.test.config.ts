import base from './vite.config.ts'
import { defineConfig, mergeConfig } from 'vite'

export default mergeConfig(base, defineConfig({
  cacheDir: '/tmp/vite_test_cache/node_modules/.vite',
  server: { host: true, port: 58091, strictPort: true, allowedHosts: true }
}))
