// [페이지] 매물 찾기 (목록 + 검색·필터) — /properties
// 누가: 임차인·임대인·중개사 (로그인 안 하면 로그인 안내 페이지)
// 요구사항: PROPERTY_004 (목록, 스크롤 방식), PROPERTY_005 (검색·필터, 결과 없음 안내), PROPERTY_009 (중개사 열람), FAVORITE_001·002
// 화면 구성: 왼쪽 검색 조건 / 오른쪽 결과 카드 (모바일에서는 위아래로 쌓인다)
import { useState } from 'react'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import PropertyCard from '../../components/property/PropertyCard.jsx'
import { Field, TextInput, SelectInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { useFavoriteToggle } from '../../hooks/useFavoriteToggle.js'
import { ROLES } from '../../constants/auth.js'
import { SAMPLE_PROPERTIES } from '../../constants/sampleProperties.js'

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

  // TODO: 검색 조건이 바뀌면 GET /api/v1/properties (BE-018) 호출
  // - 쿼리: propertyType, leaseType, depositMin/Max, rentMin/Max, areaMin/Max, households, parkingCount, floor, roomCount, address, nearbySubway
  // - 조건이 없으면 목록 API GET /api/v1/properties?cursor=&size= (BE-017)
  // - 스크롤이 바닥에 닿으면 응답의 nextCursor로 다음 목록을 이어 불러온다 (PROPERTY_004, TanStack Query useInfiniteQuery)
  // 지금은 임시 데이터에서 거래 유형·매물 종류만 화면에서 거른다
  const properties = SAMPLE_PROPERTIES.filter(
    (property) =>
      (leaseTypes.length === 0 || leaseTypes.includes(property.leaseType)) &&
      (propertyTypes.length === 0 || propertyTypes.includes(property.propertyType)),
  )

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
          <p className="font-bold">검색 결과 {properties.length}개</p>
          {properties.length > 0 ? (
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
          ) : (
            // PROPERTY_005: 조건에 맞는 매물이 없으면 안내
            <p className="rounded-xl border border-dashed border-gray-300 px-6 py-10 text-center text-gray-600">
              조건에 맞는 매물이 없어요. 조건을 줄여서 다시 찾아보세요.
            </p>
          )}
        </section>
      </div>
    </div>
  )
}

export default PropertyListPage
