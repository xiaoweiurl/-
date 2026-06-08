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
@Table(name = "raw_material_purchase")
public class RawMaterialPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "material_code")
    private String materialCode;

    private String unit;
    private String supplier;

    @Column(name = "batch_no")
    private String batchNo;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;
}
