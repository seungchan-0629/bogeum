import { publicApi } from '../../utils/jwtUtil.js'
// USER_003 (사용자 회원가입), USER_004 (동의 항목 필수)
// 가입할 때는 임대인·임차인을 고르지 않는다. 계약을 등록할 때 정해진다 (PROPERTY_011)
import { FormProvider, useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import TextField from './TextField.jsx'
import IdentityVerificationField from './IdentityVerificationField.jsx'
import AgreementFields from './AgreementFields.jsx'

// 비밀번호 규칙은 요구사항에 없어 입력 여부만 확인한다
const userSignupSchema = z
  .object({
    name: z.string().trim().min(1, '이름을 입력해 주세요.'),
    loginId: z.email({ error: '아이디는 이메일 형식으로 입력해 주세요.' }),
    email: z.email({ error: '이메일 형식으로 입력해 주세요.' }),
    phone: z.string().trim().min(1, '휴대폰 번호를 입력해 주세요.'),
    password: z.string().min(1, '비밀번호를 입력해 주세요.'),
    passwordConfirm: z.string().min(1, '비밀번호를 한 번 더 입력해 주세요.'),
    verificationToken: z.string().min(1, '본인 인증을 완료해 주세요.'),
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

function UserSignupForm() {
  const navigate = useNavigate()
  const methods = useForm({
    resolver: zodResolver(userSignupSchema),
    defaultValues: {
      name: '',
      loginId: '',
      email: '',
      phone: '',
      password: '',
      passwordConfirm: '',
      verificationToken: '',
      agreements: { privacyCollect: false, thirdPartyProvide: false },
    },
  })
  const {
    handleSubmit,
    formState: { isSubmitting },
  } = methods

  // 현재 API는 email을 로그인 아이디로 사용하며, 비밀번호 확인과 임시 인증 토큰은 받지 않는다.
  const onSubmit = async ({ name, email, phone, password, agreements }) => {
    try {
      await publicApi.post('/v1/auth/signup/user', { name, email, phone, password, agreements })
    } catch (error) {
      window.alert(error.response?.data?.message || '서버에 연결할 수 없습니다.')
      return
    }

    window.alert('회원가입이 완료되었습니다. 로그인해 주세요.')
    navigate('/login')
  }

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">
        <TextField name="name" label="이름" autoComplete="name" />
        <TextField name="loginId" label="아이디" type="email" autoComplete="username" hint="이메일 형식으로 입력해 주세요." />
        <TextField name="email" label="이메일" type="email" autoComplete="email" />
        <TextField name="password" label="비밀번호" type="password" autoComplete="new-password" />
        <TextField name="passwordConfirm" label="비밀번호 확인" type="password" autoComplete="new-password" />
        <IdentityVerificationField />
        <AgreementFields />

        <button
          type="submit"
          disabled={isSubmitting}
          className="h-12 rounded-xl bg-blue-600 text-base font-bold text-white hover:bg-blue-700 disabled:opacity-60"
        >
          가입하기
        </button>
      </form>
    </FormProvider>
  )
}

export default UserSignupForm
