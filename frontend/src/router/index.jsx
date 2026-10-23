import { createBrowserRouter } from 'react-router'
import MainPage from '../pages/main/MainPage.jsx'
import LoginPage from '../pages/auth/LoginPage.jsx'

export const router = createBrowserRouter([
  { path: '/', element: <MainPage /> },
  { path: '/login', element: <LoginPage /> },
])
