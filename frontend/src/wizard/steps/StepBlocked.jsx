export function StepBlocked({ onRestart }) {
  return (
    <div className="step step--blocked">
      <h2>Bloqueamos el proceso por seguridad</h2>
      <p>Superaste el número de intentos permitidos para validar tu identidad.</p>
      <p>Comunícate con la línea de servicio al afiliado o acércate a una oficina para actualizar tus datos.</p>
      <div className="actions">
        <button type="button" className="btn" onClick={onRestart}>Volver al inicio</button>
      </div>
    </div>
  );
}
