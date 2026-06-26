package com.b2b.orders.config;

import com.b2b.orders.application.port.in.ProcessOrderUseCase;
import com.b2b.orders.application.port.out.ClientDirectoryPort;
import com.b2b.orders.application.port.out.OrderRepositoryPort;
import com.b2b.orders.application.port.out.ProductCatalogPort;
import com.b2b.orders.application.service.OrderProcessingService;
import com.b2b.orders.domain.service.TaxCalculationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public TaxCalculationService taxCalculationService(Clock clock) {
        return new TaxCalculationService(clock);
    }

    @Bean
    public ProcessOrderUseCase processOrderUseCase(
            ProductCatalogPort productCatalog,
            ClientDirectoryPort clientDirectory,
            OrderRepositoryPort orderRepository,
            TaxCalculationService taxCalculationService
    ) {
        return new OrderProcessingService(productCatalog, clientDirectory, orderRepository, taxCalculationService);
    }
}
