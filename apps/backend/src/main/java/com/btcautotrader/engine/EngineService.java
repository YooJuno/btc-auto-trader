package com.btcautotrader.engine;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EngineService {
    private static final long STATE_ID = 1L;

    private final EngineStateRepository stateRepository;

    public EngineService(EngineStateRepository stateRepository) {
        this.stateRepository = stateRepository;
    }

    @Transactional
    public boolean start() {
        return persistRunningState(true);
    }

    @Transactional
    public boolean stop() {
        return persistRunningState(false);
    }

    private boolean persistRunningState(boolean nextRunning) {
        EngineStateEntity state = stateRepository.findById(STATE_ID)
                .orElseGet(() -> new EngineStateEntity(STATE_ID, nextRunning));
        state.setId(STATE_ID);
        state.setRunning(nextRunning);
        return stateRepository.save(state).isRunning();
    }

    @Transactional(readOnly = true)
    public boolean isRunning() {
        return stateRepository.findById(STATE_ID)
                .map(EngineStateEntity::isRunning)
                .orElse(false);
    }
}
