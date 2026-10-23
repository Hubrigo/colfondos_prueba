import { afterEach, describe, expect, it, vi } from 'vitest';
import { minimumDelay } from './timing.js';

// E6: regresión del defecto de Safari 17 (Promise.withResolvers solo existe desde Safari 17.4).
describe('minimumDelay', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.useRealTimers();
  });

  it('funciona sin Promise.withResolvers (Safari 17.0–17.3)', async () => {
    const original = Promise.withResolvers;
    delete Promise.withResolvers; // como en Safari 17.0
    try {
      vi.useFakeTimers();
      const done = minimumDelay(Date.now(), 600);
      vi.advanceTimersByTime(600);
      await expect(done).resolves.toBeUndefined();
    } finally {
      Promise.withResolvers = original;
    }
  });

  it('espera solo lo que falta para completar el mínimo', async () => {
    vi.useFakeTimers();
    const startedAt = Date.now() - 450;
    let resolved = false;
    minimumDelay(startedAt, 600).then(() => { resolved = true; });

    await vi.advanceTimersByTimeAsync(149);
    expect(resolved).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(resolved).toBe(true);
  });

  it('no espera si ya pasó el mínimo', async () => {
    vi.useFakeTimers();
    const done = minimumDelay(Date.now() - 5_000, 600);
    await vi.advanceTimersByTimeAsync(0);
    await expect(done).resolves.toBeUndefined();
  });
});
