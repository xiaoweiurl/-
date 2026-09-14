package com.imagemanager.repository;

import com.imagemanager.entity.ProductQuotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductQuotationRepository extends JpaRepository<ProductQuotation, Integer>, JpaSpecificationExecutor<ProductQuotation> {
    Optional<ProductQuotation> findByProductCode(String productCode);
}
