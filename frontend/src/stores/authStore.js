import { create } from 'zustand'

export const useAuthStore = create((set) => ({
  user: null, // { userId, name, role } - role 값은 constants/auth.js의 ROLES
  login: (user) => set({ user }),
  logout: () => set({ user: null }),
}))
