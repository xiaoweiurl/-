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
@Table(name = "raw_material_warehouse")
public class RawMaterialWarehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "product_code")
    private String productCode;

    private String color;

    @Column(name = "batch_no")
    private String batchNo;

    private String unit;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;
}
