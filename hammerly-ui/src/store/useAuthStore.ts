import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type User = {
  id?: string;
  firstName: string;
  lastName: string;
  email?: string;
  phone?: string;
  avatarImage?: string;
};

type AuthState = {
  isLoggedIn: boolean;
  user: User | null;
  token: string | null;
  watchedCount: number;

  loginSuccess: (payload: { user: User; token?: string }) => void;
  updateUser: (updates: Partial<User>) => void;
  logout: () => void;
  setWatchedCount: (count: number) => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      isLoggedIn: false,
      user: null,
      token: null,
      watchedCount: 0,

      loginSuccess: ({ user, token }) => {
        if (token) localStorage.setItem('token', token);
        else localStorage.removeItem('token');
        set({
          isLoggedIn: true,
          user,
          token: token || null,
        });
      },

      updateUser: (updates) =>
        set((state) => ({
          user: state.user ? { ...state.user, ...updates } : null,
        })),

      logout: () => {
        localStorage.removeItem('token');
        localStorage.removeItem('auth-store');
        set({
          isLoggedIn: false,
          user: null,
          token: null,
          watchedCount: 0,
        });
      },

      setWatchedCount: (count) => set({ watchedCount: count }),
    }),
    {
      name: 'auth-store',
    }
  )
);
