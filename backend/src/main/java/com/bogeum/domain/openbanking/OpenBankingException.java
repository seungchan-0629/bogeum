package com.bogeum.domain.openbanking;


/*
안쪽에 OpenbankingException을 상속하는 형태로 에러들을 구현
특히 200으로 반환되지만 에러인것들도 잡아내서 이쪽으로 throw
*/
public class OpenBankingException extends RuntimeException {
    public OpenBankingException(String message) {
        super(message);
    }
}
