import { describe, expect, it } from 'vitest';
import { initialState, reducer, STEPS } from './reducer.js';

const flow = { flowId: 'f-1', otpExpiresAt: '2026-10-22T21:05:00-05:00', resendAvailableAt: '2026-10-22T21:00:30-05:00' };
const doc = { documentType: 'CC', documentNumber: '1012345678' };

function at(step, overrides = {}) {
  return { ...initialState(), step, flow, document: doc, ...overrides };
}

describe('reducer del asistente', () => {
  it('recorre el camino feliz', () => {
    let s = reducer(initialState(), { type: 'REQUEST' });
    expect(s.pending).toBe(true);
    s = reducer(s, { type: 'FLOW_STARTED', document: doc, flow });
    expect(s).toMatchObject({ step: STEPS.OTP, pending: false, flow });
    s = reducer(s, { type: 'OTP_VERIFIED' });
    expect(s.step).toBe(STEPS.EMAIL);
    s = reducer(s, { type: 'EMAIL_CAPTURED', email: { newEmail: 'a@d.co', confirmEmail: 'a@d.co' } });
    expect(s.step).toBe(STEPS.CONFIRM);
    expect(s.idempotencyKey).toBeTruthy();
    s = reducer(s, { type: 'UPDATED', result: { transactionId: 'TX-1' } });
    expect(s).toMatchObject({ step: STEPS.RESULT, result: { transactionId: 'TX-1' } });
  });

  it('conserva un correlationId por transacción y lo renueva al reiniciar', () => {
    const s = at(STEPS.EMAIL);
    expect(reducer(s, { type: 'OTP_VERIFIED' }).correlationId).toBe(s.correlationId);
    expect(reducer(s, { type: 'RESTART' }).correlationId).not.toBe(s.correlationId);
  });

  it('reutiliza la Idempotency-Key si el correo no cambia y la renueva si cambia', () => {
    const email = { newEmail: 'a@d.co', confirmEmail: 'a@d.co' };
    const first = reducer(at(STEPS.EMAIL), { type: 'EMAIL_CAPTURED', email });
    const back = reducer(first, { type: 'BACK_TO_EMAIL' });

    expect(reducer(back, { type: 'EMAIL_CAPTURED', email }).idempotencyKey).toBe(first.idempotencyKey);
    expect(reducer(back, { type: 'EMAIL_CAPTURED', email: { newEmail: 'b@d.co', confirmEmail: 'b@d.co' } })
      .idempotencyKey).not.toBe(first.idempotencyKey);
  });

  it('OTP_BLOCKED lleva a la pantalla de bloqueo', () => {
    expect(reducer(at(STEPS.OTP), { type: 'FAILURE', error: { code: 'OTP_BLOCKED' } }).step).toBe(STEPS.BLOCKED);
  });

  it.each(['FLOW_NOT_FOUND', 'IDENTITY_NOT_VERIFIED'])('%s reinicia el asistente mostrando el error', (code) => {
    const s = reducer(at(STEPS.CONFIRM), { type: 'FAILURE', error: { code } });
    expect(s).toMatchObject({ step: STEPS.IDENTIFY, flow: null, error: { code } });
  });

  it('OTP_EXPIRED conserva el paso y marca el código como expirado (CA-06)', () => {
    const s = reducer(at(STEPS.OTP, { pending: true }), { type: 'FAILURE', error: { code: 'OTP_EXPIRED' } });
    expect(s).toMatchObject({ step: STEPS.OTP, otpExpired: true, pending: false });
  });

  it('otros errores conservan el paso y los datos ingresados', () => {
    const s = reducer(at(STEPS.CONFIRM, { pending: true }), { type: 'FAILURE', error: { code: 'PROVIDER_TIMEOUT' } });
    expect(s).toMatchObject({ step: STEPS.CONFIRM, pending: false, flow, error: { code: 'PROVIDER_TIMEOUT' } });
  });

  it('reenviar el OTP limpia la expiración sin perder el documento', () => {
    const s = reducer(at(STEPS.OTP, { otpExpired: true }), { type: 'OTP_RESENT', flow: { ...flow, flowId: 'f-1' } });
    expect(s).toMatchObject({ otpExpired: false, document: doc });
  });

  it('OTP_EXPIRED_LOCALLY es idempotente: devuelve el mismo objeto (evita renders en bucle)', () => {
    const expired = reducer(at(STEPS.OTP), { type: 'OTP_EXPIRED_LOCALLY' });
    expect(reducer(expired, { type: 'OTP_EXPIRED_LOCALLY' })).toBe(expired);
  });
});
