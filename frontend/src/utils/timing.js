// Mantiene visible el indicador "Actualizando…" un tiempo mínimo para evitar parpadeo
// cuando el API responde muy rápido.
export function minimumDelay(startedAt, minMs = 600) {
  const { promise, resolve } = Promise.withResolvers();
  setTimeout(resolve, Math.max(0, minMs - (Date.now() - startedAt)));
  return promise;
}
