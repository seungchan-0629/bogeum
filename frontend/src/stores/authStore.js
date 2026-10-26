import { create } from 'zustand'

export const useAuthStore = create((set) => ({
  // { userId, name, role, approvalStatus? }
  // - role 값은 constants/auth.js의 ROLES
  // - approvalStatus는 중개사만 쓴다 (APPROVED: 승인 완료, PENDING: 승인 대기). 로그인 응답(BE-001)에 이 값이 있는지 백엔드와 협의 필요
  user: null,
  login: (user) => set({ user }),
  logout: () => set({ user: null }),
}))
