import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import path from 'node:path'

// https://vite.dev/config/
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8080/'

export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  server:{
    proxy:{
      '/api':{
        target: apiTarget,
        changeOrigin:true,
        rewrite:(path) => path.replace(/^\/api/,'')

      },
      '/uploads':{
        target: apiTarget,
        changeOrigin:true
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
