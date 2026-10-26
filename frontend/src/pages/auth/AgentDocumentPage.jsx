// [페이지] 인증 서류 제출 (중개사) — /agent/documents
// 가입 후 2~3일 안에 공인중개사 관련 서류를 올린다. 관리자가 승인하면 중개 기능을 쓸 수 있다
// 요구사항: USER_021, NOTIFY_024 (기한 임박 알림)
// API: POST /api/v1/agents/me/documents (BE-012)
// 필요한 서류 목록은 예시다. 요구사항에는 '공인중개사 관련 서류'로만 적혀 있어 팀 확인 필요
import { useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'

const REQUIRED_DOCUMENTS = ['공인중개사 자격증', '중개사무소 등록증', '사업자등록증']

function AgentDocumentPage() {
  const navigate = useNavigate()

  // TODO: POST /api/v1/agents/me/documents (BE-012) — 서류별 파일을 multipart로 보낸다. 서류 종류 값은 백엔드와 협의
  const handleSubmit = (event) => {
    event.preventDefault()
    navigate('/agent/pending')
  }

  return (
    <div className="page-container">
      <PageHeader title="인증 서류 제출" description="관리자가 확인하면 중개사로 서비스를 이용할 수 있어요." />
      {/* TODO: 마감일은 가입일 기준으로 계산. 가입일 필드가 명세에 없어 백엔드와 협의 필요 */}
      <p className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900">
        <strong>제출 기한이 2일 남았어요.</strong> 가입 후 2~3일 안에 올려주세요.
      </p>
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <div className="grid gap-4 md:grid-cols-3">
          {REQUIRED_DOCUMENTS.map((documentName) => (
            <section key={documentName} aria-label={documentName} className="card flex flex-col gap-3">
              <h2 className="font-bold">{documentName}</h2>
              <FileDropzone title="파일 올리기" hint="사진 또는 PDF" accept="image/*,application/pdf" />
            </section>
          ))}
        </div>
        <button type="submit" className="btn-primary self-end">제출하기</button>
      </form>
    </div>
  )
}

export default AgentDocumentPage
