// [공통 컴포넌트] 페이지 번호 — 관리자 목록처럼 페이지 방식인 화면에서 쓴다 (AGENT_001, RISK_002)
// - page: 현재 페이지(1부터), totalPages: 전체 페이지 수, onChange: 페이지를 누르면 호출
// API의 page 파라미터가 0부터인지 1부터인지는 백엔드와 맞춘다
import clsx from 'clsx'

function Pagination({ page, totalPages, onChange }) {
  const pages = Array.from({ length: totalPages }, (_, index) => index + 1)

  return (
    <nav aria-label="페이지" className="flex justify-center gap-1.5">
      <button type="button" disabled={page === 1} onClick={() => onChange(page - 1)} aria-label="이전 페이지" className="size-9 rounded-lg border border-gray-200 bg-white disabled:opacity-40">
        ‹
      </button>
      {pages.map((number) => (
        <button
          key={number}
          type="button"
          onClick={() => onChange(number)}
          aria-current={number === page ? 'page' : undefined}
          className={clsx('size-9 rounded-lg border text-sm', number === page ? 'border-blue-600 bg-blue-600 font-bold text-white' : 'border-gray-200 bg-white hover:bg-gray-50')}
        >
          {number}
        </button>
      ))}
      <button type="button" disabled={page === totalPages} onClick={() => onChange(page + 1)} aria-label="다음 페이지" className="size-9 rounded-lg border border-gray-200 bg-white disabled:opacity-40">
        ›
      </button>
    </nav>
  )
}

export default Pagination
