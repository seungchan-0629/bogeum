// [임시 데이터] API 연결 전 화면 확인용 매물 목록. 매물 목록·관심 매물·유사 매물 화면이 함께 쓴다
// API가 연결되면 각 화면의 TODO 주석대로 응답으로 바꾸고 이 파일은 지운다
// 필드는 API 명세 BE-017/018 응답 기준. SALE(매매)·salePrice·nearbySubway 표시 형식은 백엔드와 협의 필요
export const SAMPLE_PROPERTIES = [
  { propertyId: 1, propertyType: '오피스텔', leaseType: 'MONTHLY', deposit: 1000, monthlyRent: 65, area: 8, address: '서울 강남구 역삼동', nearbySubway: '역삼역 도보 5분' },
  { propertyId: 2, propertyType: '아파트', leaseType: 'JEONSE', deposit: 32000, monthlyRent: 0, area: 24, address: '서울 마포구 공덕동', nearbySubway: '공덕역 도보 7분' },
  { propertyId: 3, propertyType: '빌라', leaseType: 'MONTHLY', deposit: 500, monthlyRent: 45, area: 10, address: '서울 관악구 봉천동', nearbySubway: '서울대입구역 도보 9분' },
  { propertyId: 4, propertyType: '아파트', leaseType: 'SALE', deposit: 0, monthlyRent: 0, salePrice: 95000, area: 32, address: '경기 성남시 분당구', nearbySubway: '정자역 도보 6분' },
  { propertyId: 5, propertyType: '원룸', leaseType: 'MONTHLY', deposit: 300, monthlyRent: 50, area: 6, address: '서울 동작구 흑석동', nearbySubway: '흑석역 도보 4분' },
  { propertyId: 6, propertyType: '투룸', leaseType: 'JEONSE', deposit: 18000, monthlyRent: 0, area: 14, address: '서울 성북구 안암동', nearbySubway: '안암역 도보 3분' },
]
