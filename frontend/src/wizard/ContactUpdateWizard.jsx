import { useCallback, useReducer } from 'react';
import { contactUpdateApi } from '../api/contactUpdateApi.js';
import { normalizeEmail } from '../validation/rules.js';
import { initialState, reducer, STEPS } from './reducer.js';
import { Stepper } from './steps/Stepper.jsx';
import { ErrorBanner } from './steps/ErrorBanner.jsx';
import { StepIdentify } from './steps/StepIdentify.jsx';
import { StepOtp } from './steps/StepOtp.jsx';
import { StepEmail } from './steps/StepEmail.jsx';
import { StepConfirm } from './steps/StepConfirm.jsx';
import { StepResult } from './steps/StepResult.jsx';
import { StepBlocked } from './steps/StepBlocked.jsx';

/**
 * Asistente de actualización de correo (HU-CONTACT-001).
 * Regla de INC-001: toda llamada asíncrona pasa por `run`, que siempre captura el rechazo.
 * Ninguna promesa queda sin manejo, aunque falle el render posterior o una utilidad.
 */
export function ContactUpdateWizard({ api = contactUpdateApi }) {
  const [state, dispatch] = useReducer(reducer, undefined, initialState);
  const { correlationId } = state;

  const run = useCallback(async (operation, onSuccess) => {
    dispatch({ type: 'REQUEST' });
    try {
      const data = await operation();
      onSuccess(data);
    } catch (error) {
      if (error?.name !== 'ApiError') {
        // Error de programación o del navegador: se registra con el correlationId para soporte.
        console.error('[contact-update] error inesperado', { correlationId, error });
      }
      dispatch({ type: 'FAILURE', error: error?.name === 'ApiError' ? error : { code: 'UNEXPECTED' } });
    }
  }, [correlationId]);

  const startFlow = (document) =>
    run(() => api.startFlow(document, correlationId), (flow) => dispatch({ type: 'FLOW_STARTED', document, flow }));

  const resendOtp = () =>
    run(() => api.resendOtp(state.flow.flowId, correlationId), (flow) => dispatch({ type: 'OTP_RESENT', flow }));

  const verifyOtp = (code) =>
    run(() => api.verifyOtp(state.flow.flowId, code, correlationId), () => dispatch({ type: 'OTP_VERIFIED' }));

  const captureEmail = (email) =>
    dispatch({
      type: 'EMAIL_CAPTURED',
      email: { newEmail: normalizeEmail(email.newEmail), confirmEmail: normalizeEmail(email.confirmEmail) },
    });

  const confirmUpdate = () =>
    run(
      () => api.updateEmail(state.flow.flowId, state.email, { correlationId, idempotencyKey: state.idempotencyKey }),
      (result) => dispatch({ type: 'UPDATED', result }),
    );

  const restart = () => dispatch({ type: 'RESTART' });
  const markOtpExpired = useCallback(() => dispatch({ type: 'OTP_EXPIRED_LOCALLY' }), []);

  return (
    <section className="wizard" aria-labelledby="wizard-title">
      <h1 id="wizard-title">Actualiza tu correo electrónico</h1>
      <Stepper current={state.step} />
      <ErrorBanner error={state.error} onDismiss={() => dispatch({ type: 'DISMISS_ERROR' })} />

      {state.step === STEPS.IDENTIFY && (
        <StepIdentify initial={state.document} pending={state.pending} onSubmit={startFlow} />
      )}
      {state.step === STEPS.OTP && (
        <StepOtp
          flow={state.flow}
          expired={state.otpExpired}
          pending={state.pending}
          onVerify={verifyOtp}
          onResend={resendOtp}
          onExpire={markOtpExpired}
          onCancel={restart}
        />
      )}
      {state.step === STEPS.EMAIL && <StepEmail initial={state.email} onSubmit={captureEmail} onCancel={restart} />}
      {state.step === STEPS.CONFIRM && (
        <StepConfirm
          email={state.email.newEmail}
          pending={state.pending}
          onConfirm={confirmUpdate}
          onBack={() => dispatch({ type: 'BACK_TO_EMAIL' })}
        />
      )}
      {state.step === STEPS.RESULT && <StepResult result={state.result} onFinish={restart} />}
      {state.step === STEPS.BLOCKED && <StepBlocked onRestart={restart} />}

      <p className="trace">Código de seguimiento: <code>{correlationId}</code></p>
    </section>
  );
}
