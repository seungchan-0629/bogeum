// [매물 컴포넌트] 매물 카드 — 매물 목록, 관심 매물, 유사 매물에서 쓴다
// 카드를 누르면 매물 상세(/properties/:propertyId)로 이동한다 (PROPERTY_003)
// - property: { propertyId, thumbnailUrl, propertyType(한글), leaseType, deposit·monthlyRent(만원), area(평), address, nearbySubway }
//   매물 목록은 API 응답을 이 모양으로 바꿔서 넘긴다 (PropertyListPage)
// - onToggleFavorite를 넘기면 오른쪽 위에 하트 버튼이 생긴다. 관심 매물은 사용자(USER)만 쓰므로 그 화면에서만 넘긴다 (FAVORITE_001·002)
//   isFavoritePending: 담기·빼기 요청 중이면 하트를 잠깐 막는다 / favoriteError: 실패 안내를 하트 바로 아래에 잠깐 보여 준다
// - isUnavailable: 담아 둔 뒤 공개가 끝난 매물(관심 매물 화면, FAVORITE_003). 회색으로 보여 주고 상세로 보내지 않는다
//   상세는 공개 중인 매물만 열려서다. 하트는 그대로 눌러 관심 매물에서 뺄 수 있다
import { Link } from 'react-router'
import clsx from 'clsx'
import Icon from '../common/Icon.jsx'
import PhotoPlaceholder from '../common/PhotoPlaceholder.jsx'
import { formatLeasePrice } from '../../utils/format.js'

function PropertyCard({ property, isFavorite = false, onToggleFavorite, isFavoritePending = false, favoriteError,
  isUnavailable = false }) {
  const content = (
    <>
      <div className="relative">
        {/* 사진이 없거나 저장소가 없으면 서버가 thumbnailUrl을 비워서 보낸다 */}
        {property.thumbnailUrl ? (
          <img src={property.thumbnailUrl} alt="매물 대표 사진" loading="lazy" className="h-40 w-full bg-[#e6ecf4] object-cover" />
        ) : (
          <PhotoPlaceholder label="대표 사진" className="h-40 rounded-none" />
        )}
        {isUnavailable && (
          <span className="absolute bottom-2.5 left-2.5 rounded-full bg-gray-900/80 px-3 py-1 text-xs font-medium text-white">
            더 이상 공개되지 않는 매물이에요
          </span>
        )}
      </div>
      <div className="flex flex-col gap-0.5 px-4 py-3.5">
        {/* 주소가 카드 제목이다. 자르지 않고 다 보여 주되, 한 줄 주소도 두 줄 높이를 차지해 카드 높이를 맞춘다 */}
        <h3 className="min-h-[2lh] text-lg leading-snug font-bold break-keep">{property.address}</h3>
        <p className="mt-1 font-bold text-gray-900">{formatLeasePrice(property)}</p>
        {/* 면적은 평 기준 (PROPERTY_003) */}
        <p className="text-sm text-gray-800">
          {property.propertyType} · {property.area}평
        </p>
        {/* 중개사가 쉼표로 여러 역을 적을 수 있어 받은 글자 그대로 보여 주고, 길면 한 줄로 자른다 */}
        <p className="truncate text-sm text-gray-500">{property.nearbySubway || '주변 지하철역 정보 없음'}</p>
      </div>
    </>
  )

  return (
    <article className="relative overflow-hidden rounded-2xl border border-gray-200 bg-white">
      {/* 공개가 끝난 매물은 링크 대신 흐린 상자로 둬서 눌러도 상세로 가지 않는다. 하트는 이 상자 밖이라 흐려지지 않는다 */}
      {isUnavailable ? (
        <div className="flex flex-col opacity-60 grayscale">{content}</div>
      ) : (
        <Link to={`/properties/${property.propertyId}`} className="flex flex-col">{content}</Link>
      )}
      {onToggleFavorite && (
        <button
          type="button"
          onClick={() => onToggleFavorite(property.propertyId)}
          disabled={isFavoritePending}
          aria-pressed={isFavorite}
          aria-label={isFavorite ? '관심 매물 해제' : '관심 매물로 저장'}
          className={clsx(
            'absolute top-2.5 right-2.5 flex size-10 items-center justify-center rounded-full bg-white disabled:cursor-wait',
            isFavorite ? 'text-rose-600' : 'text-gray-600 hover:text-rose-600',
          )}
        >
          <Icon name="heart" size={18} filled={isFavorite} />
        </button>
      )}
      {/* 사진 위, 하트 바로 아래에 실패 안내를 잠깐 띄운다. 카드를 눌러 상세로 가는 링크는 가리지 않게 링크 밖에 둔다 */}
      {favoriteError && (
        <p role="alert" className="absolute top-14 right-2.5 left-2.5 rounded-lg border border-rose-200 bg-rose-50 px-2.5 py-2 text-xs leading-snug text-rose-800">
          {favoriteError}
        </p>
      )}
    </article>
  )
}

export default PropertyCard
