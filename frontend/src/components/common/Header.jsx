import { Link } from 'react-router'

// 각 메뉴의 페이지가 아직 없어서 링크는 비워 둔다
const NAV_ITEMS = ['매물 찾기', '관심 매물', '내 계약 관리', '채팅']

function Header({ id }) {
  return (
    <header id={id} className="border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-[1200px] flex-wrap items-center gap-6 px-6 py-4">
        <Link to="/" className="text-2xl font-extrabold tracking-tight text-blue-600">
          보금
        </Link>
        <nav aria-label="주요 메뉴" className="flex grow flex-wrap gap-6 text-[15px] font-medium">
          {NAV_ITEMS.map((label) => (
            <a key={label} href="#" className="text-gray-900 hover:text-blue-700">
              {label}
            </a>
          ))}
        </nav>
        <div className="flex items-center gap-3">
          <button
            type="button"
            aria-label="알림"
            className="flex size-11 items-center justify-center rounded-lg text-gray-700 hover:bg-gray-100"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
              <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
            </svg>
          </button>
          <a href="#" className="rounded-lg border border-gray-300 px-[18px] py-2.5 text-sm font-medium text-gray-900 hover:bg-gray-50">
            로그인
          </a>
        </div>
      </div>
    </header>
  )
}

export default Header
