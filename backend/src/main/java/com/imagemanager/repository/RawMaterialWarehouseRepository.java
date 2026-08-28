package com.imagemanager.repository;

import com.imagemanager.entity.RawMaterialWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawMaterialWarehouseRepository extends JpaRepository<RawMaterialWarehouse, Integer>, JpaSpecificationExecutor<RawMaterialWarehouse> {
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT w.productCode) FROM RawMaterialWarehouse w")
    long countDistinctProductCode();

    /**
     * 按原料编码查全部入库记录（ERP 无采购数据后，价格基准源=入库表）
     */
    List<RawMaterialWarehouse> findByProductCode(String productCode);

    /**
     * 入库供应商数（非空去重）
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT w.supplier) FROM RawMaterialWarehouse w WHERE w.supplier IS NOT NULL AND w.supplier <> ''")
    long countDistinctSupplier();
}
