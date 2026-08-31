// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/store/useAuthStore';
import { logoutApi } from './auth';
import { authenticatedFetch } from './authenticatedFetch';

describe('authenticated requests and local session ownership', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.getState().logout();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('stores the access token once when login succeeds', () => {
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
    useAuthStore.getState().loginSuccess({
      user: { firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' },
      token: 'signed-token',
    });

    expect(storageSpy.mock.calls.filter(([key]) => key === 'token')).toEqual([['token', 'signed-token']]);
    expect(localStorage.getItem('token')).toBe('signed-token');
  });

  it('adds bearer auth and clears local auth on a protected 401 without retrying', async () => {
    useAuthStore.getState().loginSuccess({
      user: { firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' },
      token: 'expired-token',
    });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 401 }));

    await authenticatedFetch('/api/users/profile');

    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const requestInit = fetchSpy.mock.calls[0][1];
    expect(new Headers(requestInit?.headers).get('Authorization')).toBe('Bearer expired-token');
    expect(useAuthStore.getState().isLoggedIn).toBe(false);
    expect(localStorage.getItem('token')).toBeNull();
  });

  it('does not let a stale 401 clear a newer login session', async () => {
    useAuthStore.getState().loginSuccess({
      user: { firstName: 'Old', lastName: 'Session' },
      token: 'old-token',
    });
    let resolveResponse!: (response: Response) => void;
    vi.spyOn(globalThis, 'fetch').mockReturnValue(new Promise((resolve) => {
      resolveResponse = resolve;
    }));

    const pending = authenticatedFetch('/api/users/profile');
    useAuthStore.getState().loginSuccess({
      user: { firstName: 'New', lastName: 'Session' },
      token: 'new-token',
    });
    resolveResponse(new Response('{}', { status: 401 }));
    await pending;

    expect(useAuthStore.getState().isLoggedIn).toBe(true);
    expect(localStorage.getItem('token')).toBe('new-token');
  });

  it('clears immediately while best-effort logout still sends the captured bearer token', async () => {
    useAuthStore.getState().loginSuccess({
      user: { firstName: 'Ada', lastName: 'Lovelace' },
      token: 'captured-token',
    });
    const token = useAuthStore.getState().token;
    useAuthStore.getState().logout();
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{"success":true}', {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }));

    await logoutApi(token);

    expect(useAuthStore.getState().isLoggedIn).toBe(false);
    expect(new Headers(fetchSpy.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer captured-token');
  });
});
