## 📋 Summary
- 일반 회원가입 API 구현 및 회원가입 화면 연동 (USER_003, USER_004)

## 📂 변경 파일
- 'backend/src/main/java/com/bogeum/domain/user/UserController.java'
- 'backend/src/main/java/com/bogeum/domain/user/UserService.java'
- 'backend/src/main/java/com/bogeum/domain/user/UserException.java'
- 'backend/src/main/java/com/bogeum/domain/user/model/User.java'
- 'backend/src/main/java/com/bogeum/domain/user/model/UserDto.java'
- 'backend/src/main/java/com/bogeum/common/exception/ErrorCode.java'

- 'backend/src/test/java/com/bogeum/domain/user/UserControllerTest.java'

- 'frontend/src/components/auth/UserSignupForm.jsx'
- 'frontend/src/constants/auth.js'

## ✨ 주요 변경 사항
- POST /api/v1/auth/signup/user 회원가입 API 추가
- 필수 동의(개인정보 수집·이용, 제3자 제공)를 회원 정보와 같은 트랜잭션으로 저장
- 이메일 중복 시 EMAIL_ALREADY_EXISTS, 필수 동의 누락 시 INVALID_SIGNUP 에러 응답

- 회원가입 화면에서 API 호출, 성공 시 로그인 화면으로 이동하고 실패 시 서버 에러 메시지 표시

Closes #