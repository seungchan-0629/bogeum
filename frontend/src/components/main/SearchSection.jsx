import { useState } from 'react'
import { useNavigate } from 'react-router'
import clsx from 'clsx'

const TRADE_TYPES = ['전세', '월세', '매매']

function SearchSection() {
  const [tradeType, setTradeType] = useState(TRADE_TYPES[0])
  const navigate = useNavigate()

  // 매물 찾기 화면으로 이동한다. 로그인하지 않았으면 그 화면에서 로그인 안내가 뜬다
  // TODO: 고른 거래 유형과 검색어를 주소 쿼리로 넘겨 매물 찾기 화면의 검색 조건(BE-018)에 채운다
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/properties')
  }

  return (
    <section className="bg-[#f3f7fd]">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-6 px-6 py-14">
        <div className="flex flex-col gap-2">
          <h2 className="text-3xl font-extrabold tracking-tight sm:text-[34px]">어떤 집을 찾고 계세요?</h2>
          <p className="text-[17px] text-gray-600">
            모든 매물의 등기부등본을 확인하고, 위험 신호를 쉬운 말로 알려드려요.
          </p>
        </div>
        <form
          onSubmit={handleSubmit}
          className="flex flex-col gap-4 rounded-2xl border border-[#dce3ee] bg-white p-5 shadow-[0_8px_24px_rgba(17,24,39,0.06)]"
        >
          <div role="group" aria-label="거래 유형" className="flex gap-2">
            {TRADE_TYPES.map((type) => (
              <button
                key={type}
                type="button"
                aria-pressed={tradeType === type}
                onClick={() => setTradeType(type)}
                className={clsx(
                  'rounded-full px-5 py-2.5 text-[15px]',
                  tradeType === type
                    ? 'bg-blue-600 font-bold text-white'
                    : 'border border-gray-300 bg-white font-medium text-gray-700 hover:bg-gray-50',
                )}
              >
                {type}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap gap-3">
            <label htmlFor="main-search" className="sr-only">
              매물 검색
            </label>
            <input
              id="main-search"
              type="search"
              placeholder="지역, 지하철역, 학교 이름으로 찾아보세요"
              className="h-14 min-w-0 flex-[1_1_320px] rounded-xl border border-gray-300 px-5 text-base"
            />
            <button type="submit" className="h-14 rounded-xl bg-blue-600 px-8 text-base font-bold text-white hover:bg-blue-700">
              검색
            </button>
          </div>
        </form>
      </div>
    </section>
  )
}

export default SearchSection
