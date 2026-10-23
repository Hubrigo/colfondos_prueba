import { defineConfig, devices } from '@playwright/test';
import { SAFARI_17_USER_AGENT } from './e2e/safari17-baseline.js';

// Pruebas end-to-end contra el backend real en perfil `sim` y el frontend de Vite.
// Navegadores de referencia de QA (doc. 09): Chrome, Edge y Safari (motor WebKit).
// Requiere `mvn -f ../backend package` previo (usa el jar).
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'edge', use: { ...devices['Desktop Edge'], channel: 'msedge' } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    // WebKit con la línea base de JavaScript de Safari 17.0 (ver e2e/safari17-baseline.js).
    // La emulación de APIs la aplica cada spec con addInitScript.
    { name: 'safari17', use: { ...devices['Desktop Safari'], userAgent: SAFARI_17_USER_AGENT } },
  ],
  webServer: [
    {
      command: 'java -jar ../backend/target/contact-update-orchestrator-0.1.0-SNAPSHOT.jar --spring.profiles.active=sim',
      url: 'http://localhost:8080/actuator/health',
      env: { RATE_LIMIT_MAX_FLOWS_PER_DOCUMENT: '100', RATE_LIMIT_MAX_FLOWS_PER_IP: '500' },
      reuseExistingServer: false,
      timeout: 90_000,
    },
    {
      command: 'npm run dev',
      url: 'http://localhost:5173',
      reuseExistingServer: false,
      timeout: 60_000,
    },
  ],
});
