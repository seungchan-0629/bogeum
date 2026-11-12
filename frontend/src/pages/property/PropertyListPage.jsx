// [페이지] 매물 찾기 (목록 + 검색·필터) — /properties
// 누가: 임차인·임대인·중개사 (로그인 안 하면 로그인 안내 페이지)
// 요구사항: PROPERTY_004 (목록, 스크롤 방식), PROPERTY_005 (검색·필터, 결과 없음 안내), PROPERTY_009 (중개사 열람), FAVORITE_001·002
// 화면 구성: 왼쪽 검색 조건 / 오른쪽 결과 카드 (모바일에서는 위아래로 쌓인다)
import { useEffect, useRef, useState } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { Field, TextInput, SelectInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { ROLES } from '../../constants/auth.js'
import { getProperties } from '../../api/property/index.js'
import { getPropertyTypeLabel } from '../../utils/format.js'

// 목록 API 응답을 카드 모양으로 바꾼다. 금액은 원 → 만원, 면적은 평
const toCardProperty = (property) => ({
  propertyId: property.propertyId,
  thumbnailUrl: property.thumbnailUrl,
  propertyType: getPropertyTypeLabel(property.propertyType),
  leaseType: property.leaseType,
  deposit: property.deposit / 10000,
  monthlyRent: property.monthlyRent / 10000,
  area: property.areaPyeong,
  address: property.roadAddress,
  nearbySubway: property.nearbySubwayStation,
})

const LEASE_TYPE_OPTIONS = [
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
  { value: 'SALE', label: '매매' },
]
const PROPERTY_TYPE_OPTIONS = ['아파트', '오피스텔', '빌라', '원룸', '투룸']
const ROOM_COUNT_OPTIONS = [
  { value: '', label: '전체' },
  { value: '1', label: '1개 이상' },
  { value: '2', label: '2개 이상' },
  { value: '3', label: '3개 이상' },
]

function ToggleChip({ isSelected, onClick, children }) {
  return (
    <button
      type="button"
      aria-pressed={isSelected}
      onClick={onClick}
      className={clsx(
        'rounded-full px-4 py-2 text-sm',
        isSelected ? 'bg-blue-600 font-bold text-white' : 'border border-gray-300 bg-white text-gray-800 hover:bg-gray-50',
      )}
    >
      {children}
    </button>
  )
}

function toggleInList(list, value) {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

function PropertyListPage() {
  const user = useAuthStore((state) => state.user)
  const { favoriteIds, toggleFavorite } = useFavoriteToggle([2])
  const [leaseTypes, setLeaseTypes] = useState([])
  const [propertyTypes, setPropertyTypes] = useState([])

  // PROPERTY_004: 공개 매물을 12개씩 불러오고, 스크롤이 바닥에 닿으면 응답의 nextCursor로 다음 목록을 이어 불러온다
  // TODO: PROPERTY_005에서 왼쪽 검색 조건을 GET /property의 쿼리로 보낸다. 그 전까지 조건은 고를 수만 있고 목록을 거르지 않는다
  //       (불러온 목록만 화면에서 거르면 아직 안 불러온 매물이 빠져 결과가 틀린다)
  const list = useInfiniteQuery({
    queryKey: ['properties'],
    queryFn: ({ pageParam, signal }) => getProperties(pageParam, signal),
    initialPageParam: null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
  })
  const properties = list.data?.pages.flatMap((page) => page.properties).map(toCardProperty) ?? []

  // 목록 맨 아래 표시 칸이 화면에 들어오면 다음 목록을 불러온다. 불러오다 실패하면 자동으로 다시 부르지 않고 [다시 시도]를 보여 준다
  const sentinelRef = useRef(null)
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = list
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage || isFetchingNextPage || isFetchNextPageError) return
    // 바닥에 닿기 조금 전(200px)부터 불러와 스크롤이 끊기지 않게 한다
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) fetchNextPage()
    }, { rootMargin: '200px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage])

  const handleReset = () => {
    setLeaseTypes([])
    setPropertyTypes([])
  }

  return (
    <div className="page-container">
      <PageHeader title="매물 찾기" description="조건을 하나 이상 고르면 모두 맞는 매물만 보여드려요." />
      <div className="grid items-start gap-6 lg:grid-cols-[280px_1fr]">
        <aside aria-label="검색 조건" className="card flex flex-col gap-5">
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-bold">거래 유형</legend>
            <div className="flex flex-wrap gap-2">
              {LEASE_TYPE_OPTIONS.map((option) => (
                <ToggleChip key={option.value} isSelected={leaseTypes.includes(option.value)} onClick={() => setLeaseTypes((prev) => toggleInList(prev, option.value))}>
                  {option.label}
                </ToggleChip>
              ))}
            </div>
          </fieldset>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-sm font-bold">매물 종류</legend>
            <div className="flex flex-wrap gap-2">
              {PROPERTY_TYPE_OPTIONS.map((type) => (
                <ToggleChip key={type} isSelected={propertyTypes.includes(type)} onClick={() => setPropertyTypes((prev) => toggleInList(prev, type))}>
                  {type}
                </ToggleChip>
              ))}
            </div>
          </fieldset>
          <Field label="지역" htmlFor="filter-address">
            <TextInput id="filter-address" placeholder="예: 서울 강남구" />
          </Field>
          <Field label="주변 지하철역" htmlFor="filter-subway">
            <TextInput id="filter-subway" placeholder="예: 역삼역" />
          </Field>
          <div className="grid grid-cols-2 gap-2">
            <Field label="보증금 최대 (만원)" htmlFor="filter-deposit">
              <TextInput id="filter-deposit" inputMode="numeric" />
            </Field>
            <Field label="월세 최대 (만원)" htmlFor="filter-rent">
              <TextInput id="filter-rent" inputMode="numeric" />
            </Field>
            <Field label="최소 면적 (평)" htmlFor="filter-area">
              <TextInput id="filter-area" inputMode="numeric" />
            </Field>
            <Field label="방 개수" htmlFor="filter-rooms">
              <SelectInput id="filter-rooms" options={ROOM_COUNT_OPTIONS} />
            </Field>
            <Field label="최소 층수" htmlFor="filter-floor">
              <TextInput id="filter-floor" inputMode="numeric" />
            </Field>
            <Field label="주차 대수" htmlFor="filter-parking">
              <TextInput id="filter-parking" inputMode="numeric" />
            </Field>
          </div>
          <Field label="세대 수" htmlFor="filter-households">
            <TextInput id="filter-households" inputMode="numeric" placeholder="최소 세대 수" />
          </Field>
          <button type="button" onClick={handleReset} className="btn-secondary">조건 초기화</button>
        </aside>

        <section aria-label="검색 결과" className="flex flex-col gap-4">
          {/* 스크롤 목록이라 서버가 전체 개수를 주지 않아 개수 대신 제목만 보여 준다 */}
          <p className="font-bold">공개 중인 매물</p>
          {list.isPending && <p role="status">매물을 불러오고 있어요.</p>}
          {list.isError && !list.isFetchNextPageError && <p role="alert">매물을 불러오지 못했어요.</p>}
          {list.isSuccess && properties.length === 0 && (
            <p className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
              아직 공개된 매물이 없어요.
            </p>
          )}
          {properties.length > 0 && (
            <div className="grid gap-5 sm:grid-cols-2 xl:grid-cols-3">
              {properties.map((property) => (
                <PropertyCard
                  key={property.propertyId}
                  property={property}
                  isFavorite={favoriteIds.has(property.propertyId)}
                  onToggleFavorite={user.role === ROLES.USER ? toggleFavorite : undefined}
                />
              ))}
            </div>
          )}
          <div ref={sentinelRef} aria-hidden="true" />
          {isFetchingNextPage && <p role="status" className="text-center text-lg text-gray-600">매물을 더 불러오고 있어요.</p>}
          {isFetchNextPageError && (
            <div role="alert" className="flex flex-col items-center gap-2 text-lg text-gray-600">
              다음 매물을 불러오지 못했어요.
              <button type="button" onClick={() => fetchNextPage()} className="btn-secondary btn-sm">다시 시도</button>
            </div>
          )}
          {list.isSuccess && properties.length > 0 && !hasNextPage && (
            <p className="text-center text-lg text-gray-500">모든 매물을 봤어요.</p>
          )}
        </section>
      </div>
    </div>
  )
}

export default PropertyListPage
