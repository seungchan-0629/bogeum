// [페이지] 입주 하자 사진 확인 (임대인) — /care/move-in (입주·AS의 두 번째 탭)
// 임차인이 입주 때 올린 하자 사진을 본다
// 요구사항: MOVEIN_002, NOTIFY_017 (업로드 알림)
// API: GET /api/v1/contracts/{contractId}/movein-defects (BE-068)
import StatusBadge from '../../components/common/StatusBadge.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'

// TODO: GET /api/v1/contracts/{contractId}/movein-defects (BE-068) 응답으로 교체 (계약별 사진 목록과 설명, 업로드일)
// 임대인이 어떤 계약을 볼지 고르는 목록 API가 명세에 없어 백엔드와 협의 필요
const SAMPLE_DEFECTS = {
  propertyTitle: '역삼동 오피스텔',
  tenantName: '김보금',
  contractedAt: '2026.10.01',
  uploadedAt: '2026.10.05',
  photos: ['거실 벽지 얼룩', '욕실 타일 깨짐', '싱크대 문짝 흠집', '창틀 곰팡이'],
}

function DefectCheckPage() {
  return (
    <div className="flex flex-col gap-5">
      <div className="card flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col">
          <span className="font-bold">{SAMPLE_DEFECTS.propertyTitle} · 임차인 {SAMPLE_DEFECTS.tenantName}</span>
          <span className="text-sm text-gray-600">
            계약 {SAMPLE_DEFECTS.contractedAt} · 업로드 {SAMPLE_DEFECTS.uploadedAt} · {SAMPLE_DEFECTS.photos.length}장
          </span>
        </div>
        <StatusBadge tone="green">기한 내 제출</StatusBadge>
      </div>
      <ul className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {SAMPLE_DEFECTS.photos.map((caption) => (
          <li key={caption} className="card flex flex-col gap-2 p-2">
            <PhotoPlaceholder className="h-36" />
            <span className="px-1 text-sm">{caption}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default DefectCheckPage
