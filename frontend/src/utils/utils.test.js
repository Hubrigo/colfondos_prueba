import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatDateTime, formatSeconds, secondsUntil, toEpochMs } from './dates.js';
import { uuid } from './id.js';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('fechas del contrato (ISO-8601 con offset)', () => {
  it('formatea la fecha del resultado en hora de Bogotá', () => {
    const text = formatDateTime('2026-10-22T21:04:12-05:00');
    expect(text).toContain('2026');
    expect(text).toMatch(/9:04/);
  });

  it.each([undefined, null, '', 'no-es-fecha', 42])('una fecha inválida (%s) no lanza: devuelve ""', (value) => {
    expect(() => formatDateTime(value)).not.toThrow();
    expect(formatDateTime(value)).toBe('');
  });

  it('calcula los segundos restantes sin valores negativos', () => {
    const now = toEpochMs('2026-10-22T21:00:00-05:00');
    expect(secondsUntil('2026-10-22T21:05:00-05:00', now)).toBe(300);
    expect(secondsUntil('2026-10-22T20:59:00-05:00', now)).toBe(0);
    expect(secondsUntil('basura', now)).toBe(0);
  });

  it('muestra m:ss', () => {
    expect(formatSeconds(300)).toBe('5:00');
    expect(formatSeconds(65)).toBe('1:05');
    expect(formatSeconds(9)).toBe('0:09');
  });
});

describe('uuid', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('usa crypto.randomUUID cuando existe', () => {
    expect(uuid()).toMatch(UUID_V4);
  });

  it('funciona sin crypto.randomUUID (contexto HTTP no seguro, p. ej. QA)', () => {
    const { getRandomValues } = globalThis.crypto;
    vi.stubGlobal('crypto', { getRandomValues: getRandomValues.bind(globalThis.crypto) });
    const ids = new Set(Array.from({ length: 50 }, () => uuid()));
    expect(ids.size).toBe(50);
    ids.forEach((id) => expect(id).toMatch(UUID_V4));
  });
});
