package com.retailpos.pricing.service;

import com.retailpos.domain.Product;
import com.retailpos.domain.ProductCorrelation;
import com.retailpos.domain.ProductCorrelationRepository;
import com.retailpos.domain.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@SuppressWarnings("null")
public class MarketCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(MarketCorrelationService.class);

    private final ProductCorrelationRepository correlationRepository;
    private final ProductRepository productRepository;

    public MarketCorrelationService(ProductCorrelationRepository correlationRepository, ProductRepository productRepository) {
        this.correlationRepository = correlationRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductCorrelation> getCorrelationsForSourceProduct(Long sourceProductId) {
        return correlationRepository.findBySourceProductIdAndEnabledTrue(sourceProductId);
    }

    @Transactional(readOnly = true)
    public List<ProductCorrelation> getAllCorrelations() {
        return correlationRepository.findAll();
    }

    @Transactional
    public ProductCorrelation updateCorrelation(Long sourceProductId, Long targetProductId, BigDecimal coefficient, Boolean enabled) {
        Product source = productRepository.findById(sourceProductId)
                .orElseThrow(() -> new IllegalArgumentException("Source product not found with ID: " + sourceProductId));
        Product target = productRepository.findById(targetProductId)
                .orElseThrow(() -> new IllegalArgumentException("Target product not found with ID: " + targetProductId));

        if (sourceProductId.equals(targetProductId)) {
            throw new IllegalArgumentException("Cannot create self-correlation for product ID: " + sourceProductId);
        }

        ProductCorrelation correlation = correlationRepository
                .findBySourceProductIdAndTargetProductId(sourceProductId, targetProductId)
                .orElseGet(() -> ProductCorrelation.builder()
                        .sourceProduct(source)
                        .targetProduct(target)
                        .build());

        if (coefficient != null) {
            correlation.setCorrelationCoefficient(coefficient);
        }
        if (enabled != null) {
            correlation.setEnabled(enabled);
        }

        ProductCorrelation saved = correlationRepository.save(correlation);
        log.info("🔗 Market Correlation Updated: {} -> {} (coeff={}, enabled={})",
                source.getName(), target.getName(), saved.getCorrelationCoefficient(), saved.getEnabled());
        return saved;
    }

    public BigDecimal calculateSecondaryImpact(BigDecimal directImpact, BigDecimal correlationCoefficient) {
        if (directImpact == null || correlationCoefficient == null || correlationCoefficient.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return directImpact.multiply(correlationCoefficient).setScale(2, RoundingMode.HALF_UP);
    }
}
