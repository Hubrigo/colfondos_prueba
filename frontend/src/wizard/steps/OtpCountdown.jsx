import { useEffect } from 'react';
import { config } from '../../config.js';
import { useCountdown } from '../../hooks/useCountdown.js';
import { formatSeconds } from '../../utils/dates.js';

/** Vigencia del OTP con aviso preventivo (CA-07) y notificación al expirar (CA-06, INC-003). */
export function OtpCountdown({ expiresAt, onExpire }) {
  const remaining = useCountdown(expiresAt);
  const warning = remaining > 0 && remaining <= config.otpWarningSeconds;

  useEffect(() => {
    if (remaining === 0) onExpire();
  }, [remaining, onExpire]);

  if (remaining === 0) {
    return <p className="countdown countdown--expired" role="status">Tu código expiró. Solicita uno nuevo.</p>;
  }
  return (
    <p className={`countdown${warning ? ' countdown--warning' : ''}`} role="timer" aria-live={warning ? 'assertive' : 'off'}>
      {warning ? `Tu código vence en ${formatSeconds(remaining)}. Si no te alcanza, solicita uno nuevo.`
               : `El código vence en ${formatSeconds(remaining)}`}
    </p>
  );
}
