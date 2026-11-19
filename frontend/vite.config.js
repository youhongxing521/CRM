import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue2'

const proxy = { '/api': 'http://127.0.0.1:8082' }

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: [{ find: /^vue$/, replacement: 'vue/dist/vue.runtime.esm.js' }],
    dedupe: ['vue']
  },
  server: { host: '127.0.0.1', port: 5175, strictPort: true, proxy },
  preview: { host: '127.0.0.1', port: 5175, strictPort: true, proxy }
})
