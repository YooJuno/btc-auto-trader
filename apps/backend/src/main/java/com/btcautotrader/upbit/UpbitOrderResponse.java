package com.btcautotrader.upbit;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpbitOrderResponse(
        String uuid,
        String side,
        @JsonProperty("ord_type") String ordType,
        String price,
        String state,
        String market,
        @JsonProperty("created_at") String createdAt,
        String volume,
        @JsonProperty("remaining_volume") String remainingVolume,
        @JsonProperty("reserved_fee") String reservedFee,
        @JsonProperty("remaining_fee") String remainingFee,
        @JsonProperty("paid_fee") String paidFee,
        String locked,
        @JsonProperty("executed_volume") String executedVolume,
        @JsonProperty("trades_count") Integer tradesCount,
        @JsonProperty("time_in_force") String timeInForce
) {
}
