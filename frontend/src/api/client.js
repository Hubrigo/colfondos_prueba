import { config } from '../config.js';

/** Error de dominio que la UI sabe mostrar. `code` sigue el catálogo de E2 §5.2. */
export class ApiError extends Error {
  constructor({ status = 0, code, detail, correlationId, ...extra }) {
    super(detail || code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.correlationId = correlationId;
    this.extra = extra;
  }
}

/**
 * fetch con X-Correlation-Id, timeout (AbortController) y traducción de ProblemDetail.
 * Siempre resuelve con el JSON o rechaza con ApiError: el llamador nunca recibe
 * un error "crudo" del navegador (INC-001).
 */
export async function request(method, path, { body, correlationId, idempotencyKey, timeoutMs } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs ?? config.requestTimeoutMs);
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (correlationId) headers['X-Correlation-Id'] = correlationId;
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  let response;
  try {
    response = await fetch(config.apiBaseUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch (error) {
    const timedOut = error?.name === 'AbortError';
    throw new ApiError({ code: timedOut ? 'CLIENT_TIMEOUT' : 'NETWORK_ERROR', detail: error?.message, correlationId });
  } finally {
    clearTimeout(timer);
  }

  const responseCorrelationId = response.headers.get('X-Correlation-Id') ?? correlationId;
  const payload = await readJson(response);
  if (!response.ok) {
    throw new ApiError({
      code: 'UNEXPECTED_RESPONSE',
      ...payload,
      status: response.status,
      correlationId: payload?.correlationId ?? responseCorrelationId,
    });
  }
  return payload;
}

/** Lee el cuerpo como JSON sin lanzar si viene vacío o no es JSON (p. ej. un 502 del balanceador). */
async function readJson(response) {
  const text = await response.text().catch(() => '');
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
