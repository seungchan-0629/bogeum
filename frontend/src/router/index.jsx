import { createBrowserRouter } from 'react-router'
import MainPage from '../pages/main/MainPage.jsx'
import LoginPage from '../pages/auth/LoginPage.jsx'
import SignupPage from '../pages/auth/SignupPage.jsx'
import AgentSignupPage from '../pages/auth/AgentSignupPage.jsx'

export const router = createBrowserRouter([
  { path: '/', element: <MainPage /> },
  { path: '/login', element: <LoginPage /> },
  { path: '/signup', element: <SignupPage /> },
  { path: '/signup/agent', element: <AgentSignupPage /> },
])
