import { request } from './client.js';

// Contrato del orquestador (E2 §3).
const FLOWS = '/api/v1/contact-update/flows';

export const contactUpdateApi = {
  startFlow: (document, correlationId) =>
    request('POST', FLOWS, { body: document, correlationId }),

  resendOtp: (flowId, correlationId) =>
    request('POST', `${FLOWS}/${encodeURIComponent(flowId)}/otp/resend`, { correlationId }),

  verifyOtp: (flowId, code, correlationId) =>
    request('POST', `${FLOWS}/${encodeURIComponent(flowId)}/otp/verify`, { body: { code }, correlationId }),

  updateEmail: (flowId, email, { correlationId, idempotencyKey }) =>
    request('PUT', `${FLOWS}/${encodeURIComponent(flowId)}/email`, {
      body: { newEmail: email.newEmail, confirmEmail: email.confirmEmail },
      correlationId,
      idempotencyKey,
    }),
};
