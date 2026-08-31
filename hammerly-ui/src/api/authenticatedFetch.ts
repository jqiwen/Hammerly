import { useAuthStore } from '@/store/useAuthStore';

type AuthenticatedFetchOptions = {
  accessToken?: string | null;
  clearSessionOnUnauthorized?: boolean;
};

export const authenticatedFetch = async (
  input: RequestInfo | URL,
  init: RequestInit = {},
  options: AuthenticatedFetchOptions = {},
) => {
  const token = options.accessToken === undefined
    ? localStorage.getItem('token')
    : options.accessToken;
  const headers = new Headers(init.headers);

  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(input, { ...init, headers });
  if (response.status === 401 && token && options.clearSessionOnUnauthorized !== false &&
      localStorage.getItem('token') === token) {
    useAuthStore.getState().logout();
  }
  return response;
};
