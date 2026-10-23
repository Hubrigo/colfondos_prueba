import { useState } from 'react';
import { DOCUMENT_TYPES, validateDocumentNumber, validateDocumentType } from '../../validation/rules.js';
import { Field } from './Field.jsx';

export function StepIdentify({ initial, pending, onSubmit }) {
  const [documentType, setDocumentType] = useState(initial.documentType);
  const [documentNumber, setDocumentNumber] = useState(initial.documentNumber);
  const [touched, setTouched] = useState(false);

  const errors = {
    documentType: validateDocumentType(documentType),
    documentNumber: validateDocumentNumber(documentType, documentNumber),
  };
  const valid = !errors.documentType && !errors.documentNumber;

  const submit = (event) => {
    event.preventDefault();
    setTouched(true);
    if (valid && !pending) onSubmit({ documentType, documentNumber: documentNumber.trim() });
  };

  return (
    <form className="step" onSubmit={submit} noValidate>
      <h2>Identifícate</h2>
      <p>Te enviaremos un código de verificación al <strong>celular registrado</strong>.</p>

      <Field id="documentType" label="Tipo de documento" error={touched && errors.documentType}>
        {(props) => (
          <select {...props} value={documentType} onChange={(e) => setDocumentType(e.target.value)}>
            {DOCUMENT_TYPES.map((t) => <option key={t.value} value={t.value}>{t.label}</option>)}
          </select>
        )}
      </Field>

      <Field id="documentNumber" label="Número de documento" error={touched && errors.documentNumber}>
        {(props) => (
          <input {...props} value={documentNumber} autoComplete="off"
                 inputMode={documentType === 'PA' ? 'text' : 'numeric'} maxLength={15}
                 onChange={(e) => setDocumentNumber(e.target.value)} onBlur={() => setTouched(true)} />
        )}
      </Field>

      <div className="actions">
        <button type="submit" className="btn btn--primary" disabled={pending}>
          {pending ? 'Enviando…' : 'Enviar código'}
        </button>
      </div>
    </form>
  );
}
