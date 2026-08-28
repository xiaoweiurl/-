package com.imagemanager.repository;

import com.imagemanager.entity.RawMaterialWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RawMaterialWarehouseRepository extends JpaRepository<RawMaterialWarehouse, Integer>, JpaSpecificationExecutor<RawMaterialWarehouse> {

    /**
     * 原料统计报表按物料名称模糊检索（报表无物料编码列，物料名称是主标识）
     */
    List<RawMaterialWarehouse> findByMaterialNameContainingIgnoreCase(String keyword);

    /**
     * 原料种类数：按物料名称去重（报表 Excel「物料名称」列）
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT w.materialName) FROM RawMaterialWarehouse w WHERE w.materialName IS NOT NULL AND w.materialName <> ''")
    long countDistinctMaterialName();

    /**
     * 供应商数：按供应商去重（报表 Excel「供应商」列）
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT w.supplier) FROM RawMaterialWarehouse w WHERE w.supplier IS NOT NULL AND w.supplier <> ''")
    long countDistinctSupplier();

    /**
     * 含价格的报表记录数：unit_price 列非空（报表 Excel 不含单价列，仅手工维护过才有值）
     */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(w) FROM RawMaterialWarehouse w WHERE w.unitPrice IS NOT NULL")
    long countPriced();
}
