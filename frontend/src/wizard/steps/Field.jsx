/** Campo con etiqueta, ayuda y error asociados por aria-describedby. */
export function Field({ id, label, hint, error, children }) {
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`field${error ? ' field--invalid' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && <small id={`${id}-hint`} className="field__hint">{hint}</small>}
      {error && <small id={`${id}-error`} className="field__error">{error}</small>}
    </div>
  );
}
