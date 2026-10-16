package com.assessment.disputeportal.account;

import com.assessment.disputeportal.account.mapper.AccountNumberMask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberMaskTest {
    @Test
    void masksWellFormedNumbersAndPreservesLeadingZeroes() {
        assertThat(AccountNumberMask.mask("1234567890")).isEqualTo("****7890");
        assertThat(AccountNumberMask.mask("0123456789")).isEqualTo("****6789");
        assertThat(AccountNumberMask.isWellFormed("0123456789")).isTrue();
    }

    @Test
    void masksEveryMalformedValueAsExactlyFourAsterisks() {
        for (String value : new String[] {"123456789", "12345678901", "", "   ", null,
                "abcdefghij", "12345-7890", "12345 7890", "1234567890\n",
                "１２３４５６７８９０", "😀😀😀😀😀"}) {
            assertThat(AccountNumberMask.isWellFormed(value)).isFalse();
            assertThat(AccountNumberMask.mask(value)).isEqualTo("****");
        }
    }
}
