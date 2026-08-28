package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 原料统计报表/用料BOM实体（Excel 导入 14 列：货号/颜色/尺码/部件/供应商/物料名称/规格/物料颜色/批号/捻向/单位/单件用量/损耗/备注）
 * huohao=成品货号【关联键】→ order_bjd_query.huohao / order_xs_list.detailhuohao
 * 注意：Excel 报表不含「单价」「物料编码」列！unit_price / product_code 是系统预留的手工维护列，默认为空；
 * 物料唯一标识 = 物料名称+规格（见 V51 视图 v_material_price）
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

    /** 原料编码（手工维护预留列：Excel 报表不含此列，默认空；勿关联采购表——已停用） */
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

    /** 单价（手工维护预留列：Excel 报表不含此列，默认空；有值时参与 v_material_price 比价） */
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    /** 备注 */
    private String remark;

    /** 所属公司（数据隔离） */
    private String company;
}
