import { describe, expect, it } from 'vitest';
import { normalizeEmail, validateLogin, validateRegistration, type AuthFormData } from './authValidation';

const validForm = (): AuthFormData => ({
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: 'Ada@Example.com',
  phone: '+1 (555) 010-1234',
  password: 'password123',
  confirmPassword: 'password123',
  agreeTerms: true,
});

describe('auth form validation', () => {
  it('normalizes email without modifying passwords', () => {
    expect(normalizeEmail('  Ada@Example.COM ')).toBe('ada@example.com');
    const form = { ...validForm(), password: ' password123 ', confirmPassword: 'password123' };
    expect(validateRegistration(form).confirmPassword).toBe('Passwords do not match.');
  });

  it('accepts a valid registration and login', () => {
    expect(validateRegistration(validForm())).toEqual({});
    expect(validateLogin(validForm())).toEqual({});
  });

  it('returns practical field errors for invalid registration data', () => {
    const errors = validateRegistration({
      ...validForm(),
      firstName: ' ',
      email: 'invalid',
      phone: 'letters',
      password: 'short',
      confirmPassword: 'different',
      agreeTerms: false,
    });
    expect(errors).toMatchObject({
      firstName: expect.any(String),
      email: expect.any(String),
      phone: expect.any(String),
      password: expect.any(String),
      confirmPassword: expect.any(String),
      agreeTerms: expect.any(String),
    });
  });
});
