package com.btcautotrader.engine;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EngineService {
    private static final long STATE_ID = 1L;

    private final EngineStateRepository stateRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EngineService(EngineStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @PostConstruct
    void restoreRunningState() {
        EngineStateEntity state = stateRepository.findById(STATE_ID)
                .orElseGet(() -> stateRepository.save(new EngineStateEntity(STATE_ID, false)));
        running.set(state.isRunning());
    }

    @Transactional
    public synchronized boolean start() {
        return persistRunningState(true);
    }

    @Transactional
    public synchronized boolean stop() {
        return persistRunningState(false);
    }

    private boolean persistRunningState(boolean nextRunning) {
        running.set(nextRunning);
        EngineStateEntity state = stateRepository.findById(STATE_ID)
                .orElseGet(() -> new EngineStateEntity(STATE_ID, nextRunning));
        state.setId(STATE_ID);
        state.setRunning(nextRunning);
        stateRepository.save(state);
        return running.get();
    }

    public boolean isRunning() {
        return running.get();
    }
}
