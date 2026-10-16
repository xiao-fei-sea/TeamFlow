import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理到后端（默认 8080，本机被占用时以 BACKEND_PORT 环境变量覆盖）
      '/api': {
        target: `http://localhost:${process.env.BACKEND_PORT || '8080'}`,
        changeOrigin: true,
      },
      '/ws': {
        target: `ws://localhost:${process.env.BACKEND_PORT || '8080'}`,
        ws: true,
      },
    },
  },
})
