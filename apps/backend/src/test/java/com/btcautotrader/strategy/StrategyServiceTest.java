package com.btcautotrader.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StrategyServiceTest {

    @Mock
    private StrategyConfigRepository configRepository;

    @Mock
    private StrategyMarketRepository marketRepository;

    @Mock
    private StrategyMarketOverrideRepository marketOverrideRepository;

    @Mock
    private StrategyPresetRepository presetRepository;

    private StrategyService strategyService;

    @BeforeEach
    void setUp() {
        strategyService = new StrategyService(
                configRepository,
                marketRepository,
                marketOverrideRepository,
                presetRepository,
                "KRW-BTC"
        );
    }

    @Test
    void replaceMarkets_usesDeleteAllInsteadOfBatchDelete() {
        when(marketRepository.findAllByOrderByMarketAsc()).thenReturn(
                List.of(
                        new StrategyMarketEntity("KRW-AXS"),
                        new StrategyMarketEntity("KRW-BTC"),
                        new StrategyMarketEntity("KRW-ETH")
                )
        );
        when(marketOverrideRepository.findAll()).thenReturn(List.of());

        strategyService.replaceMarkets(List.of("KRW-BTC", "KRW-ETH", "KRW-ADA"));

        verify(marketRepository).deleteAll();
        verify(marketRepository, never()).deleteAllInBatch();
        verify(marketRepository).saveAll(anyList());
    }

    @Test
    void getConfig_usesBalancedAnd30000AsDefault() {
        when(configRepository.findById(1L)).thenReturn(Optional.empty());
        when(configRepository.save(any(StrategyConfigEntity.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        StrategyConfig config = strategyService.getConfig();

        assertThat(config.maxOrderKrw()).isEqualTo(30000.0);
        assertThat(config.profile()).isEqualTo("BALANCED");
    }
}
