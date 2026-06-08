package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_quotation")
public class ProductQuotation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "production_code")
    private String productionCode;

    @Column(name = "document_no")
    private String documentNo;

    private String period;
    private String customer;
    private String salesperson;

    @Column(name = "product_category")
    private String productCategory;

    @Column(name = "front_quotation_no")
    private String frontQuotationNo;

    @Column(name = "approval_status")
    private String approvalStatus;

    @Column(name = "sales_type")
    private String salesType;

    @Column(name = "raw_material_name1")
    private String rawMaterialName1;

    @Column(name = "material_usage1")
    private BigDecimal materialUsage1;

    @Column(name = "material_unit_price1")
    private BigDecimal materialUnitPrice1;

    @Column(name = "raw_material_name2")
    private String rawMaterialName2;

    @Column(name = "material_usage2")
    private BigDecimal materialUsage2;

    @Column(name = "material_unit_price2")
    private BigDecimal materialUnitPrice2;

    @Column(name = "raw_material_name3")
    private String rawMaterialName3;

    @Column(name = "material_usage3")
    private BigDecimal materialUsage3;

    @Column(name = "material_unit_price3")
    private BigDecimal materialUnitPrice3;

    @Column(name = "raw_material_name4")
    private String rawMaterialName4;

    @Column(name = "material_usage4")
    private BigDecimal materialUsage4;

    @Column(name = "material_unit_price4")
    private BigDecimal materialUnitPrice4;

    @Column(name = "raw_material_name5")
    private String rawMaterialName5;

    @Column(name = "material_usage5")
    private BigDecimal materialUsage5;

    @Column(name = "material_unit_price5")
    private BigDecimal materialUnitPrice5;

    @Column(name = "raw_material_name6")
    private String rawMaterialName6;

    @Column(name = "material_usage6")
    private BigDecimal materialUsage6;

    @Column(name = "material_unit_price6")
    private BigDecimal materialUnitPrice6;

    @Column(name = "accessory_name")
    private String accessoryName;

    @Column(name = "accessory_price")
    private BigDecimal accessoryPrice;
}
