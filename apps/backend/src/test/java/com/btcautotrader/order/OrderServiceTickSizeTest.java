package com.btcautotrader.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upbit rejects limit orders whose price is not a multiple of the tick size for its band, and nothing
 * enforced that before — the manual ticket offers step="0.1", so a hand-typed price for a large-cap was
 * rejected by the exchange rather than by us.
 */
class OrderServiceTickSizeTest {

    @Test
    void snapsLargeCapPricesToTheThousandGrid() {
        assertThat(align("KRW-BTC", "101500001"))
                .isEqualByComparingTo(new BigDecimal("101500000"));
    }

    @Test
    void appliesTheBandForEachPriceRange() {
        assertThat(align("KRW-A", "750123")).isEqualByComparingTo(new BigDecimal("750000"));   // 500
        assertThat(align("KRW-A", "123456")).isEqualByComparingTo(new BigDecimal("123400"));   // 100
        assertThat(align("KRW-A", "45678")).isEqualByComparingTo(new BigDecimal("45650"));     // 50
        assertThat(align("KRW-A", "1234")).isEqualByComparingTo(new BigDecimal("1230"));       // 10
        assertThat(align("KRW-A", "567.8")).isEqualByComparingTo(new BigDecimal("567"));       // 1
        assertThat(align("KRW-A", "45.67")).isEqualByComparingTo(new BigDecimal("45.6"));      // 0.1
        assertThat(align("KRW-A", "5.678")).isEqualByComparingTo(new BigDecimal("5.67"));      // 0.01
    }

    @Test
    void leavesPricesAlreadyOnTheGridUnchanged() {
        assertThat(align("KRW-BTC", "101500000")).isEqualByComparingTo(new BigDecimal("101500000"));
    }

    @Test
    void neverSnapsBelowOneTick() {
        // Flooring a sub-tick price to zero would submit a nonsense order.
        assertThat(align("KRW-A", "0.00005")).isEqualByComparingTo(new BigDecimal("0.0001"));
    }

    @Test
    void leavesNonKrwQuotesAlone() {
        // BTC- and USDT-quoted markets use different rules, so guessing would be worse than not touching.
        assertThat(align("BTC-ETH", "0.0512345")).isEqualByComparingTo(new BigDecimal("0.0512345"));
    }

    @Test
    void toleratesMissingInput() {
        assertThat(OrderService.alignToTickSize("KRW-BTC", null)).isNull();
        assertThat(align("KRW-BTC", "0")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private static BigDecimal align(String market, String price) {
        return OrderService.alignToTickSize(market, new BigDecimal(price));
    }
}
