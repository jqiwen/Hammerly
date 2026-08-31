// @vitest-environment jsdom

import '@testing-library/jest-dom/vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Auth from './page';

const authMocks = vi.hoisted(() => ({
  loginApi: vi.fn(),
  registerApi: vi.fn(),
}));

vi.mock('@/api/auth', () => ({
  AuthApiError: class AuthApiError extends Error {
    fields = {};
  },
  loginApi: authMocks.loginApi,
  registerApi: authMocks.registerApi,
}));

vi.mock('../../components/feature/Header', () => ({ default: () => null }));

describe('Auth page submission state', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('disables sign in while the request is pending and prevents duplicate submission', () => {
    authMocks.loginApi.mockImplementation(() => new Promise(() => {}));
    render(<BrowserRouter><Auth /></BrowserRouter>);

    fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
    fireEvent.change(screen.getByLabelText('Email Address'), { target: { value: 'ada@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'password123' } });
    const submit = screen.getAllByRole('button', { name: 'Sign In' }).at(-1)!;
    fireEvent.click(submit);
    fireEvent.click(submit);

    expect(authMocks.loginApi).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: /Signing in/ })).toBeDisabled();
  });
});
