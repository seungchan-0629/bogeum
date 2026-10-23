import { createBrowserRouter } from 'react-router'
import MainPage from '../pages/main/MainPage.jsx'

export const router = createBrowserRouter([
  { path: '/', element: <MainPage /> },
])
