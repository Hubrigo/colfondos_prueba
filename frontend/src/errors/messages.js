// Traducción de `code` (E2 §5.2) a mensajes para el afiliado. Nunca se muestra un error técnico.

const MESSAGES = {
  VALIDATION_ERROR: 'Revisa los datos ingresados.',
  FLOW_NOT_FOUND: 'Tu sesión expiró. Vuelve a empezar.',
  OTP_EXPIRED: 'Tu código expiró. Solicita uno nuevo.',
  OTP_BLOCKED: 'Por seguridad bloqueamos el proceso.',
  RATE_LIMITED: 'Has hecho demasiados intentos. Intenta de nuevo más tarde.',
  IDENTITY_NOT_VERIFIED: 'Debes validar tu identidad. Vuelve a empezar.',
  EMAIL_SAME_AS_CURRENT: 'El nuevo correo debe ser distinto al actual.',
  PROVIDER_TIMEOUT: 'El servicio está tardando más de lo normal. Intenta de nuevo.',
  PROVIDER_UNAVAILABLE: 'No pudimos validar tu identidad en este momento. Intenta más tarde.',
  CLIENT_TIMEOUT: 'La solicitud tardó demasiado. Revisa tu conexión e intenta de nuevo.',
  NETWORK_ERROR: 'No pudimos conectarnos. Revisa tu conexión e intenta de nuevo.',
};

const GENERIC = 'Ocurrió un error inesperado. Intenta de nuevo.';

/** Códigos con mensaje dinámico del backend (intentos restantes, segundos de espera). */
const USE_SERVER_DETAIL = new Set(['OTP_INVALID', 'OTP_RESEND_TOO_SOON']);

export function messageFor(error) {
  if (!error) return '';
  if (USE_SERVER_DETAIL.has(error.code) && error.message) return error.message;
  return MESSAGES[error.code] ?? GENERIC;
}

/** Errores que obligan a reiniciar el asistente porque el flujo del servidor ya no sirve. */
export const RESTART_CODES = new Set(['FLOW_NOT_FOUND', 'IDENTITY_NOT_VERIFIED']);
