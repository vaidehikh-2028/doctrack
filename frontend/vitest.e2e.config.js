import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * Full-stack tests: the real React components driving a running DocTrack backend.
 * jsdom's origin is set to the backend's, so fetch/XHR are same-origin exactly as in production.
 *
 *   mvn spring-boot:run            # fresh H2 database with the seeded demo users
 *   DOCTRACK_E2E_URL=http://localhost:8080 npm run test:e2e
 */
const url = process.env.DOCTRACK_E2E_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify(url) },
  test: {
    environment: 'jsdom',
    environmentOptions: { jsdom: { url } },
    globals: true,
    setupFiles: './src/test/setup.js',
    include: ['src/**/*.e2e.test.jsx'],
    testTimeout: 60_000,
    fileParallelism: false,
  },
});
