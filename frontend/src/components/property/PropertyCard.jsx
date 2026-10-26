// [매물 컴포넌트] 매물 카드 — 매물 목록, 관심 매물, 유사 매물에서 쓴다
// 카드를 누르면 매물 상세(/properties/:propertyId)로 이동한다 (PROPERTY_003)
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
        {/* TODO: 응답의 thumbnailUrl이 있으면 <img>로 바꾼다 */}
        <PhotoPlaceholder label="대표 사진" className="h-40 rounded-none" />
        <div className="flex flex-col gap-0.5 px-4 py-3.5">
          <p className="text-lg font-bold">{formatLeasePrice(property)}</p>
          {/* 면적은 평 기준 (PROPERTY_003) */}
          <p className="text-sm text-gray-800">
            {property.propertyType} · {property.area}평 · {property.address}
          </p>
          {/* TODO: 등기 확인 여부 필드가 응답에 없어 백엔드와 협의 필요. 지금은 모두 확인 완료로 표시 */}
          <p className="text-sm text-gray-500">{property.nearbySubway} · 등기 확인 완료</p>
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
