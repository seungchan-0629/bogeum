import { create } from 'zustand'

export const useAuthStore = create((set) => ({
  // { userId, name, role, contractRoles?, approvalStatus? }
  // - role 값은 constants/auth.js의 ROLES
  // - contractRoles는 사용자(USER)만 쓴다. 계약상 역할 목록 (CONTRACT_ROLES, 예: ['TENANT']). 계약이 없으면 빈 배열
  //   로그인 응답(BE-001)이나 내 정보(BE-013)에 이 값을 넣을지 백엔드와 협의 필요
  // - agentId와 approvalStatus는 중개사 로그인·갱신·내 정보 응답에 포함된다 (PENDING / APPROVED / REJECTED).
  user: null, // { userId, name, role, lastLoginProvider } - 로그인 응답과 같은 구조
  login: (user) => set({ user }),
  logout: () => set({ user: null }),
}))
