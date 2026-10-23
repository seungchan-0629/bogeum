# 계약 테이블 준비

소셜 로그인을 추가한 버전에서는 기존 H2 DB에 `create-social-accounts.sql`도 실행해야 합니다. `dev` 프로필은 이 테이블도 자동 생성합니다. 소셜 계정의 제공자·사용자 ID와 회원별 제공자에는 각각 중복 방지 제약이 있습니다.

기본 실행 환경은 `spring.jpa.hibernate.ddl-auto=validate`이므로, 기존 H2 데이터베이스에 연결해 서버 시작 전에 `create-housing-contracts.sql`을 실행하세요. 연결 주소는 실행 시 사용하는 `BOGEUM_DB_URL`과 같아야 합니다. 개발용 `dev` 프로필은 Hibernate가 이 테이블을 생성합니다.

기존 `member_housing_roles` 값에는 어떤 계약의 상대방인지 정보가 없습니다. 자동 이전하거나 테이블을 삭제하지 않습니다. 실제 계약을 확인한 뒤 계약별 임대인과 임차인을 별도로 등록해야 합니다.
