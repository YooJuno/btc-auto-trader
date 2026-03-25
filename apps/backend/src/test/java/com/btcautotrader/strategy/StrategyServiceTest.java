package com.btcautotrader.strategy;

import com.btcautotrader.auth.UserSettingsResponse;
import com.btcautotrader.auth.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @Mock
    private StrategyConfigRepository configRepository;

    @Mock
    private StrategyPresetRepository presetRepository;

    @Mock
    private StrategyMarketOverrideRepository marketOverrideRepository;

    @Mock
    private UserSettingsService userSettingsService;

    private StrategyService strategyService;

    @BeforeEach
    void setUp() {
        strategyService = new StrategyService(
                configRepository,
                marketOverrideRepository,
                presetRepository,
                userSettingsService,
                "KRW-BTC, KRW-ETH"
        );
    }

    @Test
    void configuredMarket_returnsFirstConfiguredMarket() {
        assertThat(strategyService.configuredMarket()).isEqualTo("KRW-BTC");
    }

    @Test
    void configuredMarkets_prefersUserSettingsWhenPresent() {
        when(userSettingsService.findPreferredMarkets(7L)).thenReturn(Optional.of(List.of("KRW-XRP", "KRW-ETH")));

        assertThat(strategyService.configuredMarkets(7L)).containsExactly("KRW-XRP", "KRW-ETH");
    }

    @Test
    void configuredMarkets_fallsBackToApplicationMarketsWhenUserSettingsEmpty() {
        when(userSettingsService.findPreferredMarkets(7L)).thenReturn(Optional.empty());

        assertThat(strategyService.configuredMarkets(7L)).containsExactly("KRW-BTC", "KRW-ETH");
    }

    @Test
    void configuredMarkets_keepsExplicitlyEmptyUserMarketSelection() {
        when(userSettingsService.findPreferredMarkets(7L)).thenReturn(Optional.of(List.of()));

        assertThat(strategyService.configuredMarkets(7L)).isEmpty();
    }

    @Test
    void getConfig_usesBalancedAnd30000AsDefault() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());
        when(configRepository.save(any(StrategyConfigEntity.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        StrategyConfig config = strategyService.getConfig();

        assertThat(config.maxOrderKrw()).isEqualTo(30000.0);
        assertThat(config.profile()).isEqualTo("BALANCED");
        assertThat(config.takeProfitPct()).isEqualTo(1.44);
        assertThat(config.stopLossPct()).isEqualTo(1.024);
        assertThat(config.trailingStopPct()).isEqualTo(0.675);
        assertThat(config.riskPerTradePct()).isEqualTo(0.7);
    }

    @Test
    void getConfig_upgradesLegacyBalancedDefaultRatios() {
        StrategyConfigEntity legacy = new StrategyConfigEntity(
                1L,
                true,
                30000.0,
                2.4,
                1.28,
                0.9,
                35.0,
                StrategyProfile.BALANCED.name(),
                100.0,
                40.0,
                25.0,
                0.7
        );
        when(configRepository.findById(1L)).thenReturn(Optional.of(legacy));
        when(configRepository.save(any(StrategyConfigEntity.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        StrategyConfig config = strategyService.getConfig();

        assertThat(config.takeProfitPct()).isEqualTo(1.44);
        assertThat(config.stopLossPct()).isEqualTo(1.024);
        assertThat(config.trailingStopPct()).isEqualTo(0.675);
    }

    @Test
    void getConfig_upgradesPreviousBalancedDefaultRatios() {
        StrategyConfigEntity previous = new StrategyConfigEntity(
                1L,
                true,
                30000.0,
                1.92,
                1.024,
                0.675,
                35.0,
                StrategyProfile.BALANCED.name(),
                100.0,
                40.0,
                25.0,
                0.7
        );
        when(configRepository.findById(1L)).thenReturn(Optional.of(previous));
        when(configRepository.save(any(StrategyConfigEntity.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        StrategyConfig config = strategyService.getConfig();

        assertThat(config.takeProfitPct()).isEqualTo(1.44);
        assertThat(config.stopLossPct()).isEqualTo(1.024);
        assertThat(config.trailingStopPct()).isEqualTo(0.675);
    }

    @Test
    void getConfig_preservesExistingRatios() {
        StrategyConfigEntity baseline = new StrategyConfigEntity(
                1L,
                true,
                30000.0,
                2.4,
                1.6,
                1.2,
                35.0,
                StrategyProfile.BALANCED.name(),
                100.0,
                40.0,
                25.0,
                0.7
        );
        when(configRepository.findById(1L)).thenReturn(Optional.of(baseline));

        StrategyConfig config = strategyService.getConfig();

        assertThat(config.takeProfitPct()).isEqualTo(2.4);
        assertThat(config.stopLossPct()).isEqualTo(1.6);
        assertThat(config.trailingStopPct()).isEqualTo(1.2);
    }

    @Test
    void getMarketOverrides_expandsConfiguredMarketsWithDefaults() {
        when(userSettingsService.findPreferredMarkets(7L)).thenReturn(Optional.of(List.of("KRW-BTC", "KRW-ETH")));
        when(configRepository.findById(1L)).thenReturn(Optional.of(new StrategyConfigEntity(
                1L,
                true,
                50000.0,
                2.4,
                1.28,
                0.9,
                35.0,
                StrategyProfile.BALANCED.name(),
                100.0,
                40.0,
                25.0,
                0.7
        )));
        when(marketOverrideRepository.findAll()).thenReturn(List.of(
                new StrategyMarketOverrideEntity(
                        "KRW-ETH",
                        12000.0,
                        "AGGRESSIVE",
                        true,
                        4.5,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        StrategyMarketOverridesResponse response = strategyService.getMarketOverrides(7L);

        assertThat(response.markets()).containsExactly("KRW-BTC", "KRW-ETH");
        assertThat(response.maxOrderKrwByMarket()).containsEntry("KRW-BTC", 50000.0);
        assertThat(response.maxOrderKrwByMarket()).containsEntry("KRW-ETH", 12000.0);
        assertThat(response.profileByMarket()).containsEntry("KRW-BTC", "BALANCED");
        assertThat(response.profileByMarket()).containsEntry("KRW-ETH", "AGGRESSIVE");
        assertThat(response.tradePausedByMarket()).containsEntry("KRW-BTC", false);
        assertThat(response.tradePausedByMarket()).containsEntry("KRW-ETH", true);
        assertThat(response.ratiosByMarket().get("KRW-ETH").takeProfitPct()).isEqualTo(4.5);
    }

    @Test
    void getMarketOverrides_returnsEmptyWhenUserClearedAllMarkets() {
        when(userSettingsService.findPreferredMarkets(7L)).thenReturn(Optional.of(List.of()));
        when(configRepository.findById(1L)).thenReturn(Optional.of(new StrategyConfigEntity(
                1L,
                true,
                50000.0,
                2.4,
                1.28,
                0.9,
                35.0,
                StrategyProfile.BALANCED.name(),
                100.0,
                40.0,
                25.0,
                0.7
        )));

        StrategyMarketOverridesResponse response = strategyService.getMarketOverrides(7L);

        assertThat(response.markets()).isEmpty();
        assertThat(response.maxOrderKrwByMarket()).isEmpty();
        assertThat(response.profileByMarket()).isEmpty();
        assertThat(response.tradePausedByMarket()).isEmpty();
        assertThat(response.ratiosByMarket()).isEmpty();
    }
}
