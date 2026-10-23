import { Link } from 'react-router'
import Skyline from '../common/Skyline.jsx'

// 로그인·회원가입 화면 공통 레이아웃 (왼쪽: 밤 스카이라인, 오른쪽: 폼)
function AuthLayout({ title, children }) {
  return (
    <div className="flex min-h-svh">
      <aside className="sticky top-0 hidden h-svh flex-1 flex-col overflow-hidden bg-night pt-10 text-white md:flex">
        <Link to="/" className="px-10 text-[22px] font-extrabold text-blue-400">
          보금
        </Link>
        <div className="m-auto flex flex-col gap-3 px-10 py-8">
          <p className="text-3xl leading-snug font-extrabold tracking-tight">
            당신의 보금자리와
            <br />
            <span className="text-blue-400">보증금</span>을 지켜드립니다.
          </p>
          <p className="text-slate-300">오늘 밤도 안심하고 불 켤 수 있도록.</p>
        </div>
        <Skyline animate={false} />
        <div aria-hidden="true" className="h-5 bg-night-ground" />
      </aside>

      <main className="flex flex-1 justify-center px-6 py-12">
        <div className="my-auto flex w-full max-w-sm flex-col gap-8">
          <div className="flex flex-col gap-2">
            <Link to="/" className="text-2xl font-extrabold text-blue-600 md:hidden">
              보금
            </Link>
            <h1 className="text-[28px] font-extrabold tracking-tight">{title}</h1>
          </div>
          {children}
        </div>
      </main>
    </div>
  )
}

export default AuthLayout
