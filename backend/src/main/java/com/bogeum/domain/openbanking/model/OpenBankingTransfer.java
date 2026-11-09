package com.bogeum.domain.openbanking.model;


import com.bogeum.domain.openbanking.TransferStatus;
import com.bogeum.domain.openbanking.TransferType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
우리쪽에 저장해둘 bank_tran_id, transfer_type, 요청금액, status, 날짜,
api_tran_id(금융결제원이 주는 api 거래번호), 응답 코드랑 메세지, 은행쪽 응답 코드랑 메세지
*/
@Entity
@Table(name = "openBankingTransfer")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenBankingTransfer
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long openBankingTransferId;

    //금융결제원에서 관리할 결제내역번호(우리가 만드는식)
    @Column(name = "bank_tran_id", nullable = false, length = 20)
    private String bankTranId;

    //송금인지 출금인지
    @Column(name = "transfer_type", nullable = false)
    private TransferType transfer_type;

    //값
    @Column(name = "request_amount", nullable = false)
    private BigDecimal requestAmount;

    //요청시각
    @Column(name = "request_at",nullable = false)
    private LocalDateTime requestedAt;

    //이체 요청상태
    @Column(name = "transfer_status", nullable = false)
    private TransferStatus status;

    // bank_tran_date: 은행 거래일자, 금융결제원 조회용도
    @Column(name = "bank_tran_date", nullable = false)
    private LocalDate bankTranDate;

    // api_tran_id: 금융결제원 API 거래번호
    @Column(name = "api_tran_id", nullable = false)
    private String apiTranId;

    // rsp_code: API 응답코드
    @Column(name = "api_response_code", nullable = false)
    private String apiResponseCode;

    // rsp_message: API 응답메시지
    @Column(name = "api_response_message", nullable = false)
    private String apiResponseMessage;

    // bank_rsp_code: 은행 응답코드
    @Column(name = "bank_response_code", nullable = false)
    private String bankResponseCode;

    // bank_rsp_message: 은행 응답메시지
    @Column(name = "bank_response_message", nullable = false)
    private String bankResponseMessage;
}
