import { z } from 'zod'

// 회원의 실제 계정 역할이 아니라 사유 기준 목록 분류다. 역할 조합은 서버가 검증한다.
export const RISK_REASONS = {
  RENT_UNPAID: { label: '월세 미지급', tone: 'red', userType: 'TENANT' },
  RENT_OVERDUE: { label: '연체', tone: 'amber', userType: 'TENANT' },
  DEPOSIT_UNPAID: { label: '보증금 미지급', tone: 'red', userType: 'LANDLORD' },
  FALSE_LISTING: { label: '허위 매물 등록', tone: 'amber', userType: 'LANDLORD' },
  NO_PROPERTY_IMAGE: { label: '매물 이미지 미등록', tone: 'amber', userType: 'AGENT' },
  AGENT_CHANGE_HISTORY: { label: '중개사 교체 이력', tone: 'amber', userType: 'AGENT' },
}

export const riskUserSchema = z.object({
  userId: z.string().trim().regex(/^\d+$/, '회원 ID는 양의 정수로 입력해 주세요.')
    .refine((value) => Number.isSafeInteger(Number(value)) && Number(value) > 0, '회원 ID는 정확히 표현할 수 있는 양의 정수로 입력해 주세요.')
    .transform(Number),
  reasonType: z.enum(Object.keys(RISK_REASONS), { error: '위험 사유를 선택해 주세요.' }),
  // TEXT 한도는 글자 수가 아닌 UTF-8 바이트 수다. 공백과 줄바꿈을 그대로 보낸다.
  detail: z.string().refine((value) => new TextEncoder().encode(value).length <= 65_535, '상세 내용은 UTF-8 기준 65,535바이트 이하로 입력해 주세요.'),
})

// RISK_003: 화면의 빈 칸이 기존 null을 빈 문자열로 바꾸지 않도록 요청 값을 복원한다.
export function createRiskUserUpdateSchema(original, isDetailEdited = () => true) {
  return z.object({
    reasonType: riskUserSchema.shape.reasonType,
    // blur로 읽은 DOM 줄바꿈 대신, 실제 입력 전에는 원문을 검증·전송한다.
    detail: z.preprocess((value) => isDetailEdited() ? value : (original.detail ?? ''), riskUserSchema.shape.detail),
    updateReason: z.string()
      .refine((value) => value.trim().length > 0, '수정 사유를 입력해 주세요.')
      // BE @Size(String.length)와 같은 UTF-16 길이로 검사한다.
      .refine((value) => value.length <= 255, '수정 사유는 255자 이하로 입력해 주세요.'),
  }).transform((values) => ({
    ...values,
    detail: original.detail === null && values.detail === '' ? null : values.detail,
  })).refine((values) => values.reasonType !== original.reasonType || values.detail !== original.detail, {
    message: '위험 사유 또는 상세 내용을 변경해 주세요.',
    path: ['detail'],
  })
}
