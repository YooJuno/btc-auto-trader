package com.btcautotrader.strategy;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class StrategyService {
    private final AtomicReference<StrategyConfig> configRef = new AtomicReference<>(
            new StrategyConfig(true, 10000.0, 3.0, 1.5)
    );

    public StrategyConfig getConfig() {
        return configRef.get();
    }

    public StrategyConfig updateConfig(StrategyConfig config) {
        configRef.set(config);
        return configRef.get();
    }
}
