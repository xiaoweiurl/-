package com.imagemanager.repository;

import com.imagemanager.entity.RawMaterialPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * 原料采购表 Repository【已停用取价职责】
 * ERP 无采购数据，价格基准源已切换 raw_material_warehouse（见 V50 迁移）。
 * 仅保留基础 CRUD 能力，供采购管理页面手工录入/查看历史结构使用。
 * 原按物料编码取最低价/最便宜供应商的方法已删除（无调用方，业务已切换入库表）。
 */
@Repository
public interface RawMaterialPurchaseRepository extends JpaRepository<RawMaterialPurchase, Integer>, JpaSpecificationExecutor<RawMaterialPurchase> {
}
