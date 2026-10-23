import { describe, expect, it } from 'vitest';
import {
  maskEmail,
  normalizeEmail,
  validateDocumentNumber,
  validateDocumentType,
  validateEmail,
  validateEmailConfirmation,
  validateOtp,
} from './rules.js';

describe('validateDocumentNumber (CA-02)', () => {
  it.each([
    ['CC', '1012345678', null],
    ['CC', ' 1012345678 ', null],
    ['CC', '12AB', 'Ingresa solo números, entre 5 y 15 dígitos'],
    ['CC', '1234', 'Ingresa solo números, entre 5 y 15 dígitos'],
    ['CC', '1234567890123456', 'Ingresa solo números, entre 5 y 15 dígitos'],
    ['CC', '', 'Ingresa el número de documento'],
    ['PA', 'AB12345', null],
    ['PA', 'AB-123', 'Ingresa entre 5 y 15 letras o números'],
  ])('%s "%s" → %s', (type, number, expected) => {
    expect(validateDocumentNumber(type, number)).toBe(expected);
  });

  it('solo acepta tipos del catálogo', () => {
    expect(validateDocumentType('CC')).toBeNull();
    expect(validateDocumentType('XX')).toBe('Selecciona el tipo de documento');
  });
});

describe('validateOtp', () => {
  it.each(['123456'])('acepta %s', (code) => expect(validateOtp(code)).toBeNull());
  it.each(['12345', '1234567', '12a456', '', undefined])('rechaza %s', (code) =>
    expect(validateOtp(code)).toBe('El código tiene 6 dígitos'));
});

describe('validateEmail (CA-08), igual que el backend', () => {
  it.each(['nuevo.correo@dominio.com', 'a+b@sub.dominio.co', '  ana@dominio.com  '])('acepta %s', (email) =>
    expect(validateEmail(email)).toBeNull());

  it.each(['sin-arroba.com', 'a@b', 'a@dominio', 'a@.com', 'a b@dominio.com', '@dominio.com'])('rechaza %s', (email) =>
    expect(validateEmail(email)).toBe('Ingresa un correo válido'));

  it('rechaza más de 254 caracteres y el vacío', () => {
    expect(validateEmail(`${'a'.repeat(250)}@d.co`)).toBe('El correo no puede superar 254 caracteres');
    expect(validateEmail('')).toBe('Ingresa el nuevo correo');
  });
});

describe('validateEmailConfirmation (CA-09)', () => {
  it('compara sin importar espacios ni mayúsculas', () => {
    expect(validateEmailConfirmation('Ana@Dominio.com', ' ana@dominio.COM ')).toBeNull();
  });
  it('detecta correos distintos', () => {
    expect(validateEmailConfirmation('a@dominio.com', 'b@dominio.com')).toBe('Los correos no coinciden');
  });
  it('exige la confirmación', () => {
    expect(validateEmailConfirmation('a@dominio.com', '')).toBe('Confirma el nuevo correo');
  });
});

describe('normalizeEmail y maskEmail', () => {
  it('normaliza el dominio y conserva la parte local', () => {
    expect(normalizeEmail('  Nuevo.Correo@Dominio.COM ')).toBe('Nuevo.Correo@dominio.com');
  });
  it('enmascara igual que el backend', () => {
    expect(maskEmail('nuevo.correo@dominio.com')).toBe('nu***@dominio.com');
    expect(maskEmail('a@dominio.com')).toBe('a***@dominio.com');
    expect(maskEmail('invalido')).toBe('***');
  });
});
