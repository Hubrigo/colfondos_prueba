// Emulación de la línea base de JavaScript de Safari 17.0 sobre el WebKit de Playwright.
//
// Por qué: Playwright trae WebKit 27, mucho más nuevo que Safari 17, y soporta APIs que
// Safari 17.0 no tenía. Sin esta emulación, un defecto como el de INC-001 NO se reproduce.
// Se eliminan, antes de cargar la página, las APIs que llegaron después de Safari 17.0.
// Fuente: MDN / @mdn/browser-compat-data (versión de Safari en que se agregó cada API).
//
// Limitación: emula la ausencia de APIs, no las diferencias de motor (parser de fechas,
// CSS, layout). La certificación sigue siendo en Safari 17 real (E5, caso C-06).
export const SAFARI_17_MISSING_APIS = [
  ['Promise.withResolvers', '17.4'],
  ['Object.groupBy', '17.4'],
  ['Map.groupBy', '17.4'],
  ['URL.parse', '18.0'],
  ['Promise.try', '18.2'],
  ['RegExp.escape', '18.2'],
  ['Iterator.prototype.map', '18.4'],
  ['Iterator.prototype.filter', '18.4'],
  ['Iterator.prototype.toArray', '18.4'],
  ['Temporal', 'no disponible'],
];

export const SAFARI_17_USER_AGENT =
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15';

/** Script que corre en la página antes que el código de la app. */
export function safari17InitScript(apis) {
  for (const [path] of apis) {
    const parts = path.split('.');
    const prop = parts.pop();
    const owner = parts.reduce((obj, key) => (obj == null ? obj : obj[key]), globalThis);
    if (owner && prop in owner) {
      try {
        delete owner[prop];
      } catch {
        Object.defineProperty(owner, prop, { value: undefined, configurable: true });
      }
    }
  }
}
