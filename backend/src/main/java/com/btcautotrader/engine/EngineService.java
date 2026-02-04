package com.btcautotrader.engine;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EngineService {
    private final AtomicBoolean running = new AtomicBoolean(false);

    public boolean start() {
        running.set(true);
        return running.get();
    }

    public boolean stop() {
        running.set(false);
        return running.get();
    }

    public boolean isRunning() {
        return running.get();
    }
}
