package com.btcautotrader.strategy;

import com.btcautotrader.auth.CurrentUserService;
import com.btcautotrader.auth.UserEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StrategyController.class)
@AutoConfigureMockMvc(addFilters = false)
class StrategyControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StrategyService strategyService;

    @MockBean
    private CurrentUserService currentUserService;

    @Test
    void getStrategy_returnsCurrentConfig() throws Exception {
        when(strategyService.getConfig()).thenReturn(new StrategyConfig(
                true,
                30000.0,
                2.4,
                1.28,
                0.9,
                35.0,
                "BALANCED",
                100.0,
                40.0,
                25.0,
                0.7
        ));

        mockMvc.perform(get("/api/strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxOrderKrw").value(30000.0))
                .andExpect(jsonPath("$.profile").value("BALANCED"));
    }

    @Test
    void updateStrategy_rejectsInvalidProfile() throws Exception {
        String body = """
                {
                  "enabled": true,
                  "maxOrderKrw": 30000,
                  "takeProfitPct": 2.4,
                  "stopLossPct": 1.28,
                  "trailingStopPct": 0.9,
                  "partialTakeProfitPct": 35.0,
                  "profile": "FAST",
                  "stopExitPct": 100.0,
                  "trendExitPct": 40.0,
                  "momentumExitPct": 25.0,
                  "riskPerTradePct": 0.7
                }
                """;

        mockMvc.perform(
                        put("/api/strategy")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.profile").value("must be AGGRESSIVE, BALANCED, or CONSERVATIVE"));
    }

    @Test
    void updateRatios_forwardsValidatedPayload() throws Exception {
        when(strategyService.updateRatios(any())).thenReturn(new StrategyConfig(
                true,
                30000.0,
                3.0,
                1.28,
                0.9,
                35.0,
                "BALANCED",
                100.0,
                40.0,
                25.0,
                0.7
        ));

        String body = """
                {
                  "takeProfitPct": 3.0
                }
                """;

        mockMvc.perform(
                        patch("/api/strategy/ratios")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.takeProfitPct").value(3.0));

        verify(strategyService).updateRatios(any());
    }

    @Test
    void getPresets_returnsPresetList() throws Exception {
        when(strategyService.getPresets()).thenReturn(List.of(
                new StrategyPresetItem("BALANCED", "밸런스", 2.4, 1.28, 0.9, 35.0, 100.0, 40.0, 25.0)
        ));

        mockMvc.perform(get("/api/strategy/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BALANCED"));
    }

    @Test
    void replaceMarketOverrides_normalizesMarketAndSavesRatios() throws Exception {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(7L);
        when(currentUserService.requireUser(any())).thenReturn(user);
        when(strategyService.replaceMarketOverrides(eq(7L), any())).thenReturn(
                new StrategyMarketOverridesResponse(
                        List.of("KRW-BTC", "KRW-ETH"),
                        Map.of("KRW-BTC", 30000.0, "KRW-ETH", 12000.0),
                        Map.of("KRW-BTC", "BALANCED", "KRW-ETH", "AGGRESSIVE"),
                        Map.of("KRW-BTC", false, "KRW-ETH", true),
                        Map.of(
                                "KRW-ETH",
                                new StrategyMarketRatios(4.5, 2.1, 2.0, 40.0, 100.0, 0.0, 0.0)
                        )
                )
        );

        String body = """
                {
                  "markets": ["krw-btc", "krw-eth"],
                  "profileByMarket": {
                    "krw-eth": "aggressive"
                  },
                  "tradePausedByMarket": {
                    "krw-eth": true
                  },
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
                .andExpect(jsonPath("$.markets[1]").value("KRW-ETH"))
                .andExpect(jsonPath("$.ratiosByMarket['KRW-ETH'].takeProfitPct").value(4.5));

        ArgumentCaptor<StrategyMarketOverridesRequest> captor = ArgumentCaptor.forClass(StrategyMarketOverridesRequest.class);
        verify(strategyService).replaceMarketOverrides(eq(7L), captor.capture());

        StrategyMarketOverridesRequest saved = captor.getValue();
        assertThat(saved.markets()).containsExactly("KRW-BTC", "KRW-ETH");
        assertThat(saved.profileByMarket()).containsEntry("KRW-ETH", "AGGRESSIVE");
        assertThat(saved.tradePausedByMarket()).containsEntry("KRW-ETH", true);
        assertThat(saved.ratiosByMarket().get("KRW-ETH").takeProfitPct()).isEqualTo(4.5);
    }

    @Test
    void replaceMarketOverrides_rejectsNotConfiguredMarket() throws Exception {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(7L);
        when(currentUserService.requireUser(any())).thenReturn(user);

        String body = """
                {
                  "markets": ["KRW-BTC"],
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

        verify(strategyService, never()).replaceMarketOverrides(eq(7L), any());
    }

    @Test
    void replaceMarketOverrides_allowsEmptyMarketList() throws Exception {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(7L);
        when(currentUserService.requireUser(any())).thenReturn(user);
        when(strategyService.replaceMarketOverrides(eq(7L), any())).thenReturn(
                new StrategyMarketOverridesResponse(
                        List.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()
                )
        );

        String body = """
                {
                  "markets": []
                }
                """;

        mockMvc.perform(
                        put("/api/strategy/market-overrides")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markets").isEmpty());

        verify(strategyService).replaceMarketOverrides(eq(7L), any());
    }
}
