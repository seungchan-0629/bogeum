// USER_003 (사용자 회원가입), USER_004 (동의 항목 필수)
// 가입할 때는 임대인·임차인을 고르지 않는다. 계약을 등록할 때 정해진다 (PROPERTY_011)
import { FormProvider, useForm } from 'react-hook-form'
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
  const methods = useForm({
    resolver: zodResolver(userSignupSchema),
    defaultValues: {
      name: '',
      loginId: '',
      email: '',
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

  // TODO: POST /api/v1/auth/signup/user (BE-010) 연결
  // - 요청: { name, loginId, email, password, verificationToken, agreements: { privacyCollect, thirdPartyProvide } }
  //   role(USER)은 이 API에서 서버가 정하는 것으로 가정. 명세(BE-010)의 role 필드를 뺄지 백엔드와 협의 필요
  //   (passwordConfirm은 화면 확인용이라 보내지 않는다)
  // - 응답: { userId } → 가입 완료 안내 후 '/login'으로 이동
  const onSubmit = async () => {}

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
