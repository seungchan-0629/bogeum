// [페이지] 404 · 없는 페이지 — 등록되지 않은 모든 주소 (router의 path: '*')
// 누가: 누구나. 불이 거의 꺼진 건물로 '찾을 수 없는 집'을 표현한다
import { Link } from 'react-router'
import GuideScreen from '../../components/common/GuideScreen.jsx'

function NotFoundPage() {
  return (
    <GuideScreen code="404" title="이 집은 찾을 수 없어요" description="주소가 바뀌었거나 없어진 페이지예요." litRatio={0.04}>
      <Link to="/" className="btn-primary">메인으로</Link>
      <Link to="/properties" className="btn-secondary">매물 찾기</Link>
    </GuideScreen>
  )
}

export default NotFoundPage
