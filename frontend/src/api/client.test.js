import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, request } from './client.js';

function respond(status, body, headers = {}) {
  const text = body === undefined ? '' : typeof body === 'string' ? body : JSON.stringify(body);
  return new Response(text, { status, headers });
}

afterEach(() => vi.unstubAllGlobals());

describe('request', () => {
  it('envía correlationId, idempotencia y JSON, y devuelve el cuerpo', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(200, { ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    const data = await request('PUT', '/x', { body: { a: 1 }, correlationId: 'corr-1', idempotencyKey: 'key-1' });

    expect(data).toEqual({ ok: true });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/x');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe('{"a":1}');
    expect(init.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-Correlation-Id': 'corr-1',
      'Idempotency-Key': 'key-1',
    });
  });

  it('convierte un ProblemDetail en ApiError con su code y datos extra', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(422, {
      code: 'OTP_INVALID', detail: 'Código incorrecto. Te quedan 2 intentos.', remainingAttempts: 2, correlationId: 'c-9',
    })));

    const error = await request('POST', '/x').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 422, code: 'OTP_INVALID', correlationId: 'c-9' });
    expect(error.message).toBe('Código incorrecto. Te quedan 2 intentos.');
    expect(error.extra.remainingAttempts).toBe(2);
  });

  it('una respuesta de error que no es JSON (502 del balanceador) no rompe el cliente', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(502, '<html>Bad gateway</html>')));

    await expect(request('GET', '/x')).rejects.toMatchObject({ name: 'ApiError', status: 502, code: 'UNEXPECTED_RESPONSE' });
  });

  it('un fallo de red se convierte en NETWORK_ERROR', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(request('GET', '/x', { correlationId: 'c-1' }))
      .rejects.toMatchObject({ code: 'NETWORK_ERROR', correlationId: 'c-1' });
  });

  it('aborta al vencer el timeout y lo informa como CLIENT_TIMEOUT', async () => {
    vi.stubGlobal('fetch', vi.fn((url, { signal }) => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    })));

    await expect(request('GET', '/x', { timeoutMs: 20 })).rejects.toMatchObject({ code: 'CLIENT_TIMEOUT' });
  });

  it('acepta respuestas sin cuerpo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(202)));
    await expect(request('POST', '/x')).resolves.toBeNull();
  });
});
