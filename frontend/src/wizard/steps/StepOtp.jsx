import { useState } from 'react';
import { useCountdown } from '../../hooks/useCountdown.js';
import { validateOtp } from '../../validation/rules.js';
import { Field } from './Field.jsx';
import { OtpCountdown } from './OtpCountdown.jsx';

export function StepOtp({ flow, expired, pending, onVerify, onResend, onExpire, onCancel }) {
  const [code, setCode] = useState('');
  const [touched, setTouched] = useState(false);
  const resendIn = useCountdown(flow.resendAvailableAt);
  const error = touched ? validateOtp(code) : null;

  const submit = (event) => {
    event.preventDefault();
    setTouched(true);
    if (!validateOtp(code) && !pending && !expired) onVerify(code);
  };

  const resend = () => {
    setCode('');
    setTouched(false);
    onResend();
  };

  return (
    <form className="step" onSubmit={submit} noValidate>
      <h2>Valida tu identidad</h2>
      <p>{flow.message ?? 'Enviamos un código al celular registrado.'}</p>

      <OtpCountdown expiresAt={flow.otpExpiresAt} onExpire={onExpire} />

      <Field id="otp" label="Código de verificación" hint="6 dígitos" error={error}>
        {(props) => (
          <input {...props} className="otp-input" value={code} inputMode="numeric" autoComplete="one-time-code"
                 maxLength={6} pattern="\d{6}" disabled={expired}
                 onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))} />
        )}
      </Field>

      <div className="actions">
        <button type="submit" className="btn btn--primary" disabled={pending || expired}>
          {pending ? 'Validando…' : 'Validar código'}
        </button>
        <button type="button" className="btn" onClick={resend} disabled={pending || resendIn > 0}>
          {resendIn > 0 ? `Reenviar en ${resendIn} s` : 'Reenviar código'}
        </button>
        <button type="button" className="link" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}
