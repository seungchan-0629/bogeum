// 다음(카카오) 우편번호 서비스로 도로명 주소를 검색한다. API 키가 필요 없고, 처음 검색할 때만 스크립트를 불러온다
const POSTCODE_SCRIPT_URL = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js'

let scriptPromise = null

function loadPostcodeScript() {
  if (window.daum?.Postcode) return Promise.resolve()
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = POSTCODE_SCRIPT_URL
      script.onload = resolve
      script.onerror = () => {
        // 실패했으면 다음 클릭에서 다시 불러온다
        scriptPromise = null
        reject(new Error('주소 검색을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'))
      }
      document.head.appendChild(script)
    })
  }
  return scriptPromise
}

// 고른 도로명 주소를 돌려준다. 고르지 않고 창을 닫으면 null
export async function searchRoadAddress() {
  await loadPostcodeScript()
  return new Promise((resolve) => {
    new window.daum.Postcode({
      // 지번 주소를 골라도 도로명 주소로 저장한다 (중개사 지역 배정이 도로명 주소 기준)
      oncomplete: (data) => resolve(data.roadAddress || data.autoRoadAddress || null),
      onclose: (state) => {
        if (state === 'FORCE_CLOSE') resolve(null)
      },
    }).open()
  })
}
