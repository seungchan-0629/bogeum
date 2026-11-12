// [매물 컴포넌트] 매물 카드 — 매물 목록, 관심 매물, 유사 매물에서 쓴다
// 카드를 누르면 매물 상세(/properties/:propertyId)로 이동한다 (PROPERTY_003)
// - property: { propertyId, thumbnailUrl, propertyType(한글), leaseType, deposit·monthlyRent(만원), area(평), address, nearbySubway }
//   매물 목록은 API 응답을 이 모양으로 바꿔서 넘긴다 (PropertyListPage)
// - onToggleFavorite를 넘기면 오른쪽 위에 하트 버튼이 생긴다. 관심 매물은 임차인만 쓰므로 임차인 화면에서만 넘긴다 (FAVORITE_001)
import { Link } from 'react-router'
import clsx from 'clsx'
import Icon from '../common/Icon.jsx'
import PhotoPlaceholder from '../common/PhotoPlaceholder.jsx'
import { formatLeasePrice } from '../../utils/format.js'

function PropertyCard({ property, isFavorite = false, onToggleFavorite }) {
  return (
    <article className="relative overflow-hidden rounded-2xl border border-gray-200 bg-white">
      <Link to={`/properties/${property.propertyId}`} className="flex flex-col">
        {/* 사진이 없거나 저장소가 없으면 서버가 thumbnailUrl을 비워서 보낸다 */}
        {property.thumbnailUrl ? (
          <img src={property.thumbnailUrl} alt="매물 대표 사진" loading="lazy" className="h-40 w-full bg-[#e6ecf4] object-cover" />
        ) : (
          <PhotoPlaceholder label="대표 사진" className="h-40 rounded-none" />
        )}
        <div className="flex flex-col gap-0.5 px-4 py-3.5">
          <p className="text-lg font-bold">{formatLeasePrice(property)}</p>
          {/* 면적은 평 기준 (PROPERTY_003) */}
          <p className="text-sm text-gray-800">
            {property.propertyType} · {property.area}평
          </p>
          <p className="truncate text-sm text-gray-800">{property.address}</p>
          {/* 중개사가 쉼표로 여러 역을 적을 수 있어 받은 글자 그대로 보여 주고, 길면 한 줄로 자른다 */}
          <p className="truncate text-sm text-gray-500">{property.nearbySubway || '주변 지하철역 정보 없음'}</p>
        </div>
      </Link>
      {onToggleFavorite && (
        <button
          type="button"
          onClick={() => onToggleFavorite(property.propertyId)}
          aria-pressed={isFavorite}
          aria-label={isFavorite ? '관심 매물 해제' : '관심 매물로 저장'}
          className={clsx(
            'absolute top-2.5 right-2.5 flex size-10 items-center justify-center rounded-full bg-white',
            isFavorite ? 'text-rose-600' : 'text-gray-600 hover:text-rose-600',
          )}
        >
          <Icon name="heart" size={18} filled={isFavorite} />
        </button>
      )}
    </article>
  )
}

export default PropertyCard
