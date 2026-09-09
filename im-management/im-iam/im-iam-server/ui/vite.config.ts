import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': 'http://127.0.0.1:18090', '/login': 'http://127.0.0.1:18090' } },
  build: { outDir: '../target/classes/static', emptyOutDir: true },
  test: { environment: 'jsdom' },
})
