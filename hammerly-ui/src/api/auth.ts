type LoginReq = { email: string; password: string };
type RegisterReq = { firstName: string; lastName: string; email: string; password: string; phone: string };

export type AuthResponse = {
  user: {
    id: string;
    firstName: string;
    lastName: string;
    email: string;
  };
  token?: string;
};

const mockUser = {
  id: 'mock-user-001',
  firstName: 'User',
  lastName: '001',
  email: 'user001@hammerly.com',
  phone: '+1 1234567890',
  avatarImage: '/images/user.jpg',
  password: '123456789'
};

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api';

// ========== MOCK LOGIN - FOR TESTING ==========
const USE_MOCK_LOGIN = false; // Set to false to use real backend

const fakeDelay = (ms = 500) => new Promise(resolve => setTimeout(resolve, ms));

/**
 * Mock register - returns success with sample user
 */
const mockRegisterApi = async (payload: RegisterReq): Promise<AuthResponse> => {
  await fakeDelay();
  return {
    user: {
      id: 'mock-user-' + Date.now(),
      firstName: payload.firstName,
      lastName: payload.lastName,
      email: payload.email,
    },
    token: 'mock-token-' + Date.now(),
  };
};

/**
 * Mock login - returns success with sample user
 */
const mockLoginApi = async (payload: LoginReq): Promise<AuthResponse> => {
  await fakeDelay();
  return {
    user: {
      ...mockUser,
      email: payload.email,
    },
    token: 'mock-token-' + Date.now(),
  };
};

/**
 * Register a new user
 * Maps to: POST /api/auth/register
 */
export const registerApi = async (payload: RegisterReq): Promise<AuthResponse> => {
  // Use mock if enabled
  if (USE_MOCK_LOGIN) {
    console.log('🎭 Using MOCK register');
    return mockRegisterApi(payload);
  }

  try {
    console.log('📡 Calling API:', `${API_BASE_URL}/auth/register`);
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    
    let data;
    try {
      data = await response.json();
    } catch (parseError) {
      console.error('❌ Failed to parse response:', parseError);
      throw new Error(`Server error: ${response.status} ${response.statusText}`);
    }
    
    if (!response.ok) {
      throw new Error(data.message || `HTTP ${response.status}: Registration failed`);
    }
    
    // Save token to localStorage
    if (data.token) {
      localStorage.setItem('token', data.token);
    }
    return data;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Registration failed - network error';
    console.error('❌ Register error:', message);
    throw new Error(message);
  }
};

/**
 * Login user
 * Maps to: POST /api/auth/login
 */
export const loginApi = async (payload: LoginReq): Promise<AuthResponse> => {
  // Use mock if enabled
  if (USE_MOCK_LOGIN) {
    console.log('🎭 Using MOCK login');
    return mockLoginApi(payload);
  }

  try {
    console.log('📡 Calling API:', `${API_BASE_URL}/auth/login`);
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    
    let data;
    try {
      data = await response.json();
    } catch (parseError) {
      console.error('❌ Failed to parse response:', parseError);
      throw new Error(`Server error: ${response.status} ${response.statusText}`);
    }
    
    if (!response.ok) {
      throw new Error(data.message || `HTTP ${response.status}: Login failed`);
    }
    
    // Save token to localStorage
    if (data.token) {
      localStorage.setItem('token', data.token);
    }
    return data;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Login failed - network error';
    console.error('❌ Login error:', message);
    throw new Error(message);
  }
};

/**
 * Logout user
 * Maps to: POST /api/auth/logout
 */
export const logoutApi = async (): Promise<{ success: boolean }> => {
  try {
    const response = await fetch(`${API_BASE_URL}/auth/logout`, {
      method: 'POST'
    });
    if (!response.ok) throw new Error('Logout failed');
    return await response.json();
  } catch (error) {
    console.error('Error logging out:', error);
    throw error;
  }
};

export const authApi = {
  register: async (email: string, password: string, firstName: string, lastName: string, phone: string) => {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/register`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, password, firstName, lastName, phone }),
      });

      if (!response.ok) {
        throw new Error('Failed to register');
      }

      return await response.json();
    } catch (error) {
      console.error('Error during registration:', error);
      throw error;
    }
  },

  login: loginApi,
  logout: logoutApi
};