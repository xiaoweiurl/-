package com.imagemanager.repository;

import com.imagemanager.entity.RawMaterialWarehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface RawMaterialWarehouseRepository extends JpaRepository<RawMaterialWarehouse, Integer>, JpaSpecificationExecutor<RawMaterialWarehouse> {
}
