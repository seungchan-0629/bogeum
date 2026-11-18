// MOVEIN_001: 선택한 계약에 입주 하자 사진과 사진별 설명을 등록한다.
import { useEffect, useRef, useState } from 'react'
import { useOutletContext } from 'react-router'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { TextInput } from '../../components/common/FormControls.jsx'
import { uploadMoveInPhotos } from '../../api/contract/index.js'
import { useAuthStore } from '../../stores/authStore.js'

function PhotoPreview({ file }) {
  const imageRef = useRef(null)
  useEffect(() => {
    const preview = URL.createObjectURL(file)
    imageRef.current.src = preview
    return () => URL.revokeObjectURL(preview)
  }, [file])
  return <img ref={imageRef} alt={file.name} className="h-32 w-full rounded-lg object-cover" />
}

function MoveInUploadPage() {
  const { selectedContract } = useOutletContext()
  const userId = useAuthStore((state) => state.user?.userId)
  // 계약·사용자 변경 시 파일 선택기의 내부 파일명도 함께 초기화한다.
  return <MoveInUploadForm key={`${userId}:${selectedContract?.contractId}`} contractId={selectedContract?.contractId} userId={userId} />
}

function MoveInUploadForm({ contractId, userId }) {
  const [photos, setPhotos] = useState([])
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [pickerVersion, setPickerVersion] = useState(0)
  const pending = useRef(false)
  const active = useRef(false)
  const nextId = useRef(0)

  useEffect(() => {
    active.current = true
    // 요청 취소로 서버 저장 취소를 보장할 수 없다. 이전 응답의 화면 반영만 막는다.
    return () => { active.current = false }
  }, [])

  const handleSelect = (files) => {
    if (pending.current || !contractId || !userId) return
    setSuccess('')
    // 선택 결과는 아래 목록에 표시하고 같은 파일도 다시 선택할 수 있게 입력을 비운다.
    setPickerVersion((version) => version + 1)
    if (photos.length + files.length > 10) {
      setError('사진은 한 번에 1~10장 선택해 주세요.')
      return
    }
    setError('')
    const selected = files.map((file) => ({ id: nextId.current++, file, caption: '' }))
    setPhotos((previous) => [...previous, ...selected])
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    if (pending.current) return
    setSuccess('')
    if (!contractId || !userId) {
      setError('위의 계약한 매물에서 사진을 등록할 매물을 선택해 주세요.')
      return
    }
    if (photos.length < 1 || photos.length > 10 || photos.some((photo) => photo.caption.length > 255)) {
      setError('사진은 1~10장, 설명은 사진마다 255자 이내로 입력해 주세요.')
      return
    }
    pending.current = true
    setSubmitting(true)
    setError('')
    try {
      const result = await uploadMoveInPhotos(contractId, photos)
      if (!active.current) return
      setSuccess(`이번에 사진 ${result.photos.length}장을 등록했어요.`)
      setPhotos([])
      setPickerVersion((version) => version + 1)
    } catch (failure) {
      if (!active.current) return
      if (!failure.response) {
        setError('등록 결과를 확인하지 못했어요. 서버에 저장되었을 수 있으며 자동으로 재전송하지 않아요. 다시 제출하면 중복 등록될 수 있어요.')
      } else {
        setError(failure.response.data?.message || '사진을 등록하지 못했어요. 입력 내용을 확인하고 잠시 후 다시 시도해 주세요.')
      }
    } finally {
      pending.current = false
      if (active.current) setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5">
      <p className="rounded-xl bg-slate-100 px-4 py-3 text-slate-800">
        <strong>계약일부터 7일 후까지 마지막 날을 포함해 사진을 올릴 수 있어요.</strong> 실제 등록 가능 여부는 서버가 한국 날짜를 기준으로 확인해요.
      </p>
      {!contractId && <p role="status">위의 계약한 매물에서 매물을 선택해 주세요. 계약한 매물이 없으면 사진을 등록할 수 없어요.</p>}
      <p className="text-sm text-gray-600">선택한 로컬 사진 미리보기예요. 등록한 사진의 다시 보기·수정·삭제는 아직 제공하지 않아요.</p>
      {error && <p role="alert" className="text-red-600">{error}</p>}
      {success && <p role="status" className="text-green-700">{success}</p>}
      <fieldset disabled={submitting || !contractId || !userId} className="flex min-w-0 flex-col gap-5">
        <FileDropzone key={pickerVersion} title="사진을 눌러서 선택하세요" hint="한 번에 1~10장, 설명은 사진마다 255자 이내로 입력해 주세요" multiple onChange={handleSelect} />
        <ul className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {photos.map((photo, index) => (
            <li key={photo.id} className="card flex flex-col gap-2 p-2">
              <PhotoPreview file={photo.file} />
              <span className="break-all text-sm">{photo.file.name}</span>
              <label htmlFor={`defect-caption-${photo.id}`}>사진 {index + 1} 설명 (선택)</label>
              <TextInput id={`defect-caption-${photo.id}`} value={photo.caption} maxLength={255} size="sm" onChange={(event) => {
                setPhotos((previous) => previous.map((item) => item.id === photo.id ? { ...item, caption: event.target.value } : item))
                setError('')
              }} />
              <button type="button" onClick={() => {
                setPhotos((previous) => previous.filter((item) => item.id !== photo.id))
                setError('')
              }} aria-label={`사진 ${index + 1} 선택 취소`} className="self-end px-1 text-sm text-gray-600 hover:text-gray-900">선택 취소</button>
            </li>
          ))}
        </ul>
        <button type="submit" disabled={photos.length === 0 || photos.length > 10 || photos.some((photo) => photo.caption.length > 255)} className="btn-primary self-end">
          {submitting ? '등록 중…' : `${photos.length}장 제출하기`}
        </button>
      </fieldset>
    </form>
  )
}

export default MoveInUploadPage
