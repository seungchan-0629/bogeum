// USER_016 (중개사 회원가입), USER_017 (동의 항목 필수), USER_021 (가입 후 서류 제출 안내)
import { publicApi } from '../../utils/jwtUtil.js'
import { useNavigate } from 'react-router'
import { FormProvider, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import TextField from './TextField.jsx'
import AgreementFields from './AgreementFields.jsx'

// 비밀번호·휴대폰 번호 규칙은 요구사항에 없어 입력 여부만 확인한다
const agentSignupSchema = z
  .object({
    officeName: z.string().trim().min(1, '중개사무소 이름을 입력해 주세요.'),
    representativeName: z.string().trim().min(1, '대표자 이름을 입력해 주세요.'),
    officeAddress: z.string().trim().min(1, '중개사무소 주소를 입력해 주세요.'),
    phone: z.string().trim().min(1, '휴대폰 번호를 입력해 주세요.'),
    email: z.email({ error: '이메일 형식으로 입력해 주세요.' }),
    password: z.string().min(1, '비밀번호를 입력해 주세요.'),
    passwordConfirm: z.string().min(1, '비밀번호를 한 번 더 입력해 주세요.'),
    agreements: z.object({
      privacyCollect: z.literal(true, { error: '필수 항목에 동의해 주세요.' }),
      thirdPartyProvide: z.literal(true, { error: '필수 항목에 동의해 주세요.' }),
    }),
  })
  .refine((values) => values.password === values.passwordConfirm, {
    error: '비밀번호가 일치하지 않아요.',
    path: ['passwordConfirm'],
    when: (payload) => Boolean(payload.value?.password && payload.value?.passwordConfirm),
  })

function AgentSignupForm() {
  const navigate = useNavigate()
  const methods = useForm({
    resolver: zodResolver(agentSignupSchema),
    defaultValues: {
      officeName: '',
      representativeName: '',
      officeAddress: '',
      phone: '',
      email: '',
      password: '',
      passwordConfirm: '',
      agreements: { privacyCollect: false, thirdPartyProvide: false },
    },
  })
  const {
    handleSubmit,
    setError,
    formState: { isSubmitting, errors },
  } = methods

  const onSubmit = async ({ officeName, representativeName, officeAddress, phone, email, password, agreements }) => {
    try {
      await publicApi.post('/v1/auth/signup/agent', {
        officeName, representativeName, officeAddress, phone, email, password, agreements,
      })
    } catch (error) {
      setError('root', { message: error.response?.data?.message || '서버에 연결할 수 없습니다.' })
      return
    }
    window.alert('중개사 가입 신청이 완료되었습니다. 로그인하면 승인 상태를 확인할 수 있습니다.')
    navigate('/login')
  }

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">
        <p className="rounded-xl bg-blue-50 px-4 py-3 text-sm leading-relaxed text-blue-900">
          가입 후 <strong>3일 안에 공인중개사 관련 서류</strong>를 올려 주세요. 관리자 승인이 끝나면 서비스를 이용할 수 있어요.
        </p>

        <TextField name="officeName" label="중개사무소 이름" autoComplete="organization" />
        <TextField name="representativeName" label="대표자 이름" autoComplete="name" />
        <TextField name="officeAddress" label="중개사무소 주소" autoComplete="street-address" />
        <TextField name="phone" label="휴대폰 번호" type="tel" inputMode="numeric" autoComplete="tel" />
        <TextField name="email" label="이메일" type="email" autoComplete="email" />
        <TextField name="password" label="비밀번호" type="password" autoComplete="new-password" />
        <TextField name="passwordConfirm" label="비밀번호 확인" type="password" autoComplete="new-password" />
        <AgreementFields />

        {errors.root && <p role="alert" className="text-sm text-red-600">{errors.root.message}</p>}

        <button
          type="submit"
          disabled={isSubmitting}
          className="h-12 rounded-xl bg-blue-600 text-base font-bold text-white hover:bg-blue-700 disabled:opacity-60"
        >
          가입 신청하기
        </button>
      </form>
    </FormProvider>
  )
}

export default AgentSignupForm
