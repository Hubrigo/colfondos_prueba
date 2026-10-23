import { useState } from 'react';
import { validateEmail, validateEmailConfirmation } from '../../validation/rules.js';
import { Field } from './Field.jsx';

export function StepEmail({ initial, onSubmit, onCancel }) {
  const [newEmail, setNewEmail] = useState(initial.newEmail);
  const [confirmEmail, setConfirmEmail] = useState(initial.confirmEmail);
  const [touched, setTouched] = useState({ newEmail: false, confirmEmail: false });

  const errors = {
    newEmail: validateEmail(newEmail),
    confirmEmail: validateEmailConfirmation(newEmail, confirmEmail),
  };

  const submit = (event) => {
    event.preventDefault();
    setTouched({ newEmail: true, confirmEmail: true });
    if (!errors.newEmail && !errors.confirmEmail) onSubmit({ newEmail, confirmEmail });
  };

  const blur = (field) => () => setTouched((t) => ({ ...t, [field]: true }));

  return (
    <form className="step" onSubmit={submit} noValidate>
      <h2>Ingresa tu nuevo correo</h2>

      <Field id="newEmail" label="Nuevo correo electrónico" error={touched.newEmail && errors.newEmail}>
        {(props) => (
          <input {...props} type="email" value={newEmail} autoComplete="email" inputMode="email" maxLength={254}
                 onChange={(e) => setNewEmail(e.target.value)} onBlur={blur('newEmail')} />
        )}
      </Field>

      <Field id="confirmEmail" label="Confirma el nuevo correo" hint="Escríbelo de nuevo para evitar errores"
             error={touched.confirmEmail && errors.confirmEmail}>
        {(props) => (
          <input {...props} type="email" value={confirmEmail} autoComplete="off" inputMode="email" maxLength={254}
                 onChange={(e) => setConfirmEmail(e.target.value)} onBlur={blur('confirmEmail')} />
        )}
      </Field>

      <div className="actions">
        <button type="submit" className="btn btn--primary">Continuar</button>
        <button type="button" className="link" onClick={onCancel}>Cancelar</button>
      </div>
    </form>
  );
}
