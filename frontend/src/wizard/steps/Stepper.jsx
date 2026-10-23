import { STEP_ORDER, STEPS } from '../reducer.js';

const LABELS = {
  [STEPS.IDENTIFY]: 'Identificación',
  [STEPS.OTP]: 'Validación',
  [STEPS.EMAIL]: 'Nuevo correo',
  [STEPS.CONFIRM]: 'Confirmación',
  [STEPS.RESULT]: 'Resultado',
};

export function Stepper({ current }) {
  const currentIndex = STEP_ORDER.indexOf(current);
  return (
    <ol className="stepper" aria-label="Progreso">
      {STEP_ORDER.map((step, index) => {
        const status = index < currentIndex ? 'done' : index === currentIndex ? 'current' : 'todo';
        return (
          <li key={step} className={`stepper__item stepper__item--${status}`}
              aria-current={status === 'current' ? 'step' : undefined}>
            <span className="stepper__dot" aria-hidden="true">{index + 1}</span>
            <span className="stepper__label">{LABELS[step]}</span>
          </li>
        );
      })}
    </ol>
  );
}
