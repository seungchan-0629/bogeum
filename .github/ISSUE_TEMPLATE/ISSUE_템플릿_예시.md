---
name: 이슈 템플릿
about: 이슈 템플릿
title: "[Feat] 제목 작성"
labels: Feat
assignees: ''

---

## 📋 요구사항 정보

| 항목 | 내용 |
|------|------|
| **요구사항 ID** | `USER_004` |
| **분류** | 백엔드 > 회원 |
| **기능** | 회원가입 필수 동의 항목 저장 |
| **담당자** | - |
| **우선순위** | - |
| **구현 대상** | 백엔드 |

## 📌 요구사항

- 회원가입 시 개인정보 수집·이용, 제3자 제공 동의를 각각 필수로 받아 저장한다.

## 📝 상세 내용

- `Consent` Entity 필드 정의 및 JPA 매핑 (`user_consent` 테이블)
- `ConsentType` enum 정의 (`PRIVACY_COLLECTION`, `THIRD_PARTY_PROVISION`)
- 회원 저장과 동의 저장을 같은 트랜잭션으로 처리
- 필수 동의가 빠지면 `INVALID_SIGNUP` 예외 반환
- 패키지 구조: `user/model`, `user/repository`, `user/service`, `user/controller`
