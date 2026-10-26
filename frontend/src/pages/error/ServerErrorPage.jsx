// [페이지] 500 · 오류 — 화면을 그리다 오류가 나면 router의 errorElement로 이 화면이 뜬다
// 개발 중에는 오류 내용을 콘솔에 남겨 원인을 찾을 수 있게 한다
import { useEffect } from 'react'
import { Link, useRouteError } from 'react-router'
import GuideScreen from '../../components/common/GuideScreen.jsx'

function ServerErrorPage() {
  const error = useRouteError()

  useEffect(() => {
    console.error(error)
  }, [error])

  return (
    <GuideScreen code="500" title="잠시 불이 꺼졌어요" description="일시적인 오류예요. 잠시 후 다시 시도해 주세요." litRatio={0}>
      <button type="button" onClick={() => window.location.reload()} className="btn-primary">다시 시도</button>
      <Link to="/" className="btn-secondary">메인으로</Link>
    </GuideScreen>
  )
}

export default ServerErrorPage
