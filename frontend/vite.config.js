import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  build: {
    // Navegadores de referencia de QA (doc. 09). Ojo: el target solo transpila sintaxis;
    // las APIs nuevas (p. ej. Promise.withResolvers) NO se polyfillean (lección de INC-001).
    target: ['es2020', 'chrome111', 'edge111', 'safari16'],
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{js,jsx}'],
    setupFiles: ['./src/test-setup.js'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/**/*.test.{js,jsx}', 'src/main.jsx', 'src/test-setup.js'],
      reporter: ['text-summary', 'html'],
    },
  },
});
