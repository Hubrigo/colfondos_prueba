import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client.js';
import { ContactUpdateWizard } from './ContactUpdateWizard.jsx';

const inMinutes = (m) => new Date(Date.now() + m * 60_000).toISOString();
const flow = (overrides = {}) => ({
  flowId: 'f-1',
  otpChannel: 'SMS',
  otpExpiresAt: inMinutes(5),
  resendAvailableAt: inMinutes(0.5),
  message: 'Si los datos corresponden a un afiliado, enviamos un código al celular registrado.',
  ...overrides,
});
const result = {
  transactionId: 'TX-20261022-ABC123',
  status: 'UPDATED',
  maskedEmail: 'nu***@dominio.com',
  updatedAt: '2026-10-22T21:04:12-05:00',
  notificationStatus: 'SENT',
};

function fakeApi(overrides = {}) {
  return {
    startFlow: vi.fn().mockResolvedValue(flow()),
    resendOtp: vi.fn().mockResolvedValue(flow({ otpExpiresAt: inMinutes(5) })),
    verifyOtp: vi.fn().mockResolvedValue({ verified: true }),
    updateEmail: vi.fn().mockResolvedValue(result),
    ...overrides,
  };
}

let user;
beforeEach(() => {
  user = userEvent.setup();
});

async function identify() {
  await user.type(screen.getByLabelText('Número de documento'), '1012345678');
  await user.click(screen.getByRole('button', { name: 'Enviar código' }));
  await screen.findByRole('heading', { name: 'Valida tu identidad' });
}

async function validateOtp(code = '123456') {
  await user.type(screen.getByLabelText('Código de verificación'), code);
  await user.click(screen.getByRole('button', { name: 'Validar código' }));
}

async function enterEmail(email = 'nuevo.correo@dominio.com') {
  await screen.findByRole('heading', { name: 'Ingresa tu nuevo correo' });
  await user.type(screen.getByLabelText('Nuevo correo electrónico'), email);
  await user.type(screen.getByLabelText('Confirma el nuevo correo'), email);
  await user.click(screen.getByRole('button', { name: 'Continuar' }));
}

describe('ContactUpdateWizard', () => {
  it('CA-01: completa el flujo y usa el mismo correlationId en todas las llamadas', async () => {
    const api = fakeApi();
    render(<ContactUpdateWizard api={api} />);

    await identify();
    expect(screen.getByRole('timer')).toHaveTextContent('El código vence en');
    await validateOtp();
    await enterEmail();
    expect(screen.getByText('nu***@dominio.com')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByRole('heading', { name: '¡Listo! Actualizamos tu correo' })).toBeInTheDocument();
    expect(screen.getByText('TX-20261022-ABC123')).toBeInTheDocument();

    const correlationId = api.startFlow.mock.calls[0][1];
    expect(api.verifyOtp).toHaveBeenCalledWith('f-1', '123456', correlationId);
    expect(api.updateEmail).toHaveBeenCalledWith('f-1',
      { newEmail: 'nuevo.correo@dominio.com', confirmEmail: 'nuevo.correo@dominio.com' },
      expect.objectContaining({ correlationId, idempotencyKey: expect.any(String) }));
  });

  it('CA-02: no llama al backend si el documento es inválido', async () => {
    const api = fakeApi();
    render(<ContactUpdateWizard api={api} />);

    await user.type(screen.getByLabelText('Número de documento'), '12AB');
    await user.click(screen.getByRole('button', { name: 'Enviar código' }));

    expect(screen.getByText('Ingresa solo números, entre 5 y 15 dígitos')).toBeInTheDocument();
    expect(screen.getByLabelText('Número de documento')).toHaveAttribute('aria-invalid', 'true');
    expect(api.startFlow).not.toHaveBeenCalled();
  });

  it('CA-04: muestra el mensaje del backend con los intentos restantes', async () => {
    const api = fakeApi({
      verifyOtp: vi.fn().mockRejectedValue(new ApiError({ status: 422, code: 'OTP_INVALID', detail: 'Código incorrecto. Te quedan 2 intentos.' })),
    });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp('000000');

    expect(await screen.findByRole('alert')).toHaveTextContent('Código incorrecto. Te quedan 2 intentos.');
  });

  it('CA-05: con OTP_BLOCKED muestra la pantalla de bloqueo', async () => {
    const api = fakeApi({ verifyOtp: vi.fn().mockRejectedValue(new ApiError({ status: 423, code: 'OTP_BLOCKED' })) });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp('000000');

    expect(await screen.findByRole('heading', { name: 'Bloqueamos el proceso por seguridad' })).toBeInTheDocument();
  });

  it('CA-06: un OTP vencido deshabilita la validación y permite reenviar sin perder el paso', async () => {
    const api = fakeApi({
      startFlow: vi.fn().mockResolvedValue(flow({ otpExpiresAt: inMinutes(-1), resendAvailableAt: inMinutes(-1) })),
    });
    render(<ContactUpdateWizard api={api} />);
    await identify();

    expect(screen.getByRole('status')).toHaveTextContent('Tu código expiró. Solicita uno nuevo.');
    expect(screen.getByLabelText('Código de verificación')).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Reenviar código' }));

    expect(api.resendOtp).toHaveBeenCalledWith('f-1', api.startFlow.mock.calls[0][1]);
    await waitFor(() => expect(screen.getByLabelText('Código de verificación')).toBeEnabled());
    expect(screen.getByRole('heading', { name: 'Valida tu identidad' })).toBeInTheDocument();
  });

  it('CA-07: avisa cuando quedan 30 segundos o menos', async () => {
    const api = fakeApi({ startFlow: vi.fn().mockResolvedValue(flow({ otpExpiresAt: inMinutes(0.4) })) });
    render(<ContactUpdateWizard api={api} />);
    await identify();

    expect(screen.getByRole('timer')).toHaveTextContent(/Tu código vence en 0:2\d\. Si no te alcanza, solicita uno nuevo\./);
  });

  it('CA-12: tras un timeout del cliente el reintento usa la misma Idempotency-Key', async () => {
    const updateEmail = vi.fn()
      .mockRejectedValueOnce(new ApiError({ code: 'CLIENT_TIMEOUT' }))
      .mockResolvedValueOnce(result);
    const api = fakeApi({ updateEmail });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp();
    await enterEmail();

    await user.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('La solicitud tardó demasiado');
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    await screen.findByRole('heading', { name: '¡Listo! Actualizamos tu correo' });
    const [first, second] = updateEmail.mock.calls.map((call) => call[2].idempotencyKey);
    expect(second).toBe(first);
  });

  it('CA-13: proveedor caído muestra un mensaje claro y conserva el paso', async () => {
    const api = fakeApi({ startFlow: vi.fn().mockRejectedValue(new ApiError({ status: 503, code: 'PROVIDER_UNAVAILABLE', correlationId: 'c-503' })) });
    render(<ContactUpdateWizard api={api} />);
    await user.type(screen.getByLabelText('Número de documento'), '1000000002');
    await user.click(screen.getByRole('button', { name: 'Enviar código' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('No pudimos validar tu identidad en este momento');
    expect(alert).toHaveTextContent('Seguimiento: c-503');
    expect(screen.getByRole('heading', { name: 'Identifícate' })).toBeInTheDocument();
  });

  it('CA-14: notificación PENDING informa que la confirmación puede tardar', async () => {
    const api = fakeApi({ updateEmail: vi.fn().mockResolvedValue({ ...result, notificationStatus: 'PENDING' }) });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp();
    await enterEmail();
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText(/La confirmación por correo puede tardar unos minutos/)).toBeInTheDocument();
  });

  it('INC-001: un rechazo inesperado (no ApiError) se captura, se registra y no queda sin manejo', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    const api = fakeApi({ updateEmail: vi.fn().mockRejectedValue(new TypeError('undefined is not a function')) });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp();
    await enterEmail();
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ocurrió un error inesperado. Intenta de nuevo.');
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled();
    expect(consoleError).toHaveBeenCalledWith('[contact-update] error inesperado', expect.objectContaining({
      error: expect.any(TypeError),
    }));
  });

  it('E6: en Safari 17.0 (sin Promise.withResolvers) confirmar llega al resultado y no deja rechazos sin manejo', async () => {
    const original = Promise.withResolvers;
    delete Promise.withResolvers;
    const unhandled = [];
    const onUnhandled = (reason) => unhandled.push(reason);
    process.on('unhandledRejection', onUnhandled);
    try {
      const api = fakeApi();
      render(<ContactUpdateWizard api={api} />);
      await identify();
      await validateOtp();
      await enterEmail();
      await user.click(screen.getByRole('button', { name: 'Confirmar' }));

      expect(await screen.findByRole('heading', { name: '¡Listo! Actualizamos tu correo' }, { timeout: 2_000 }))
        .toBeInTheDocument();
      await new Promise((resolve) => setTimeout(resolve, 0));
      expect(unhandled).toEqual([]);
    } finally {
      process.off('unhandledRejection', onUnhandled);
      Promise.withResolvers = original;
    }
  });

  it('E6: si la actualización falla, el error se muestra y Confirmar vuelve a habilitarse', async () => {
    const api = fakeApi({ updateEmail: vi.fn().mockRejectedValue(new ApiError({ status: 504, code: 'PROVIDER_TIMEOUT' })) });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp();
    await enterEmail();
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El servicio está tardando más de lo normal');
    expect(screen.getByRole('button', { name: 'Confirmar' })).toBeEnabled();
  });

  it('el botón Confirmar se deshabilita mientras la solicitud está en curso (sin doble envío)', async () => {
    let release;
    const api = fakeApi({ updateEmail: vi.fn(() => new Promise((resolve) => { release = () => resolve(result); })) });
    render(<ContactUpdateWizard api={api} />);
    await identify();
    await validateOtp();
    await enterEmail();
    await user.click(screen.getByRole('button', { name: 'Confirmar' }));

    expect(screen.getByRole('button', { name: 'Actualizando…' })).toBeDisabled();
    release();
    await screen.findByRole('heading', { name: '¡Listo! Actualizamos tu correo' });
    expect(api.updateEmail).toHaveBeenCalledTimes(1);
  });
});
