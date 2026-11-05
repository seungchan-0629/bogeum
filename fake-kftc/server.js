import { createHash, randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import { fileURLToPath } from 'node:url'

const REDIRECT_URI = 'http://localhost:5173/mypage/openbanking/callback'
const page = readFileSync(new URL('./authorize.html', import.meta.url), 'utf8')
export const BANKS = ['국민은행', '신한은행', '우리은행', '하나은행', '농협은행', '카카오뱅크', '토스뱅크']

// 계좌 등록 흐름만 흉내 내는 로컬 서버다. 실제 은행 인증이나 이체는 하지 않는다.
export function createFakeKftcServer() {
  const codes = new Map()
  const tokens = new Map()

  return createServer(async (request, response) => {
    const url = new URL(request.url, 'http://localhost:8090')
    response.setHeader('Cache-Control', 'no-store')
    const send = (status, data) => {
      response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
      response.end(JSON.stringify(data))
    }
    const isAuthorizationValid = (params) => params.get('client_id') === 'bogeum'
      && params.get('response_type') === 'code'
      && params.get('redirect_uri') === REDIRECT_URI
      && /^[a-zA-Z0-9]{32}$/.test(params.get('state') ?? '')

    try {
      if (request.method === 'GET' && ['/', '/oauth/2.0/authorize'].includes(url.pathname)) {
        if (url.search && !isAuthorizationValid(url.searchParams)) {
          return send(400, { message: '인증 요청이 올바르지 않습니다. 보금에서 계좌 등록을 다시 시작해 주세요.' })
        }
        response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' })
        return response.end(page.replace('<!-- BANK_OPTIONS -->', BANKS.map((bank) => `<option>${bank}</option>`).join('')))
      }

      if (request.method === 'POST') {
        let body = ''
        for await (const chunk of request) {
          body += chunk
          if (body.length > 16384) return send(413, { message: '요청이 너무 큽니다.' })
        }
        const params = new URLSearchParams(body)

        if (url.pathname === '/oauth/2.0/authorize') {
          if (!isAuthorizationValid(params)) return send(400, { message: '유효하지 않은 인증 요청입니다.' })
          const callback = new URL(REDIRECT_URI)
          callback.searchParams.set('state', params.get('state'))
          if (params.get('action') === 'cancel') {
            callback.searchParams.set('error', 'access_denied')
          } else {
            const bankName = params.get('bank_name')
            const accountNumber = (params.get('account_num') ?? '').replaceAll('-', '')
            const holderName = (params.get('account_holder_name') ?? '').trim()
            if (!BANKS.includes(bankName) || !/^\d{10,16}$/.test(accountNumber)
              || !holderName || holderName.length > 50 || params.get('agree') !== 'Y') {
              return send(400, { message: '은행, 계좌번호(숫자 10~16자리), 예금주와 동의를 확인해 주세요.' })
            }
            const identity = createHash('sha256').update(`${bankName}:${accountNumber}`).digest('hex')
            const code = randomUUID()
            codes.set(code, {
              clientId: params.get('client_id'),
              scope: params.get('scope') || 'login inquiry transfer',
              userName: holderName,
              userSeqNo: BigInt(`0x${identity.slice(0, 12)}`).toString().padStart(10, '0').slice(0, 10),
              account: {
                bank_name: bankName,
                account_holder_name: holderName,
                account_num_masked: `${'*'.repeat(accountNumber.length - 4)}${accountNumber.slice(-4)}`,
                fintech_use_num: BigInt(`0x${identity}`).toString().slice(0, 24),
              },
              expiresAt: Date.now() + 10 * 60 * 1000,
            })
            callback.searchParams.set('code', code)
          }
          response.writeHead(303, { Location: callback.href })
          return response.end()
        }

        if (url.pathname === '/oauth/2.0/token') {
          const authorization = codes.get(params.get('code'))
          if (params.get('grant_type') !== 'authorization_code' || !authorization
            || authorization.expiresAt <= Date.now() || params.get('redirect_uri') !== REDIRECT_URI
            || params.get('client_id') !== authorization.clientId) {
            return send(400, { error: 'invalid_grant' })
          }
          codes.delete(params.get('code'))
          const accessToken = randomUUID()
          tokens.set(accessToken, { ...authorization, expiresAt: Date.now() + 3600000 })
          return send(200, {
            access_token: accessToken, token_type: 'Bearer', expires_in: 3600,
            refresh_token: randomUUID(), scope: authorization.scope, user_seq_no: authorization.userSeqNo,
          })
        }
      }

      if (request.method === 'GET' && url.pathname === '/v2.0/user/me') {
        const authorization = tokens.get(request.headers.authorization?.replace(/^Bearer /, ''))
        if (!authorization || authorization.expiresAt <= Date.now()
          || url.searchParams.get('user_seq_no') !== authorization.userSeqNo) {
          return send(401, { rsp_code: 'O0003', rsp_message: '유효하지 않은 사용자 토큰입니다.' })
        }
        return send(200, {
          rsp_code: 'A0000', rsp_message: '성공', user_name: authorization.userName,
          user_seq_no: authorization.userSeqNo, res_list: [authorization.account],
        })
      }
      send(404, { message: '계좌 등록용 가상 서버에서 지원하지 않는 경로입니다.' })
    } catch (error) {
      console.error('가상 금결원 요청 처리 실패:', error.message)
      if (!response.headersSent) send(500, { message: '요청을 처리하지 못했습니다.' })
      else response.end()
    }
  })
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  createFakeKftcServer().listen(8090, '127.0.0.1', () => {
    console.log('가상 금결원: http://localhost:8090 (종료: Ctrl+C)')
  })
}
