package com.btcautotrader.engine;

import com.btcautotrader.tenant.TenantContext;
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

    public boolean isRunning() {
        return stateRepository.findById(STATE_ID)
                .map(EngineStateEntity::isRunning)
                .orElseGet(this::resolveSystemRunningState);
    }

    private boolean resolveSystemRunningState() {
        String tenantDatabase = TenantContext.getTenantDatabase();
        if (tenantDatabase == null || tenantDatabase.isBlank()) {
            return false;
        }
        return TenantContext.callWithTenantDatabase(null, () -> stateRepository.findById(STATE_ID)
                .map(EngineStateEntity::isRunning)
                .orElse(false));
    }
}
