import { useEffect } from 'react';
import { getProfile } from '@/api/profile';
import { useAuthStore } from '@/store/useAuthStore';

export default function AuthBootstrap() {
  useEffect(() => {
    const syncSession = async () => {
      const storedToken = localStorage.getItem('token');

      if (!storedToken) {
        useAuthStore.getState().logout();
        return;
      }

      try {
        const data = await getProfile();
        useAuthStore.getState().loginSuccess({
          user: data.user,
          token: storedToken,
        });
      } catch {
        useAuthStore.getState().logout();
      }
    };

    void syncSession();
  }, []);

  return null;
}
