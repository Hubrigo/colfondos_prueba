import { useEffect, useState } from 'react';
import { secondsUntil } from '../utils/dates.js';

/**
 * Segundos que faltan para `isoTarget`, actualizados cada segundo. Se recalcula contra el
 * reloj en cada tick (no se resta 1), así una pestaña en segundo plano no se desfasa:
 * Safari y Chrome espacian los timers de pestañas inactivas.
 */
export function useCountdown(isoTarget) {
  const [remaining, setRemaining] = useState(() => secondsUntil(isoTarget));

  useEffect(() => {
    setRemaining(secondsUntil(isoTarget));
    const id = setInterval(() => setRemaining(secondsUntil(isoTarget)), 1000);
    return () => clearInterval(id);
  }, [isoTarget]);

  return remaining;
}
