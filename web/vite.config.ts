import path from 'node:path'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  build: {
    rolldownOptions: {
      output: {
        // Separa las librerías de terceros del código de la app para que la caché del navegador
        // conserve el vendor entre despliegues y el chunk principal quede pequeño.
        advancedChunks: {
          groups: [
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|scheduler)[\\/]/ },
            { name: 'ui', test: /node_modules[\\/](@radix-ui|sonner|react-day-picker|@remixicon|tailwind-variants|tailwind-merge|clsx)[\\/]/ },
            { name: 'data', test: /node_modules[\\/](@tanstack|date-fns)[\\/]/ },
          ],
        },
      },
    },
  },
})
