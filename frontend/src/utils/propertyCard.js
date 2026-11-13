import { getPropertyTypeLabel } from './format.js'

// 매물 카드(PropertyCard)용 모양으로 바꾼다. 매물 찾기 목록과 관심 매물 목록이 같은 응답 칸을 써서 함께 쓴다
// 금액은 원 → 만원(formatLeasePrice가 만원 단위), 면적은 평, 매물 종류는 한글 이름
export function toCardProperty(property) {
  return {
    propertyId: property.propertyId,
    thumbnailUrl: property.thumbnailUrl,
    propertyType: getPropertyTypeLabel(property.propertyType),
    leaseType: property.leaseType,
    deposit: property.deposit / 10000,
    monthlyRent: property.monthlyRent / 10000,
    area: property.areaPyeong,
    address: property.roadAddress,
    nearbySubway: property.nearbySubwayStation,
  }
}
