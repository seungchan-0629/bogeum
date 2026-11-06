package com.bogeum.domain.openbanking;

/*
송금인지 출금인지
 */
public enum TransferType
{
    WITHDRAW,   // 출금: 임차인 → 보금
    REMITTANCE  // 송금: 보금 → 임대인
}
