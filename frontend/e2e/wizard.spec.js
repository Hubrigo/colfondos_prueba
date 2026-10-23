import { expect, test } from '@playwright/test';
import { SAFARI_17_MISSING_APIS, safari17InitScript } from './safari17-baseline.js';

test.beforeEach(async ({ page }, testInfo) => {
  if (testInfo.project.name === 'safari17') {
    await page.addInitScript(safari17InitScript, SAFARI_17_MISSING_APIS);
  }
});

const OTP = '123456';
const EVIDENCE = '../docs/evidencias';
// El backend simulado conserva estado entre navegadores: correos únicos por corrida y proyecto
// evitan un EMAIL_SAME_AS_CURRENT espurio.
const uniqueEmail = (prefix, project) => `${prefix}.${project}.${Date.now()}@dominio.com`;

/** Falla la prueba ante cualquier error JS o promesa rechazada sin manejo (INC-001). */
function trackPageErrors(page) {
  const errors = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  return errors;
}

async function identify(page, documentNumber) {
  await page.goto('/');
  await page.getByLabel('Tipo de documento').selectOption('CC');
  await page.getByLabel('Número de documento').fill(documentNumber);
  await page.getByRole('button', { name: 'Enviar código' }).click();
}

async function verifyOtp(page, code = OTP) {
  await page.getByLabel('Código de verificación').fill(code);
  await page.getByRole('button', { name: 'Validar código' }).click();
}

async function enterEmail(page, email) {
  await page.getByLabel('Nuevo correo electrónico').fill(email);
  await page.getByLabel('Confirma el nuevo correo').fill(email);
  await page.getByRole('button', { name: 'Continuar' }).click();
}

test('CA-01 / CA-15: actualización completa sin errores JS', async ({ page }, testInfo) => {
  const errors = trackPageErrors(page);
  const browser = testInfo.project.name;

  await identify(page, '1012345678');
  await expect(page.getByRole('heading', { name: 'Valida tu identidad' })).toBeVisible();
  await expect(page.getByRole('timer')).toContainText('El código vence en');
  await page.screenshot({ path: `${EVIDENCE}/frontend-${browser}-1-otp.png` });

  await verifyOtp(page);
  await enterEmail(page, uniqueEmail('nuevo', browser));
  await expect(page.getByText('nu***@dominio.com')).toBeVisible();
  await page.getByRole('button', { name: 'Confirmar' }).click();

  await expect(page.getByRole('heading', { name: '¡Listo! Actualizamos tu correo' })).toBeVisible();
  await expect(page.getByText(/^TX-\d{8}-[0-9A-F]{6}$/)).toBeVisible();
  await expect(page.getByText('Te enviamos una confirmación al nuevo correo.')).toBeVisible();
  await page.screenshot({ path: `${EVIDENCE}/frontend-${browser}-2-resultado.png` });

  expect(errors).toEqual([]);
});

test('CA-04: OTP incorrecto muestra intentos restantes', async ({ page }) => {
  await identify(page, '1012345678');
  await verifyOtp(page, '000000');
  await expect(page.getByRole('alert')).toContainText('Te quedan 2 intentos');
});

test('CA-02 / CA-09: validaciones en línea sin llamar al backend', async ({ page }) => {
  let calls = 0;
  // Solo llamadas al backend: en modo dev Vite sirve módulos como /src/api/client.js.
  page.on('request', (r) => { if (r.url().startsWith('http://localhost:8080/')) calls++; });
  await page.goto('/');
  await page.getByLabel('Número de documento').fill('12AB');
  await page.getByRole('button', { name: 'Enviar código' }).click();
  await expect(page.getByText('Ingresa solo números, entre 5 y 15 dígitos')).toBeVisible();
  expect(calls).toBe(0);
});

test('CA-13: proveedor de identidad caído muestra mensaje claro', async ({ page }) => {
  await identify(page, '1000000002');
  await expect(page.getByRole('alert')).toContainText('No pudimos validar tu identidad en este momento');
  await expect(page.getByRole('heading', { name: 'Identifícate' })).toBeVisible();
});

test('CA-14: Messaging caído no impide la actualización', async ({ page }, testInfo) => {
  await identify(page, '1000000003');
  await verifyOtp(page);
  await enterEmail(page, uniqueEmail('jorge', testInfo.project.name));
  await page.getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.getByText('La confirmación por correo puede tardar unos minutos.')).toBeVisible({ timeout: 15_000 });
});
