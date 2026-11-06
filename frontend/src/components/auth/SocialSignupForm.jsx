import { FormProvider, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import TextField from './TextField.jsx'
import IdentityVerificationField from './IdentityVerificationField.jsx'
import AgreementFields from './AgreementFields.jsx'

const signupSchema = z.object({
  name: z.string().trim().min(1, '이름을 입력해 주세요.').max(50, '이름은 50자 이하로 입력해 주세요.'),
  email: z.string().trim().max(255).pipe(z.email({ error: '이메일 형식으로 입력해 주세요.' })),
  phone: z.string().trim().min(1, '휴대폰 번호를 입력해 주세요.'),
  verificationToken: z.string().min(1, '본인 인증을 완료해 주세요.'),
  agreements: z.object({
    privacyCollect: z.literal(true, { error: '필수 항목에 동의해 주세요.' }),
    thirdPartyProvide: z.literal(true, { error: '필수 항목에 동의해 주세요.' }),
  }),
})

function SocialSignupForm({ providerLabel, profile, onSubmit, onCancel }) {
  const methods = useForm({
    resolver: zodResolver(signupSchema),
    defaultValues: {
      name: profile.name ?? '',
      email: profile.email ?? '',
      phone: '',
      verificationToken: '',
      agreements: { privacyCollect: false, thirdPartyProvide: false },
    },
  })
  const { handleSubmit, setError, formState: { errors, isSubmitting } } = methods

  const handleSignup = async (values) => {
    try {
      await onSubmit(values)
    } catch (error) {
      setError('root', { message: error.response?.data?.message || '가입을 완료하지 못했습니다. 다시 시도해 주세요.' })
    }
  }

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(handleSignup)} noValidate className="flex flex-col gap-6">
        <div>
          <h2 className="text-lg font-bold">{providerLabel} 회원가입</h2>
          <p className="mt-2 text-sm text-gray-600">휴대폰 인증과 필수 동의를 완료하면 가입과 로그인이 함께 완료돼요.</p>
        </div>
        <TextField name="name" label="이름" autoComplete="name" readOnly={Boolean(profile.name)} hint={profile.name ? `${providerLabel}에서 제공한 이름이에요.` : undefined} />
        <TextField name="email" label="이메일" type="email" autoComplete="email" readOnly={Boolean(profile.email)} hint={profile.email ? `${providerLabel}에서 제공한 이메일이에요.` : undefined} />
        <IdentityVerificationField />
        <AgreementFields />
        {errors.root && <p role="alert" className="text-sm text-red-600">{errors.root.message}</p>}
        <button type="submit" disabled={isSubmitting} className="h-12 rounded-xl bg-blue-600 text-base font-bold text-white hover:bg-blue-700 disabled:opacity-60">
          {isSubmitting ? '가입 중…' : '가입하고 로그인'}
        </button>
        <button type="button" onClick={onCancel} disabled={isSubmitting} className="text-sm text-gray-600 underline">로그인으로 돌아가기</button>
      </form>
    </FormProvider>
  )
}

export default SocialSignupForm
