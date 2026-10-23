// Mantiene visible el indicador "Actualizando…" un tiempo mínimo para evitar parpadeo
// cuando el API responde muy rápido.
//
// E6: se usa `new Promise` y no `Promise.withResolvers`, que solo existe desde Safari 17.4
// (Safari 17.0–17.3 lanzaba TypeError y la confirmación quedaba trabada).
export function minimumDelay(startedAt, minMs = 600) {
  return new Promise((resolve) => {
    setTimeout(resolve, Math.max(0, minMs - (Date.now() - startedAt)));
  });
}
