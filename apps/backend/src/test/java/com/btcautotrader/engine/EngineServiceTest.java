package com.btcautotrader.engine;

import com.btcautotrader.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngineServiceTest {
    @Mock
    private EngineStateRepository stateRepository;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void isRunning_doesNotFallbackToSystemStateWhenTenantStateMissing() {
        EngineService service = new EngineService(stateRepository);
        when(stateRepository.findById(1L)).thenReturn(Optional.empty());

        boolean running = TenantContext.callWithTenantDatabase("btc_user_21", service::isRunning);

        assertThat(running).isFalse();
        verify(stateRepository, times(1)).findById(1L);
    }
}
