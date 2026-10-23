// Fechas del contrato: ISO-8601 con offset (E2 §3), p. ej. 2026-10-22T21:04:12-05:00.
const DISPLAY = new Intl.DateTimeFormat('es-CO', {
  dateStyle: 'long',
  timeStyle: 'short',
  timeZone: 'America/Bogota',
});

/** Convierte una fecha ISO del API en milisegundos; devuelve NaN si no es válida. */
export function toEpochMs(iso) {
  return typeof iso === 'string' ? Date.parse(iso) : Number.NaN;
}

/** Texto para mostrar al afiliado. Nunca lanza excepción: ante una fecha inválida devuelve ''. */
export function formatDateTime(iso) {
  const ms = toEpochMs(iso);
  return Number.isNaN(ms) ? '' : DISPLAY.format(ms);
}

/** Segundos restantes hasta `iso`, nunca negativos. */
export function secondsUntil(iso, nowMs = Date.now()) {
  const ms = toEpochMs(iso);
  return Number.isNaN(ms) ? 0 : Math.max(0, Math.ceil((ms - nowMs) / 1000));
}

/** 125 → "2:05". */
export function formatSeconds(total) {
  const minutes = Math.floor(total / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}
