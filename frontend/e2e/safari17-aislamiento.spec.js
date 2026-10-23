import { expect, test } from '@playwright/test';
import { SAFARI_17_MISSING_APIS, safari17InitScript } from './safari17-baseline.js';

// E6, aislamiento por bisección sobre la línea base emulada: ¿qué API ausente causa la falla?
// Corre solo en WebKit (motor de Safari).
const CULPRIT = 'Promise.withResolvers';
const scenarios = [
  ['solo sin Promise.withResolvers', SAFARI_17_MISSING_APIS.filter(([api]) => api === CULPRIT)],
  ['sin todas las demás APIs, con Promise.withResolvers', SAFARI_17_MISSING_APIS.filter(([api]) => api !== CULPRIT)],
];

for (const [name, apis] of scenarios) {
  test(`E6 aislamiento: ${name}`, async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'webkit', 'bisección solo en WebKit');
    await page.addInitScript(safari17InitScript, apis);
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));

    await page.goto('/');
    await page.getByLabel('Número de documento').fill('1012345678');
    await page.getByRole('button', { name: 'Enviar código' }).click();
    await page.getByLabel('Código de verificación').fill('123456');
    await page.getByRole('button', { name: 'Validar código' }).click();
    const email = `aislamiento.${Date.now()}@dominio.com`;
    await page.getByLabel('Nuevo correo electrónico').fill(email);
    await page.getByLabel('Confirma el nuevo correo').fill(email);
    await page.getByRole('button', { name: 'Continuar' }).click();
    await page.getByRole('button', { name: 'Confirmar' }).click();

    await expect(page.getByRole('heading', { name: '¡Listo! Actualizamos tu correo' })).toBeVisible({ timeout: 5_000 });
    expect(errors).toEqual([]);
  });
}
