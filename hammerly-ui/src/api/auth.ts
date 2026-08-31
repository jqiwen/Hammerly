import { authenticatedFetch } from './authenticatedFetch';

export type LoginRequest = { email: string; password: string };
export type RegisterRequest = {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phone: string;
};

export type AuthResponse = {
  user: {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
    phone?: string;
    avatarImage?: string;
  };
  token: string;
};

type ErrorPayload = {
  message?: unknown;
  fields?: unknown;
};

export class AuthApiError extends Error {
  readonly fields: Record<string, string>;

  constructor(message: string, fields: Record<string, string> = {}) {
    super(message);
    this.name = 'AuthApiError';
    this.fields = fields;
  }
}

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const parseErrorFields = (value: unknown): Record<string, string> => {
  if (!value || typeof value !== 'object') return {};
  return Object.fromEntries(
    Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
  );
};

const postAuth = async <T>(path: string, payload: unknown, fallback: string): Promise<T> => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  const data = await response.json().catch(() => null) as ErrorPayload | null;
  if (!response.ok) {
    throw new AuthApiError(
      typeof data?.message === 'string' ? data.message : fallback,
      parseErrorFields(data?.fields),
    );
  }
  return data as T;
};

export const registerApi = (payload: RegisterRequest) =>
  postAuth<AuthResponse>('/auth/register', payload, 'Registration failed. Please try again.');

export const loginApi = (payload: LoginRequest) =>
  postAuth<AuthResponse>('/auth/login', payload, 'Sign in failed. Please try again.');

export const logoutApi = async (accessToken: string | null): Promise<{ success: boolean }> => {
  if (!accessToken) return { success: true };
  const response = await authenticatedFetch(
    `${API_BASE_URL}/auth/logout`,
    { method: 'POST' },
    { accessToken, clearSessionOnUnauthorized: false },
  );
  if (!response.ok) throw new Error('Logout acknowledgement failed');
  return response.json();
};

export const authApi = {
  register: (email: string, password: string, firstName: string, lastName: string, phone: string) =>
    registerApi({ email, password, firstName, lastName, phone }),
  login: loginApi,
  logout: logoutApi,
};
