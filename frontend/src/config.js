// Configuración del frontend. Igual que en el backend, si falta una variable obligatoria
// la app no arranca a medias: muestra un error de configuración (lección de INC-004).
const DEV_DEFAULT_API = 'http://localhost:8080';

function resolveApiBaseUrl(env) {
  const configured = env.VITE_API_BASE_URL?.trim();
  if (configured) return configured.replace(/\/+$/, '');
  return env.DEV ? DEV_DEFAULT_API : null;
}

export const config = {
  apiBaseUrl: resolveApiBaseUrl(import.meta.env),
  requestTimeoutMs: 15000,
  otpWarningSeconds: 30,
};

export const missingConfig = config.apiBaseUrl ? [] : ['VITE_API_BASE_URL'];
