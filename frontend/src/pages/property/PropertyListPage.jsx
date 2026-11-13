// [페이지] 매물 찾기 (목록 + 검색·필터) — /properties
// 누가: 임차인·임대인·중개사 (로그인 안 하면 로그인 안내 페이지)
// 요구사항: PROPERTY_004 (목록, 스크롤 방식), PROPERTY_005 (검색·필터, 결과 없음 안내), PROPERTY_009 (중개사 열람), FAVORITE_001·002
// 화면 구성: 왼쪽 검색 조건 / 오른쪽 결과 카드 (모바일에서는 위아래로 쌓인다)
// 검색 조건은 주소창(/properties?leaseTypes=JEONSE&region=…)에 둔다
// - 메인 검색창은 이 주소로 보내기만 하면 되고, 상세를 봤다가 뒤로 가도 조건이 그대로 남는다
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import PropertyFilterPanel from '../../components/property/PropertyFilterPanel.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { ROLES } from '../../constants/auth.js'
import { getProperties } from '../../api/property/index.js'
import { getPropertyTypeLabel } from '../../utils/format.js'

// 조건을 바꾸고 이만큼 더 바꾸지 않으면 다시 찾는다. 글자를 칠 때마다·막대를 끄는 동안 요청이 쏟아지지 않게 한다
const APPLY_DELAY_MS = 300

// 입력 중인 검색 조건. 금액은 원 단위 문자열이다
const EMPTY_FILTER = {
  leaseTypes: [], propertyTypes: [], region: '', subway: '',
  minDeposit: '', maxDeposit: '', minMonthlyRent: '', maxMonthlyRent: '',
  minFloor: '', minArea: '', minRoomCount: '', minParkingCount: '', minHouseholdCount: '',
}
const LIST_KEYS = ['leaseTypes', 'propertyTypes']
const TEXT_KEYS = ['region', 'subway', 'minDeposit', 'maxDeposit', 'minMonthlyRent', 'maxMonthlyRent',
  'minFloor', 'minArea', 'minRoomCount', 'minParkingCount', 'minHouseholdCount']

// 주소창 → 입력 칸. 서버 쿼리 이름을 그대로 쓴다
function toFilter(searchParams) {
  const filter = { ...EMPTY_FILTER }
  LIST_KEYS.forEach((key) => {
    filter[key] = searchParams.get(key)?.split(',').filter(Boolean) ?? []
  })
  TEXT_KEYS.forEach((key) => {
    filter[key] = searchParams.get(key) ?? ''
  })
  // 층은 지상 층만 찾는다. 주소창에 음수(지하)나 숫자가 아닌 값이 오면 조건 없이 둔다
  if (!/^\d+$/.test(filter.minFloor)) filter.minFloor = ''
  return filter
}

// 입력 칸 → 주소창. 비어 있는 조건은 빼서, 조건을 하나도 안 고르면 주소창도 비고 전체 목록이 나온다
function toSearchParams(filter) {
  const params = new URLSearchParams()
  LIST_KEYS.forEach((key) => {
    if (filter[key].length > 0) params.set(key, filter[key].join(','))
  })
  TEXT_KEYS.forEach((key) => {
    const value = filter[key].trim()
    // 면적을 "."까지만 입력한 중간 상태는 숫자가 아니라 보내지 않는다
    if (value && !(key === 'minArea' && Number.isNaN(Number(value)))) params.set(key, value)
  })
  return params
}

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

function PropertyListPage() {
  const user = useAuthStore((state) => state.user)
  const { favoriteIds, toggleFavorite, isPending, errorOf } = useFavoriteToggle()
  const [searchParams, setSearchParams] = useSearchParams()
  // 지금 목록에 적용된 조건. 주소창 그대로라 query key로도 쓴다
  const appliedQuery = searchParams.toString()
  const hasConditions = appliedQuery !== ''
  const [filter, setFilter] = useState(() => toFilter(searchParams))

  // 주소창이 바깥에서 바뀌면(메인 검색창, 뒤로 가기) 입력 칸도 맞춘다. 입력 칸이 이미 같으면 그대로 둔다
  // effect 대신 그리는 중에 바꿔서, 옛 입력 칸으로 한 번 더 그리지 않는다
  const [syncedQuery, setSyncedQuery] = useState(appliedQuery)
  if (syncedQuery !== appliedQuery) {
    setSyncedQuery(appliedQuery)
    if (toSearchParams(filter).toString() !== appliedQuery) setFilter(toFilter(searchParams))
  }

  // 조건을 바꾸면 잠깐 기다렸다가 주소창에 적용한다. 조건 칸을 바꿀 때마다 방문 기록이 쌓이지 않게 replace로 바꾼다
  useEffect(() => {
    const nextQuery = toSearchParams(filter).toString()
    if (nextQuery === appliedQuery) return
    const timer = setTimeout(() => setSearchParams(nextQuery, { replace: true }), APPLY_DELAY_MS)
    return () => clearTimeout(timer)
  }, [filter, appliedQuery, setSearchParams])

  const handleReset = () => {
    setFilter(EMPTY_FILTER)
    setSearchParams('', { replace: true })
  }

  // PROPERTY_004·005: 조건에 맞는 공개 매물을 12개씩 불러오고, 스크롤이 바닥에 닿으면 같은 조건으로 다음 목록을 이어 불러온다
  // 조건이 바뀌면 query key가 바뀌어 첫 목록부터 다시 불러온다
  // 새 목록이 올 때까지 이전 목록을 그대로 둔다. 목록이 잠깐 사라지면 화면 길이가 줄어 스크롤이 위로 튄다
  const list = useInfiniteQuery({
    queryKey: ['properties', appliedQuery],
    queryFn: ({ pageParam, signal }) => getProperties(Object.fromEntries(new URLSearchParams(appliedQuery)), pageParam, signal),
    initialPageParam: null,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.nextCursor : undefined),
    placeholderData: keepPreviousData,
  })
  const properties = list.data?.pages.flatMap((page) => page.properties).map(toCardProperty) ?? []
  // 이전 조건의 목록을 보여 주는 중 (새 조건의 목록을 불러오는 중)
  const isSearching = list.isPlaceholderData

  // 목록 맨 아래 표시 칸이 화면에 들어오면 다음 목록을 불러온다. 불러오다 실패하면 자동으로 다시 부르지 않고 [다시 시도]를 보여 준다
  // 이전 조건의 목록을 보여 주는 동안에는 그 목록의 다음을 부르지 않는다
  const sentinelRef = useRef(null)
  const { hasNextPage, isFetchingNextPage, isFetchNextPageError, fetchNextPage } = list
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel || !hasNextPage || isFetchingNextPage || isFetchNextPageError || isSearching) return
    // 바닥에 닿기 조금 전(200px)부터 불러와 스크롤이 끊기지 않게 한다
    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) fetchNextPage()
    }, { rootMargin: '200px' })
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [hasNextPage, isFetchingNextPage, isFetchNextPageError, isSearching, fetchNextPage])

  return (
    <div className="page-container">
      <PageHeader title="매물 찾기" description="조건을 고르면 바로 모두 맞는 매물만 보여드려요." />
      <div className="grid items-start gap-6 lg:grid-cols-[280px_1fr]">
        <PropertyFilterPanel filter={filter} onChange={(changes) => setFilter((prev) => ({ ...prev, ...changes }))}
          onReset={handleReset} />

        <section aria-label="검색 결과" className="flex flex-col gap-4">
          {/* 스크롤 목록이라 서버가 전체 개수를 주지 않아 개수 대신 제목만 보여 준다 */}
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <p className="font-bold">{hasConditions ? '검색 결과' : '공개 중인 매물'}</p>
            {isSearching && <p role="status" className="text-sm text-gray-500">조건에 맞는 매물을 찾고 있어요.</p>}
          </div>
          {list.isPending && <p role="status">매물을 불러오고 있어요.</p>}
          {list.isError && !list.isFetchNextPageError && <p role="alert">매물을 불러오지 못했어요.</p>}
          {list.isSuccess && properties.length === 0 && (
            // PROPERTY_005: 조건에 맞는 매물이 없으면 안내
            <p className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
              {hasConditions ? '조건에 맞는 매물이 없어요. 조건을 줄여서 다시 찾아보세요.' : '아직 공개된 매물이 없어요.'}
            </p>
          )}
          {properties.length > 0 && (
            <div className={clsx('grid gap-5 transition-opacity sm:grid-cols-2 xl:grid-cols-3', isSearching && 'opacity-50')}>
              {properties.map((property) => (
                <PropertyCard
                  key={property.propertyId}
                  property={property}
                  isFavorite={favoriteIds.has(property.propertyId)}
                  onToggleFavorite={user.role === ROLES.USER ? toggleFavorite : undefined}
                  isFavoritePending={isPending(property.propertyId)}
                  favoriteError={errorOf(property.propertyId)}
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
