import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { proxy: { '/api': 'http://127.0.0.1:18093', '/iam': 'http://127.0.0.1:18093' } },
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: { element: ['element-plus'], vue: ['vue', 'vue-router'], http: ['axios'] },
      },
    },
  },
  test: { environment: 'jsdom' },
})
