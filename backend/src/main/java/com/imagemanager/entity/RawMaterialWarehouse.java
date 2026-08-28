package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 原料入库/用料BOM实体（价格基准源）
 * 对应 V49 表结构：货号/颜色/尺码/部件/供应商/物料名称/规格/物料颜色/批号/捻向/单位/单件用量/损耗/备注
 * huohao=成品货号【关联键】→ order_bjd_query.huohao / order_xs_list.detailhuohao
 * product_code=原料编码【关联键】→ product_quotation.raw_material_name1~6
 * ERP 无采购数据（raw_material_purchase 已停用），本表 unit_price 即智能报价/供应商对比的取价基准（见 V50 迁移）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "raw_material_warehouse")
public class RawMaterialWarehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 货号（成品货号，关联报价单/销售订单/工艺单） */
    @Column(name = "huohao")
    private String huohao;

    /** 原料编码（关联原料采购表 material_code） */
    @Column(name = "product_code")
    private String productCode;

    /** 颜色 */
    private String color;

    /** 尺码 */
    private String size;

    /** 部件 */
    private String component;

    /** 供应商 */
    private String supplier;

    /** 物料名称 */
    @Column(name = "material_name")
    private String materialName;

    /** 规格 */
    private String specification;

    /** 物料颜色 */
    @Column(name = "material_color")
    private String materialColor;

    /** 批号 */
    @Column(name = "batch_no")
    private String batchNo;

    /** 捻向 */
    @Column(name = "twist_direction")
    private String twistDirection;

    /** 单位 */
    private String unit;

    /** 单件用量 */
    @Column(name = "usage_per_unit")
    private BigDecimal usagePerUnit;

    /** 损耗(%) */
    @Column(name = "loss_rate")
    private BigDecimal lossRate;

    /** 入库单价 */
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    /** 备注 */
    private String remark;

    /** 所属公司（数据隔离） */
    private String company;
}
