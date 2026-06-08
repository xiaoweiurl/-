package com.imagemanager.repository;

import com.imagemanager.entity.AccessoryPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessoryPurchaseRepository extends JpaRepository<AccessoryPurchase, Integer>, JpaSpecificationExecutor<AccessoryPurchase> {
}
