package com.bogeum.domain.openbanking;

import com.bogeum.domain.openbanking.model.OpenBanking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

/*
계좌등록, 유저 토큰발급, 기업oob토큰 발급
등록계좌조회 ,이체결과조회 , 토큰 리프래시, 입금, 출금
*/
@Service
@Validated
@RequiredArgsConstructor
public class OpenBankingService
{
    private final OpenBankingRepository openBankingRepository;
    private final OpenBankingTransferRepository openBankingTransferRepository;

    @Transactional
    public OpenBanking registerAccount(String code, String state)
    {
        return openBankingRepository.saveAndFlush();
    }

}
