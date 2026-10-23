import { messageFor } from '../../errors/messages.js';

/** Mensaje de error anunciado a lectores de pantalla. Incluye el código de seguimiento. */
export function ErrorBanner({ error, onDismiss }) {
  if (!error) return null;
  return (
    <div className="banner banner--error" role="alert">
      <p>{messageFor(error)}</p>
      {error.correlationId && <p className="banner__trace">Seguimiento: {error.correlationId}</p>}
      <button type="button" className="link" onClick={onDismiss}>Cerrar</button>
    </div>
  );
}
