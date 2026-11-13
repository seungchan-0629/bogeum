// [매물 컴포넌트] 매물 찾기 왼쪽 검색 조건 (PROPERTY_005)
// - filter: 입력 중인 조건 (PropertyListPage의 EMPTY_FILTER 모양). 값을 바꾸면 onChange(바뀐 칸)로 알린다
// - onReset: 조건 초기화
// 조건을 바꾸면 목록 화면이 잠깐 기다렸다가 바로 다시 찾는다 (따로 [검색] 버튼이 없다)
import clsx from 'clsx'
import { Field, TextInput, SelectInput } from '../common/FormControls.jsx'
import PriceRangeSlider from './PriceRangeSlider.jsx'

// 매매는 서비스에서 다루지 않아 전세·월세만 고른다
const LEASE_TYPE_OPTIONS = [
  { value: 'JEONSE', label: '전세' },
  { value: 'MONTHLY', label: '월세' },
]
// 매물 정보 올리기 화면의 매물 종류와 같다 (PROPERTY_010)
const PROPERTY_TYPE_OPTIONS = [
  { value: 'APARTMENT', label: '아파트' },
  { value: 'VILLA', label: '빌라' },
  { value: 'OFFICETEL', label: '오피스텔' },
  { value: 'MULTI_FAMILY', label: '다가구주택' },
]
const ROOM_COUNT_OPTIONS = [
  { value: '', label: '전체' },
  { value: '1', label: '1개 이상' },
  { value: '2', label: '2개 이상' },
  { value: '3', label: '3개 이상' },
]

// 보증금 0 ~ 10억: 1억까지는 1천만 원씩, 그 위는 5천만 원씩. 오른쪽 끝은 "10억 이상"이라 더 비싼 매물도 나온다
const DEPOSIT_STEPS = [
  ...Array.from({ length: 11 }, (_, index) => index * 10_000_000),
  ...Array.from({ length: 18 }, (_, index) => 150_000_000 + index * 50_000_000),
]
// 월세 0 ~ 500만: 10만 원씩
const MONTHLY_RENT_STEPS = Array.from({ length: 51 }, (_, index) => index * 100_000)

// 숫자 칸에는 숫자만 남긴다. 면적은 소수 둘째 자리까지 받는다 (서버 검사와 같다)
const onlyDigits = (value) => value.replace(/[^0-9]/g, '')
const onlyDecimal = (value) => value.replace(/[^0-9.]/g, '').match(/^\d*(\.\d{0,2})?/)[0]

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

function ChipGroup({ legend, options, isSelected, onToggle }) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-2 text-sm font-bold">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <ToggleChip key={option.value} isSelected={isSelected(option.value)} onClick={() => onToggle(option.value)}>
            {option.label}
          </ToggleChip>
        ))}
      </div>
    </fieldset>
  )
}

function toggleInList(list, value) {
  return list.includes(value) ? list.filter((item) => item !== value) : [...list, value]
}

function PropertyFilterPanel({ filter, onChange, onReset }) {
  // 전세만 고르면 월세가 없어 월세 막대는 쓸 일이 없다
  const isJeonseOnly = filter.leaseTypes.length === 1 && filter.leaseTypes[0] === 'JEONSE'

  return (
    // 넓은 화면에서는 목록을 스크롤해도 화면 위에 붙어 따라온다. 필터가 화면보다 길면 필터 안에서만 스크롤한다
    // 좁은 화면에서는 목록 위에 쌓이므로 고정하지 않는다 (고정하면 목록을 가린다)
    <aside aria-label="검색 조건"
      className="card flex flex-col gap-5 lg:sticky lg:top-6 lg:max-h-[calc(100svh-3rem)] lg:overflow-y-auto">

      <ChipGroup legend="거래 유형" options={LEASE_TYPE_OPTIONS} isSelected={(value) => filter.leaseTypes.includes(value)}
        onToggle={(value) => onChange({ leaseTypes: toggleInList(filter.leaseTypes, value) })} />
      <ChipGroup legend="매물 종류" options={PROPERTY_TYPE_OPTIONS} isSelected={(value) => filter.propertyTypes.includes(value)}
        onToggle={(value) => onChange({ propertyTypes: toggleInList(filter.propertyTypes, value) })} />
      <Field label="지역" htmlFor="filter-region">
        <TextInput id="filter-region" value={filter.region} maxLength={50} placeholder="ex. 서울 강남구"
          onChange={(event) => onChange({ region: event.target.value })} />
      </Field>
      <Field label="주변 지하철역" htmlFor="filter-subway" hint="역 이름 일부만 입력해도 찾아요">
        <TextInput id="filter-subway" value={filter.subway} maxLength={50} placeholder="ex. 고속터미널"
          onChange={(event) => onChange({ subway: event.target.value })} />
      </Field>

      <PriceRangeSlider id="filter-deposit" label="보증금" steps={DEPOSIT_STEPS}
        value={{ min: filter.minDeposit, max: filter.maxDeposit }}
        onChange={({ min, max }) => onChange({ minDeposit: min, maxDeposit: max })} />
      <PriceRangeSlider id="filter-rent" label="월세" steps={MONTHLY_RENT_STEPS}
        value={{ min: filter.minMonthlyRent, max: filter.maxMonthlyRent }}
        onChange={({ min, max }) => onChange({ minMonthlyRent: min, maxMonthlyRent: max })}
        disabled={isJeonseOnly} hint={isJeonseOnly ? '전세는 월세가 없어요.' : undefined} />

      {/* 칸 5개 중 안내 문구가 있는 희망 층수만 한 줄 전체를 쓰고, 나머지 4개를 2×2로 놓아 빈칸 없이 맞춘다 */}
      <div className="grid grid-cols-2 gap-x-2 gap-y-4">
        {/* 지상 층만 찾는다. 저층(1층·반지하)을 피하고 싶을 때 쓴다 */}
        <div className="col-span-2">
          <Field label="희망 층수" htmlFor="filter-floor" hint="입력한 층부터 위층 매물을 보여요">
            <TextInput id="filter-floor" inputMode="numeric" value={filter.minFloor} placeholder="ex. 3"
              onChange={(event) => onChange({ minFloor: onlyDigits(event.target.value) })} />
          </Field>
        </div>
        <Field label="최소 면적 (평)" htmlFor="filter-area">
          <TextInput id="filter-area" inputMode="decimal" value={filter.minArea} placeholder="ex. 10"
            onChange={(event) => onChange({ minArea: onlyDecimal(event.target.value) })} />
        </Field>
        <Field label="방 개수" htmlFor="filter-rooms">
          <SelectInput id="filter-rooms" options={ROOM_COUNT_OPTIONS} value={filter.minRoomCount}
            onChange={(event) => onChange({ minRoomCount: event.target.value })} />
        </Field>
        <Field label="최소 주차 대수" htmlFor="filter-parking">
          <TextInput id="filter-parking" inputMode="numeric" value={filter.minParkingCount} placeholder="ex. 1"
            onChange={(event) => onChange({ minParkingCount: onlyDigits(event.target.value) })} />
        </Field>
        <Field label="최소 세대 수" htmlFor="filter-households">
          <TextInput id="filter-households" inputMode="numeric" value={filter.minHouseholdCount} placeholder="ex. 100"
            onChange={(event) => onChange({ minHouseholdCount: onlyDigits(event.target.value) })} />
        </Field>
      </div>
      <button type="button" onClick={onReset} className="btn-secondary">조건 초기화</button>
    </aside>
  )
}

export default PropertyFilterPanel
