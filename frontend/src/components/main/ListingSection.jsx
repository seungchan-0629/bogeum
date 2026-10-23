// RECOMMEND_001 (조회 지역 기반 인기 매물 추천), FAVORITE_001, FAVORITE_002 (관심 매물 등록·해제)
import { useState } from 'react'
import clsx from 'clsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'
import { formatLeasePrice, getLeaseTypeLabel } from '../../utils/format.js'

// TODO: GET /api/v1/recommendations/popular?size=3 (BE-029) 응답의 properties로 교체
// - 응답 필드: propertyId, thumbnailUrl, propertyType, leaseType, deposit, monthlyRent, area, address
// - 관심 매물 여부(isFavorite)와 등기 확인 여부 필드가 응답에 없어 백엔드와 협의 필요
const SAMPLE_PROPERTIES = [
  { propertyId: 1, thumbnailUrl: null, propertyType: '원룸', leaseType: 'JEONSE', deposit: 21000, monthlyRent: 0, area: 7, address: '서울 마포구' },
  { propertyId: 2, thumbnailUrl: null, propertyType: '투룸', leaseType: 'MONTHLY', deposit: 1000, monthlyRent: 65, area: 11, address: '서울 관악구' },
  { propertyId: 3, thumbnailUrl: null, propertyType: '아파트', leaseType: 'JEONSE', deposit: 32000, monthlyRent: 0, area: 18, address: '경기 수원시' },
]

function HeartIcon({ isFilled }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill={isFilled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z" />
    </svg>
  )
}

function ListingSection() {
  const user = useAuthStore((state) => state.user)
  // 관심 매물은 임차인만 등록할 수 있다 (FAVORITE_001)
  const canFavorite = user?.role === ROLES.TENANT
  const [favoriteIds, setFavoriteIds] = useState(() => new Set())

  // TODO: 등록은 POST /api/v1/favorites (BE-031, 요청: propertyId),
  //       해제는 DELETE /api/v1/favorites/{propertyId} (BE-032) 호출 후 성공하면 상태 반영
  const toggleFavorite = (propertyId) => {
    setFavoriteIds((prev) => {
      const next = new Set(prev)
      if (next.has(propertyId)) next.delete(propertyId)
      else next.add(propertyId)
      return next
    })
  }

  return (
    <section className="mx-auto flex max-w-[1200px] flex-col gap-6 px-6 py-16">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-[26px] font-extrabold">추천 매물</h2>
        <a href="#" className="text-[15px] font-medium text-blue-700 hover:text-blue-900">
          전체 보기
        </a>
      </div>
      <div className="flex flex-wrap gap-5">
        {SAMPLE_PROPERTIES.map((property) => {
          const isFavorite = favoriteIds.has(property.propertyId)
          return (
            <article key={property.propertyId} className="min-w-0 flex-[1_1_280px] overflow-hidden rounded-2xl border border-gray-200 bg-white">
              <div className="relative flex h-45 items-center justify-center bg-[#e8edf5] text-gray-500">
                {property.thumbnailUrl ? (
                  <img src={property.thumbnailUrl} alt="" className="size-full object-cover" />
                ) : (
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                    <path d="M3 10.5 12 3l9 7.5" />
                    <path d="M5 9.5V21h14V9.5" />
                  </svg>
                )}
                {canFavorite && (
                  <button
                    type="button"
                    onClick={() => toggleFavorite(property.propertyId)}
                    aria-pressed={isFavorite}
                    aria-label={isFavorite ? '관심 매물 해제' : '관심 매물로 저장'}
                    className={clsx(
                      'absolute top-3 right-3 flex size-11 items-center justify-center rounded-full bg-white shadow-sm',
                      isFavorite ? 'text-red-500' : 'text-gray-700 hover:text-red-500',
                    )}
                  >
                    <HeartIcon isFilled={isFavorite} />
                  </button>
                )}
              </div>
              <div className="flex flex-col gap-2 p-[18px]">
                <div className="flex flex-wrap gap-1.5">
                  {/* TODO: 등기 확인 여부 필드가 생기면 그 값으로 표시 */}
                  <span className="rounded-md bg-emerald-50 px-2.5 py-1 text-[13px] font-bold text-emerald-700">등기 확인 완료</span>
                  <span className="rounded-md bg-gray-100 px-2.5 py-1 text-[13px] font-medium text-gray-700">{getLeaseTypeLabel(property.leaseType)}</span>
                </div>
                <p className="text-xl font-extrabold">{formatLeasePrice(property)}</p>
                {/* 면적은 평 기준 (BE-018 비고) */}
                <p className="text-sm text-gray-600">
                  {property.address} · {property.propertyType} · {property.area}평
                </p>
              </div>
            </article>
          )
        })}
      </div>
    </section>
  )
}

export default ListingSection
