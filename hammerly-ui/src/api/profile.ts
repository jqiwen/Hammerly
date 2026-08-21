const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

const getAuthHeaders = () => {
  const token = localStorage.getItem('token');
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
};

// ─── Profile Info ─────────────────────────────────────────────

export const getProfile = async () => {
  const res = await fetch(`${API_BASE_URL}/users/profile`, {
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to get profile');
  return data;
};

export const updateProfile = async (payload: {
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
}) => {
  const res = await fetch(`${API_BASE_URL}/users/profile`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to update profile');
  return data;
};

// ─── Password ─────────────────────────────────────────────────

export const changePassword = async (payload: {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}) => {
  const res = await fetch(`${API_BASE_URL}/users/profile/password`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to change password');
  return data;
};

// ─── Avatar ───────────────────────────────────────────────────

export const updateAvatar = async (avatarImage: string) => {
  const res = await fetch(`${API_BASE_URL}/users/profile/avatar`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify({ avatarImage }),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to update avatar');
  return data;
};

export const removeAvatar = async () => {
  const res = await fetch(`${API_BASE_URL}/users/profile/avatar`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to remove avatar');
  return data;
};

// ─── Payment Methods ──────────────────────────────────────────

export type PaymentMethod = {
  id: number;
  cardType: string;
  cardNumber: string;
  expiryMonth: number;
  expiryYear: number;
  cardholderName: string;
  isDefault: number;
  billingAddress: string;
  billingCity: string;
  billingProvince: string;
  billingPostalCode: string;
  billingCountry: string;
};

export const getPaymentMethods = async (): Promise<{ success: boolean; paymentMethods: PaymentMethod[] }> => {
  const res = await fetch(`${API_BASE_URL}/users/profile/payment-methods`, {
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to get payment methods');
  return data;
};

export const addPaymentMethod = async (payload: {
  cardType: string;
  cardNumber: string;
  expiryMonth: number;
  expiryYear: number;
  cardholderName: string;
  isDefault?: boolean;
  billingAddress?: string;
  billingCity?: string;
  billingProvince?: string;
  billingPostalCode?: string;
  billingCountry?: string;
}) => {
  const res = await fetch(`${API_BASE_URL}/users/profile/payment-methods`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to add payment method');
  return data;
};

export const deletePaymentMethod = async (id: number) => {
  const res = await fetch(`${API_BASE_URL}/users/profile/payment-methods/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to delete payment method');
  return data;
};

export const setDefaultPaymentMethod = async (id: number) => {
  const res = await fetch(`${API_BASE_URL}/users/profile/payment-methods/${id}/default`, {
    method: 'PUT',
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to set default payment method');
  return data;
};

// ─── Bidding List ─────────────────────────────────────────────

export const getBiddingList = async () => {
  const res = await fetch(`${API_BASE_URL}/users/my-bids`, {
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to get bidding list');
  return data;
};

// ─── Selling List ─────────────────────────────────────────────

export const getSellingList = async () => {
  const res = await fetch(`${API_BASE_URL}/users/my-auctions`, {
    headers: getAuthHeaders(),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(data.message || 'Failed to get selling list');
  return data;
};
