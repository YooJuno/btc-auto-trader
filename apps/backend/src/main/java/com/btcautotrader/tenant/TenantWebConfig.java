package com.btcautotrader.tenant;

import com.btcautotrader.auth.CurrentUserService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class TenantWebConfig implements WebMvcConfigurer {
    private final ObjectProvider<TenantContextInterceptor> tenantContextInterceptorProvider;

    public TenantWebConfig(ObjectProvider<TenantContextInterceptor> tenantContextInterceptorProvider) {
        this.tenantContextInterceptorProvider = tenantContextInterceptorProvider;
    }

    @Bean
    @ConditionalOnBean(CurrentUserService.class)
    public TenantContextInterceptor tenantContextInterceptor(
            CurrentUserService currentUserService,
            TenantDatabaseProvisioningService tenantDatabaseProvisioningService
    ) {
        return new TenantContextInterceptor(currentUserService, tenantDatabaseProvisioningService);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        TenantContextInterceptor interceptor = tenantContextInterceptorProvider.getIfAvailable();
        if (interceptor != null) {
            registry.addInterceptor(interceptor)
                    .addPathPatterns("/api/**");
        }
    }
}
