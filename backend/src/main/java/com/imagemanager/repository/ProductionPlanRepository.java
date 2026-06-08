package com.imagemanager.repository;

import com.imagemanager.entity.ProductionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductionPlanRepository extends JpaRepository<ProductionPlan, Integer>, JpaSpecificationExecutor<ProductionPlan> {
}
