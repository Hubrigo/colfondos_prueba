// Prueba de humo del orquestador en perfil `sim` (escenarios de E2 §9).
// Uso: node scripts/smoke-backend.mjs [baseUrl]   (por defecto http://localhost:8080)
// El script reutiliza el documento del camino feliz más de 5 veces: arranca el backend con
// RATE_LIMIT_MAX_FLOWS_PER_DOCUMENT=50 o el limitador responderá 429 (y estará bien que lo haga).
import { randomUUID } from 'node:crypto';

const BASE = (process.argv[2] ?? 'http://localhost:8080') + '/api/v1/contact-update/flows';
const OTP = process.env.SIM_FIXED_OTP ?? '123456';
const SAFARI_17 =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';

async function call(method, path, body, headers = {}) {
  const started = Date.now();
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', 'User-Agent': SAFARI_17, ...headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => null);
  return { status: res.status, json, correlationId: res.headers.get('x-correlation-id'), ms: Date.now() - started };
}

const results = [];
function check(name, ok, info) {
  results.push(ok);
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${info ? `  → ${info}` : ''}`);
}

async function start(documentNumber, headers) {
  return call('POST', '', { documentType: 'CC', documentNumber }, headers);
}

async function verifiedFlow(documentNumber) {
  const s = await start(documentNumber);
  await call('POST', `/${s.json.flowId}/otp/verify`, { code: OTP });
  return s.json.flowId;
}

async function updateEmail(flowId, email, key = randomUUID()) {
  return call('PUT', `/${flowId}/email`, { newEmail: email, confirmEmail: email }, { 'Idempotency-Key': key });
}

// CA-01 + CA-16: camino feliz con correlationId propagado
{
  const cid = 'smoke-' + randomUUID();
  const s = await start('1012345678', { 'X-Correlation-Id': cid });
  check('CA-01 inicio de flujo 202', s.status === 202 && s.json.otpChannel === 'SMS', `otpExpiresAt=${s.json.otpExpiresAt}`);
  check('CA-16 correlationId devuelto', s.correlationId === cid);
  const v = await call('POST', `/${s.json.flowId}/otp/verify`, { code: OTP });
  check('CA-01 OTP válido', v.status === 200 && v.json.verified === true);
  const key = randomUUID();
  const u = await updateEmail(s.json.flowId, 'Nuevo.Correo@Dominio.COM', key);
  check('CA-01 correo actualizado', u.status === 200 && u.json.status === 'UPDATED' && u.json.notificationStatus === 'SENT',
    `${u.json.transactionId} ${u.json.maskedEmail} updatedAt=${u.json.updatedAt}`);
  const again = await updateEmail(s.json.flowId, 'Nuevo.Correo@Dominio.COM', key);
  check('Idempotencia: misma clave, misma transacción', again.json.transactionId === u.json.transactionId);
  const audit = await call('GET', `/${s.json.flowId}/audit`);
  const types = audit.json.map((e) => e.type).join(',');
  check('CA-01 trazabilidad registrada', types.includes('EMAIL_UPDATED') && types.includes('NOTIFICATION_SENT'), types);
  const raw = JSON.stringify(audit.json);
  check('CA-11 auditoría sin OTP ni correo en claro', !raw.includes(OTP) && !raw.toLowerCase().includes('nuevo.correo'));
}

// CA-02 / CA-08 / CA-09: validaciones
{
  const bad = await call('POST', '', { documentType: 'CC', documentNumber: '12AB' });
  check('CA-02 documento inválido 400', bad.status === 400 && bad.json.code === 'VALIDATION_ERROR', JSON.stringify(bad.json.errors));
  const flowId = await verifiedFlow('1012345678');
  const invalid = await updateEmail(flowId, 'a@b');
  check('CA-08 correo inválido 400', invalid.status === 400);
  const mismatch = await call('PUT', `/${flowId}/email`, { newEmail: 'a@dominio.com', confirmEmail: 'b@dominio.com' },
    { 'Idempotency-Key': randomUUID() });
  check('CA-09 correos no coinciden 400', mismatch.status === 400, mismatch.json.errors?.[0]?.message);
  const same = await updateEmail(await verifiedFlow('1000000004'), 'actual@correo.com');
  check('CA-08 correo igual al actual 422', same.status === 422 && same.json.code === 'EMAIL_SAME_AS_CURRENT');
  const unknown = await call('POST', '', { documentType: 'CC', documentNumber: '1012345678', extra: 'x' });
  check('Propiedades desconocidas rechazadas 400', unknown.status === 400);
}

// CA-03: sin enumeración
{
  const real = await start('1012345678');
  const ghost = await start('5555555555');
  check('CA-03 misma respuesta exista o no', real.status === ghost.status && real.json.message === ghost.json.message
    && Object.keys(real.json).join() === Object.keys(ghost.json).join());
}

// CA-04 / CA-05: OTP incorrecto y bloqueo
{
  const s = await start('1012345678');
  const r1 = await call('POST', `/${s.json.flowId}/otp/verify`, { code: '000000' });
  check('CA-04 OTP incorrecto 422 con intentos', r1.status === 422 && r1.json.remainingAttempts === 2, r1.json.detail);
  await call('POST', `/${s.json.flowId}/otp/verify`, { code: '000000' });
  const r3 = await call('POST', `/${s.json.flowId}/otp/verify`, { code: '000000' });
  check('CA-05 bloqueo tras 3 intentos 423', r3.status === 423 && r3.json.code === 'OTP_BLOCKED');
  const after = await call('POST', `/${s.json.flowId}/otp/verify`, { code: OTP });
  check('CA-05 bloqueado aun con el código correcto', after.status === 423);
}

// Reenvío antes de tiempo
{
  const s = await start('1012345678');
  const r = await call('POST', `/${s.json.flowId}/otp/resend`);
  check('Reenvío antes de 30 s → 429', r.status === 429 && r.json.code === 'OTP_RESEND_TOO_SOON', r.json.detail);
}

// CA-10: actualizar sin validar identidad
{
  const s = await start('1012345678');
  const u = await updateEmail(s.json.flowId, 'otro@dominio.com');
  check('CA-10 actualización sin OTP → 403', u.status === 403 && u.json.code === 'IDENTITY_NOT_VERIFIED');
}

// CA-12: timeout transitorio en Customer Data, reintento exitoso e idempotente
{
  const u = await updateEmail(await verifiedFlow('1000000001'), 'luis.nuevo@dominio.com');
  check('CA-12 timeout + reintento → 200', u.status === 200 && u.json.status === 'UPDATED', `${u.ms} ms`);
}

// CA-13: proveedor de identidad caído
{
  const s = await start('1000000002');
  check('CA-13 Identity caído → 503', s.status === 503 && s.json.code === 'PROVIDER_UNAVAILABLE',
    `${s.json.provider}, ${s.ms} ms`);
}

// CA-14: Messaging caído no revierte
{
  const u = await updateEmail(await verifiedFlow('1000000003'), 'jorge.nuevo@dominio.com');
  check('CA-14 Messaging caído → UPDATED + PENDING', u.status === 200 && u.json.notificationStatus === 'PENDING', `${u.ms} ms`);
}

// Flujo inexistente
{
  const r = await call('POST', `/${randomUUID()}/otp/verify`, { code: OTP });
  check('Flujo inexistente → 404', r.status === 404 && r.json.code === 'FLOW_NOT_FOUND');
}

const failed = results.filter((ok) => !ok).length;
console.log(`\n${results.length - failed}/${results.length} verificaciones OK`);
process.exit(failed ? 1 : 0);
