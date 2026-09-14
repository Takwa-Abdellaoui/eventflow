package com.eventflow.inventoryservice;

import com.eventflow.inventoryservice.entity.Product;
import com.eventflow.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            log.info("Products already initialized — skipping");
            return;
        }

        log.info("Initializing product catalog...");

        productRepository.save(Product.builder()
                .id("PROD-001")
                .name("Laptop Pro 15")
                .stockQuantity(50)
                .price(new BigDecimal("1299.99"))
                .build());

        productRepository.save(Product.builder()
                .id("PROD-002")
                .name("Wireless Headphones")
                .stockQuantity(200)
                .price(new BigDecimal("149.99"))
                .build());

        productRepository.save(Product.builder()
                .id("PROD-003")
                .name("Mechanical Keyboard")
                .stockQuantity(100)
                .price(new BigDecimal("89.99"))
                .build());

        productRepository.save(Product.builder()
                .id("PROD-004")
                .name("4K Monitor")
                .stockQuantity(30)
                .price(new BigDecimal("549.99"))
                .build());

        productRepository.save(Product.builder()
                .id("PROD-005")
                .name("USB-C Hub")
                .stockQuantity(5)  // Stock volontairement bas pour tester les rejets
                .price(new BigDecimal("49.99"))
                .build());

        log.info("✓ {} products initialized", productRepository.count());
    }
}
