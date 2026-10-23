import { formatDateTime } from '../../utils/dates.js';

export function StepResult({ result, onFinish }) {
  const updatedAt = formatDateTime(result.updatedAt);
  return (
    <div className="step step--success" role="status">
      <h2>¡Listo! Actualizamos tu correo</h2>
      <dl className="summary">
        <dt>Nuevo correo</dt>
        <dd>{result.maskedEmail}</dd>
        <dt>Número de transacción</dt>
        <dd><code>{result.transactionId}</code></dd>
        {updatedAt && (<><dt>Fecha</dt><dd>{updatedAt}</dd></>)}
      </dl>
      {result.notificationStatus === 'SENT'
        ? <p>Te enviamos una confirmación al nuevo correo.</p>
        : <p className="banner banner--info">Tu correo quedó actualizado. La confirmación por correo puede tardar unos minutos.</p>}
      <div className="actions">
        <button type="button" className="btn btn--primary" onClick={onFinish}>Finalizar</button>
      </div>
    </div>
  );
}
