package com.btcautotrader.strategy;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
class StrategyControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StrategyService strategyService;

    @Test
    void replaceMarketOverrides_normalizesMarketAndSavesRatios() throws Exception {
        when(strategyService.configuredMarkets()).thenReturn(List.of("KRW-BTC", "KRW-ETH"));
        when(strategyService.replaceMarketOverrides(any())).thenReturn(
                new StrategyMarketOverridesResponse(
                        List.of("KRW-BTC", "KRW-ETH"),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                "KRW-ETH",
                                new StrategyMarketRatios(4.5, 2.1, 2.0, 40.0, 100.0, 0.0, 0.0)
                        )
                )
        );

        String body = """
                {
                  "ratiosByMarket": {
                    "krw-eth": {
                      "takeProfitPct": 4.5,
                      "stopLossPct": 2.1,
                      "trailingStopPct": 2.0,
                      "partialTakeProfitPct": 40.0,
                      "stopExitPct": 100.0,
                      "trendExitPct": 0.0,
                      "momentumExitPct": 0.0
                    }
                  }
                }
                """;

        mockMvc.perform(
                        put("/api/strategy/market-overrides")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratiosByMarket['KRW-ETH'].takeProfitPct").value(4.5));

        ArgumentCaptor<StrategyMarketOverridesRequest> captor = ArgumentCaptor.forClass(StrategyMarketOverridesRequest.class);
        verify(strategyService).replaceMarketOverrides(captor.capture());

        StrategyMarketOverridesRequest saved = captor.getValue();
        assertThat(saved).isNotNull();
        assertThat(saved.ratiosByMarket()).containsKey("KRW-ETH");
        assertThat(saved.ratiosByMarket().get("KRW-ETH").takeProfitPct()).isEqualTo(4.5);
        assertThat(saved.ratiosByMarket().get("KRW-ETH").stopLossPct()).isEqualTo(2.1);
    }

    @Test
    void replaceMarketOverrides_rejectsNotConfiguredMarket() throws Exception {
        when(strategyService.configuredMarkets()).thenReturn(List.of("KRW-BTC"));

        String body = """
                {
                  "ratiosByMarket": {
                    "KRW-ETH": {
                      "takeProfitPct": 4.0
                    }
                  }
                }
                """;

        mockMvc.perform(
                        put("/api/strategy/market-overrides")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['ratiosByMarket.KRW-ETH']").value("market is not configured"));

        verify(strategyService, never()).replaceMarketOverrides(any());
    }
}
