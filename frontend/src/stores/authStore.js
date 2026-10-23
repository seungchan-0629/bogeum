import { create } from 'zustand'

export const useAuthStore = create((set) => ({
  user: null, // { id, name, role }
  login: (user) => set({ user }),
  logout: () => set({ user: null }),
}))
