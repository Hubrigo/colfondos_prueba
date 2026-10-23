import { expect, test } from '@playwright/test';
import { SAFARI_17_MISSING_APIS, safari17InitScript } from './safari17-baseline.js';

// E6: defecto reportado por QA. "Safari 17 falla al confirmar la actualización; el API
// retorna HTTP 200, pero la consola muestra Promise rejected - Unhandled JavaScript exception."
// Se ejecuta en el proyecto `safari17` (WebKit + línea base de Safari 17.0) y también en
// Chromium y Edge como control.

const EVIDENCE = '../docs/evidencias';

test.beforeEach(async ({ page }, testInfo) => {
  if (testInfo.project.name === 'safari17') {
    await page.addInitScript(safari17InitScript, SAFARI_17_MISSING_APIS);
  }
});

test('E6: confirmar la actualización termina sin errores JS aunque el API responda 200', async ({ page }, testInfo) => {
  const browser = testInfo.project.name;
  const errors = [];
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
  page.on('console', (msg) => { if (msg.type() === 'error') errors.push(`console: ${msg.text()}`); });

  await page.goto('/');
  // La emulación está activa: en safari17 estas APIs no existen.
  const withResolvers = await page.evaluate(() => typeof Promise.withResolvers);
  expect(withResolvers).toBe(browser === 'safari17' ? 'undefined' : 'function');

  await page.getByLabel('Número de documento').fill('1012345678');
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await page.getByLabel('Código de verificación').fill('123456');
  await page.getByRole('button', { name: 'Validar código' }).click();
  const email = `safari.${browser}.${Date.now()}@dominio.com`;
  await page.getByLabel('Nuevo correo electrónico').fill(email);
  await page.getByLabel('Confirma el nuevo correo').fill(email);
  await page.getByRole('button', { name: 'Continuar' }).click();

  const updateResponse = page.waitForResponse((r) => r.url().endsWith('/email') && r.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Confirmar' }).click();
  expect((await updateResponse).status(), 'el API responde 200, igual que en el reporte de QA').toBe(200);

  const done = page.getByRole('heading', { name: '¡Listo! Actualizamos tu correo' });
  await done.waitFor({ timeout: 5_000 }).catch(() => {});
  await page.screenshot({ path: `${EVIDENCE}/e6-${browser}-confirmacion.png` });

  expect(errors, 'sin errores JS ni promesas rechazadas sin manejo').toEqual([]);
  await expect(done).toBeVisible();
});
