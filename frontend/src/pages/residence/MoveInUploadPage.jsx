// [페이지] 입주 하자 사진 업로드 (임차인) — /my-home/move-in (내 집 관리의 세 번째 탭)
// 입주 전부터 있던 하자를 사진으로 남겨 퇴실 때 분쟁을 막는다
// 요구사항: MOVEIN_001 (업로드, 계약 후 기한 제한), NOTIFY_018 (기한 알림)
// API: POST /api/v1/contracts/{contractId}/movein-defects (BE-067)
// 업로드 기한(예: 7일)은 요구사항에 '1주 이내 등'으로만 적혀 있어 팀 확정 필요
import { useState } from 'react'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { TextInput } from '../../components/common/FormControls.jsx'

// TODO: 이미 올린 사진은 GET /api/v1/contracts/{contractId}/movein-defects (BE-068) 응답으로 채운다
const SAMPLE_PHOTOS = ['거실 벽지 얼룩', '욕실 타일 깨짐', '싱크대 문짝 흠집', '창틀 곰팡이']

function MoveInUploadPage() {
  const [photos, setPhotos] = useState(SAMPLE_PHOTOS)

  // TODO: POST /api/v1/contracts/{contractId}/movein-defects (BE-067) — 사진과 설명을 multipart로 보낸다
  // 기한이 지나면 서버가 거절하고, 화면은 업로드 칸을 잠근다
  const handleSubmit = (event) => {
    event.preventDefault()
  }

  const handleRemove = (caption) => {
    setPhotos((prev) => prev.filter((item) => item !== caption))
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <p className="rounded-xl bg-slate-100 px-4 py-3 text-slate-800">
        <strong>계약 체결 후 7일 안에 올려주세요 · 2일 남음.</strong> 입주 전부터 있던 하자를 기록해 두면 퇴실할 때 분쟁을 막을 수 있어요.
      </p>
      <FileDropzone title="사진을 눌러서 선택하세요" hint="하자가 잘 보이게 찍어주세요" multiple />
      <ul className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {photos.map((caption, index) => (
          <li key={caption} className="card flex flex-col gap-2 p-2">
            <PhotoPlaceholder className="h-32" />
            <label htmlFor={`defect-caption-${index}`} className="sr-only">사진 설명</label>
            <TextInput id={`defect-caption-${index}`} defaultValue={caption} size="sm" />
            <button type="button" onClick={() => handleRemove(caption)} className="self-end px-1 text-sm text-gray-600 hover:text-gray-900">삭제</button>
          </li>
        ))}
      </ul>
      <button type="submit" className="btn-primary self-end">{photos.length}장 제출하기</button>
    </form>
  )
}

export default MoveInUploadPage
