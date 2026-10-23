import { uuid } from '../utils/id.js';
import { RESTART_CODES } from '../errors/messages.js';

// Pasos del asistente (E2 §2.1).
export const STEPS = {
  IDENTIFY: 'IDENTIFY',
  OTP: 'OTP',
  EMAIL: 'EMAIL',
  CONFIRM: 'CONFIRM',
  RESULT: 'RESULT',
  BLOCKED: 'BLOCKED',
};

export const STEP_ORDER = [STEPS.IDENTIFY, STEPS.OTP, STEPS.EMAIL, STEPS.CONFIRM, STEPS.RESULT];

export function initialState() {
  return {
    step: STEPS.IDENTIFY,
    // Un correlationId por transacción del afiliado: el mismo en todas las llamadas del flujo (CA-16).
    correlationId: uuid(),
    pending: false,
    error: null,
    document: { documentType: 'CC', documentNumber: '' },
    flow: null,
    otpExpired: false,
    email: { newEmail: '', confirmEmail: '' },
    idempotencyKey: null,
    result: null,
  };
}

export function reducer(state, action) {
  switch (action.type) {
    case 'REQUEST':
      return { ...state, pending: true, error: null };

    case 'FAILURE':
      return failure(state, action.error);

    case 'FLOW_STARTED':
      return {
        ...state,
        pending: false,
        step: STEPS.OTP,
        document: action.document,
        flow: action.flow,
        otpExpired: false,
      };

    case 'OTP_RESENT':
      // El afiliado no pierde lo ya ingresado al reenviar el código (CA-06).
      return { ...state, pending: false, flow: action.flow, otpExpired: false };

    case 'OTP_EXPIRED_LOCALLY':
      // Mismo objeto si ya estaba expirado: evita renders en bucle desde el contador.
      return state.otpExpired ? state : { ...state, otpExpired: true };

    case 'OTP_VERIFIED':
      return { ...state, pending: false, step: STEPS.EMAIL };

    case 'EMAIL_CAPTURED': {
      // La clave de idempotencia se genera una vez por correo confirmado y se reutiliza en
      // los reintentos: si el afiliado cambia el correo, la clave también cambia.
      const sameEmail = state.idempotencyKey && action.email.newEmail === state.email.newEmail;
      return {
        ...state,
        step: STEPS.CONFIRM,
        error: null,
        email: action.email,
        idempotencyKey: sameEmail ? state.idempotencyKey : uuid(),
      };
    }

    case 'BACK_TO_EMAIL':
      return { ...state, step: STEPS.EMAIL, error: null };

    case 'UPDATED':
      return { ...state, pending: false, step: STEPS.RESULT, result: action.result };

    case 'RESTART':
      return { ...initialState(), error: action.error ?? null };

    case 'DISMISS_ERROR':
      return { ...state, error: null };

    default:
      return state;
  }
}

function failure(state, error) {
  if (error?.code === 'OTP_BLOCKED') {
    return { ...state, pending: false, step: STEPS.BLOCKED, error };
  }
  if (RESTART_CODES.has(error?.code)) {
    return { ...initialState(), error };
  }
  if (error?.code === 'OTP_EXPIRED') {
    return { ...state, pending: false, otpExpired: true, error };
  }
  return { ...state, pending: false, error };
}
