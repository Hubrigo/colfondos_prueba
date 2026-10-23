// Reglas de validación del frontend (E1 §3.1). Son la experiencia; el backend es la autoridad.
// Cada función devuelve un mensaje de error o null.

export const DOCUMENT_TYPES = [
  { value: 'CC', label: 'Cédula de ciudadanía' },
  { value: 'CE', label: 'Cédula de extranjería' },
  { value: 'PA', label: 'Pasaporte' },
  { value: 'TI', label: 'Tarjeta de identidad' },
];

// Misma expresión que el backend (UpdateEmailRequest.EMAIL_PATTERN): dominio con al menos un punto.
const EMAIL_RE =
  /^[A-Za-z0-9._%+-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;

export function validateDocumentType(type) {
  return DOCUMENT_TYPES.some((t) => t.value === type) ? null : 'Selecciona el tipo de documento';
}

export function validateDocumentNumber(type, number) {
  const value = (number ?? '').trim();
  if (!value) return 'Ingresa el número de documento';
  const pattern = type === 'PA' ? /^[A-Za-z0-9]{5,15}$/ : /^\d{5,15}$/;
  if (pattern.test(value)) return null;
  return type === 'PA'
    ? 'Ingresa entre 5 y 15 letras o números'
    : 'Ingresa solo números, entre 5 y 15 dígitos';
}

export function validateOtp(code) {
  return /^\d{6}$/.test(code ?? '') ? null : 'El código tiene 6 dígitos';
}

export function normalizeEmail(email) {
  const value = (email ?? '').trim();
  const at = value.lastIndexOf('@');
  return at < 0 ? value : value.slice(0, at) + value.slice(at).toLowerCase();
}

export function validateEmail(email) {
  const value = normalizeEmail(email);
  if (!value) return 'Ingresa el nuevo correo';
  if (value.length > 254) return 'El correo no puede superar 254 caracteres';
  return EMAIL_RE.test(value) ? null : 'Ingresa un correo válido';
}

export function validateEmailConfirmation(email, confirmation) {
  if (!(confirmation ?? '').trim()) return 'Confirma el nuevo correo';
  return normalizeEmail(email).toLowerCase() === normalizeEmail(confirmation).toLowerCase()
    ? null
    : 'Los correos no coinciden';
}

/** Enmascara para mostrar en pantalla: ana.perez@dominio.com → an***@dominio.com. */
export function maskEmail(email) {
  const value = normalizeEmail(email);
  const at = value.indexOf('@');
  if (at < 1) return '***';
  return value.slice(0, Math.min(2, at)) + '***' + value.slice(at);
}
