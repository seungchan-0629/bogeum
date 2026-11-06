package com.bogeum.domain.openbanking.model;

/*
userID,금융결제원 쪽 회원번호 , 핀테크이용번호,
사용자토큰/리프래시토큰, 토큰 만료일자
*/

import com.bogeum.domain.user.model.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
//@Table(name = "openBanking")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenBanking
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long openBankingId;


    //userId
    //@OneToOne(fetch = FetchType.LAZY, optional = false)
    //@JoinColumn(name = "user_id", nullable = false, unique = true)
    //private User user;
    private Long userId;

    //금융결제원 사용자 일련번호
    //@Column(name = "user_seq_no", length = 50)
    private String userSeqNo;

    //핀테크 이용 번호
    //@Column(name = "fintech_num", length = 50)
    private String fintechNum;

	// 은행명
	//@Column(name = "bank_name", length = 50)
	private String bankName;

	// 화면 표시용 마스킹된 계좌번호
	//@Column(name = "account_number_masked", length = 30)
	private String accountNumberMasked;

    // 액세스 토큰
    //@Column(name = "access_token", length = 2048)
    private String accessToken;

    // 리프레시 토큰
    //@Column(name = "refresh_token", length = 2048)
    private String refreshToken;

    // 액세스 토큰 만료일
    //@Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;
}
