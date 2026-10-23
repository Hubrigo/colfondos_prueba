import { maskEmail } from '../../validation/rules.js';

export function StepConfirm({ email, pending, onConfirm, onBack }) {
  return (
    <div className="step">
      <h2>Confirma la actualización</h2>
      <dl className="summary">
        <dt>Nuevo correo</dt>
        <dd>{maskEmail(email)}</dd>
      </dl>
      <p>Enviaremos una confirmación a tu nuevo correo y un aviso de seguridad al correo anterior.</p>
      <div className="actions">
        {/* Deshabilitado mientras hay una solicitud en curso: evita doble envío. */}
        <button type="button" className="btn btn--primary" onClick={onConfirm} disabled={pending}>
          {pending ? 'Actualizando…' : 'Confirmar'}
        </button>
        <button type="button" className="btn" onClick={onBack} disabled={pending}>Volver</button>
      </div>
    </div>
  );
}
