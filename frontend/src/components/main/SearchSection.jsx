import { useState } from 'react'
import { useNavigate } from 'react-router'
import clsx from 'clsx'

// 매매는 서비스에서 다루지 않아 뺐다. 처음엔 거래 유형 조건 없이(전체) 찾는다 (PROPERTY_005)
const TRADE_TYPES = [
  { value: '', label: '전체' },
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
]

function SearchSection() {
  const [tradeType, setTradeType] = useState('')
  const [region, setRegion] = useState('')
  const [subway, setSubway] = useState('')
  const navigate = useNavigate()

  // 고른 거래 유형·지역·지하철역을 주소 쿼리로 넘긴다. 매물 찾기 화면이 이 값을 왼쪽 같은 칸에 채운다
  // 지역과 지하철역을 칸으로 나눠 받아서, 입력한 글자가 무엇인지 짐작하지 않아도 된다. 둘 다 비우면 전체 목록이다
  // 로그인하지 않았으면 그 화면에서 로그인 안내가 뜬다
  const handleSubmit = (event) => {
    event.preventDefault()
    const params = new URLSearchParams()
    if (tradeType) params.set('leaseTypes', tradeType)
    if (region.trim()) params.set('region', region.trim())
    if (subway.trim()) params.set('subway', subway.trim())
    const query = params.toString()
    navigate(query ? `/properties?${query}` : '/properties')
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
                key={type.value}
                type="button"
                aria-pressed={tradeType === type.value}
                onClick={() => setTradeType(type.value)}
                className={clsx(
                  'rounded-full px-5 py-2.5 text-[15px]',
                  tradeType === type.value
                    ? 'bg-blue-600 font-bold text-white'
                    : 'border border-gray-300 bg-white font-medium text-gray-700 hover:bg-gray-50',
                )}
              >
                {type.label}
              </button>
            ))}
          </div>
          {/* 넓은 화면에서는 한 줄, 좁은 화면에서는 칸이 아래로 쌓인다 */}
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex min-w-0 flex-[1_1_240px] flex-col gap-1.5">
              <label htmlFor="main-search-region" className="text-sm font-medium text-gray-700">지역</label>
              <input
                id="main-search-region"
                type="search"
                value={region}
                maxLength={50}
                onChange={(event) => setRegion(event.target.value)}
                placeholder="ex. 서울 강남구"
                className="h-14 w-full rounded-xl border border-gray-300 px-5 text-base"
              />
            </div>
            <div className="flex min-w-0 flex-[1_1_240px] flex-col gap-1.5">
              <label htmlFor="main-search-subway" className="text-sm font-medium text-gray-700">지하철역</label>
              <input
                id="main-search-subway"
                type="search"
                value={subway}
                maxLength={50}
                onChange={(event) => setSubway(event.target.value)}
                placeholder="ex. 고속터미널"
                className="h-14 w-full rounded-xl border border-gray-300 px-5 text-base"
              />
            </div>
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
