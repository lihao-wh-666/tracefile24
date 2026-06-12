import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'url'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    globals: true,
    environment: 'happy-dom',
    include: ['tests/**/*.{test,spec}.{js,jsx,ts,tsx}'],
    exclude: ['node_modules', 'dist', '.git'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'html'],
      include: [
        'src/config/sessionTimeout.js',
        'src/utils/sessionTimeout.js',
        'src/components/SessionTimeoutModal.vue'
      ],
      exclude: ['node_modules', 'tests']
    },
    setupFiles: ['./tests/setup.js'],
    testTimeout: 10000
  }
})
