// USER_003 (임대인·임차인 회원가입), USER_004 (동의 항목 필수)
import { FormProvider, useForm, useWatch } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import clsx from 'clsx'
import TextField from './TextField.jsx'
import IdentityVerificationField from './IdentityVerificationField.jsx'
import AgreementFields from './AgreementFields.jsx'
import { ROLES, ROLE_LABELS } from '../../constants/auth.js'

const SIGNUP_ROLES = [ROLES.LANDLORD, ROLES.TENANT]

// 비밀번호 규칙은 요구사항에 없어 입력 여부만 확인한다
const userSignupSchema = z
  .object({
    role: z.enum(SIGNUP_ROLES, { error: '임대인 또는 임차인을 선택해 주세요.' }),
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
      loginId: '',
      email: '',
      password: '',
      passwordConfirm: '',
      verificationToken: '',
      agreements: { privacyCollect: false, thirdPartyProvide: false },
    },
  })
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = methods
  const selectedRole = useWatch({ control, name: 'role' })

  // TODO: POST /api/v1/auth/signup/user (BE-010) 연결
  // - 요청: { loginId, email, password, role, verificationToken, agreements: { privacyCollect, thirdPartyProvide } }
  //   (passwordConfirm은 화면 확인용이라 보내지 않는다)
  // - 응답: { userId } → 가입 완료 안내 후 '/login'으로 이동
  const onSubmit = async () => {}

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-6">
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1.5 text-sm font-medium text-gray-700">가입 유형</legend>
          <div className="grid grid-cols-2 gap-2">
            {SIGNUP_ROLES.map((role) => (
              <label
                key={role}
                className={clsx(
                  'flex h-12 cursor-pointer items-center justify-center rounded-xl border text-[15px] font-bold has-focus-visible:outline-2 has-focus-visible:outline-blue-600',
                  selectedRole === role ? 'border-blue-600 bg-blue-50 text-blue-800' : 'border-gray-300 text-gray-700 hover:bg-gray-50',
                )}
              >
                <input type="radio" value={role} className="sr-only" {...register('role')} />
                {ROLE_LABELS[role]}
              </label>
            ))}
          </div>
          {errors.role && <p className="text-sm text-red-600">{errors.role.message}</p>}
        </fieldset>

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
