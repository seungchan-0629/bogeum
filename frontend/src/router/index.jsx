// 전체 화면 주소(라우트) 목록
// 구조: 공개 화면 / 일반 화면(RequireRole → AppLayout: 헤더·푸터) / 관리자 화면(AdminLayout: 왼쪽 메뉴) / 그 외 404
// RequireRole로 감싼 묶음은 roles에 적힌 역할만 들어갈 수 있다 (로그인 안 하면 로그인 안내, 역할이 다르면 권한 없음 페이지)
// 통과하면 RequireRole이 화면을 AppLayout(헤더·푸터)에 넣어 보여준다
import { createBrowserRouter, Navigate } from 'react-router'
import { ROLES } from '../constants/auth.js'
import AdminLayout from '../components/common/AdminLayout.jsx'
import RequireRole from '../components/common/RequireRole.jsx'
// 공개 화면
import MainPage from '../pages/main/MainPage.jsx'
import LoginPage from '../pages/auth/LoginPage.jsx'
import SignupPage from '../pages/auth/SignupPage.jsx'
import AgentSignupPage from '../pages/auth/AgentSignupPage.jsx'
import AdminLoginPage from '../pages/auth/AdminLoginPage.jsx'
import NotFoundPage from '../pages/error/NotFoundPage.jsx'
import ServerErrorPage from '../pages/error/ServerErrorPage.jsx'
// 공통 (임차인·임대인·중개사)
import PropertyListPage from '../pages/property/PropertyListPage.jsx'
import PropertyDetailPage from '../pages/property/PropertyDetailPage.jsx'
import NotificationListPage from '../pages/notification/NotificationListPage.jsx'
import ChatPage from '../pages/chat/ChatPage.jsx'
import MyPage from '../pages/auth/MyPage.jsx'
// 임차인
import FavoriteListPage from '../pages/recommend-favorite/FavoriteListPage.jsx'
import MyHomeLayout from '../pages/residence/MyHomeLayout.jsx'
import RentPaymentPage from '../pages/rent/RentPaymentPage.jsx'
import TenantAsPage from '../pages/residence/TenantAsPage.jsx'
import MoveInUploadPage from '../pages/residence/MoveInUploadPage.jsx'
import ClaimPage from '../pages/residence/ClaimPage.jsx'
import PaymentMethodPage from '../pages/rent/PaymentMethodPage.jsx'
// 임대인
import MyPropertyListPage from '../pages/property/MyPropertyListPage.jsx'
import BrokerageRequestFormPage from '../pages/property/BrokerageRequestFormPage.jsx'
import RentCollectionPage from '../pages/rent/RentCollectionPage.jsx'
import LandlordCareLayout from '../pages/residence/LandlordCareLayout.jsx'
import LandlordAsPage from '../pages/residence/LandlordAsPage.jsx'
import DefectCheckPage from '../pages/residence/DefectCheckPage.jsx'
import SettlementAccountPage from '../pages/rent/SettlementAccountPage.jsx'
// 중개사
import AgentRequestListPage from '../pages/property/AgentRequestListPage.jsx'
import AgentContractListPage from '../pages/property/AgentContractListPage.jsx'
import PropertyDetailFormPage from '../pages/property/PropertyDetailFormPage.jsx'
import ContractUploadPage from '../pages/property/ContractUploadPage.jsx'
import BrokerageHistoryPage from '../pages/agent/BrokerageHistoryPage.jsx'
import AgentDocumentPage from '../pages/auth/AgentDocumentPage.jsx'
import AgentMyPage from '../pages/auth/AgentMyPage.jsx'
import AgentPendingPage from '../pages/auth/AgentPendingPage.jsx'
// 관리자
import AdminPropertyPage from '../pages/property/admin/AdminPropertyPage.jsx'
import AdminAgentPage from '../pages/agent/admin/AdminAgentPage.jsx'
import AdminRentPage from '../pages/rent/admin/AdminRentPage.jsx'
import AdminRiskUserPage from '../pages/risk/admin/AdminRiskUserPage.jsx'
import AdminRiskPropertyPage from '../pages/risk/admin/AdminRiskPropertyPage.jsx'
import AdminReportedChatPage from '../pages/chat/admin/AdminReportedChatPage.jsx'

const { TENANT, LANDLORD, AGENT } = ROLES

export const router = createBrowserRouter([
  {
    // 화면을 그리다 오류가 나면 500 안내 페이지
    errorElement: <ServerErrorPage />,
    children: [
      // ── 공개 화면 ──
      { path: '/', element: <MainPage /> },
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/signup/agent', element: <AgentSignupPage /> },
      { path: '/admin/login', element: <AdminLoginPage /> },

      // ── 일반 화면 (헤더 + 푸터). 로그인 안내·권한 없음은 헤더 없이 전체 화면 ──
      {
        children: [
          {
            // 임차인·임대인·중개사 공통. 승인 전 중개사는 승인 대기 페이지
            element: <RequireRole roles={[TENANT, LANDLORD, AGENT]} requireApproved />,
            children: [
              { path: '/properties', element: <PropertyListPage /> },
              { path: '/properties/:propertyId', element: <PropertyDetailPage /> },
              { path: '/notifications', element: <NotificationListPage /> },
              { path: '/chats', element: <ChatPage /> },
              { path: '/chats/:roomId', element: <ChatPage /> },
            ],
          },
          {
            element: <RequireRole roles={[TENANT, LANDLORD]} />,
            children: [{ path: '/mypage', element: <MyPage /> }],
          },
          {
            // 임차인 전용
            element: <RequireRole roles={[TENANT]} />,
            children: [
              { path: '/favorites', element: <FavoriteListPage /> },
              {
                path: '/my-home',
                element: <MyHomeLayout />,
                children: [
                  { index: true, element: <Navigate to="/my-home/rent" replace /> },
                  { path: 'rent', element: <RentPaymentPage /> },
                  { path: 'as', element: <TenantAsPage /> },
                  { path: 'move-in', element: <MoveInUploadPage /> },
                  { path: 'claim', element: <ClaimPage /> },
                ],
              },
              { path: '/mypage/payment-method', element: <PaymentMethodPage /> },
            ],
          },
          {
            // 임대인 전용
            element: <RequireRole roles={[LANDLORD]} />,
            children: [
              { path: '/my-properties', element: <MyPropertyListPage /> },
              { path: '/my-properties/new', element: <BrokerageRequestFormPage /> },
              { path: '/my-properties/:propertyId/edit', element: <BrokerageRequestFormPage /> },
              { path: '/rent/collections', element: <RentCollectionPage /> },
              {
                path: '/care',
                element: <LandlordCareLayout />,
                children: [
                  { index: true, element: <Navigate to="/care/as" replace /> },
                  { path: 'as', element: <LandlordAsPage /> },
                  { path: 'move-in', element: <DefectCheckPage /> },
                  { path: 'claim', element: <ClaimPage /> },
                ],
              },
              { path: '/mypage/settlement-account', element: <SettlementAccountPage /> },
            ],
          },
          {
            // 중개사 — 승인 전에도 쓸 수 있는 화면 (서류 제출, 마이페이지, 승인 대기)
            element: <RequireRole roles={[AGENT]} />,
            children: [
              { path: '/agent/documents', element: <AgentDocumentPage /> },
              { path: '/agent/mypage', element: <AgentMyPage /> },
              { path: '/agent/pending', element: <AgentPendingPage /> },
            ],
          },
          {
            // 중개사 — 관리자 승인 후에만 쓸 수 있는 화면 (USER_021)
            element: <RequireRole roles={[AGENT]} requireApproved />,
            children: [
              { path: '/agent/requests', element: <AgentRequestListPage /> },
              { path: '/agent/contracts', element: <AgentContractListPage /> },
              { path: '/agent/properties/:propertyId/edit', element: <PropertyDetailFormPage /> },
              { path: '/agent/properties/:propertyId/contract', element: <ContractUploadPage /> },
              { path: '/agent/history', element: <BrokerageHistoryPage /> },
            ],
          },
        ],
      },

      // ── 관리자 화면 (왼쪽 메뉴). 권한 검사는 AdminLayout 안에서 한다 ──
      {
        path: '/admin',
        element: <AdminLayout />,
        children: [
          { index: true, element: <Navigate to="/admin/properties" replace /> },
          { path: 'properties', element: <AdminPropertyPage /> },
          { path: 'agents', element: <AdminAgentPage /> },
          { path: 'rent', element: <AdminRentPage /> },
          { path: 'risk-users', element: <AdminRiskUserPage /> },
          { path: 'risk-properties', element: <AdminRiskPropertyPage /> },
          { path: 'chats', element: <AdminReportedChatPage /> },
          { path: 'notifications', element: <NotificationListPage /> },
        ],
      },

      // ── 등록되지 않은 주소 ──
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
