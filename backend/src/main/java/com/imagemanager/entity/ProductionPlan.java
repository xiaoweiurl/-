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
@Table(name = "production_plan")
public class ProductionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "semi_product_code")
    private String semiProductCode;

    @Column(name = "product_code")
    private String productCode;

    @Column(name = "sewing_weight")
    private BigDecimal sewingWeight;

    @Column(name = "machine_type")
    private String machineType;

    @Column(name = "needle_count")
    private String needleCount;

    private BigDecimal seconds;

    @Column(name = "machine_count")
    private Integer machineCount;

    @Column(name = "single_machine_output")
    private BigDecimal singleMachineOutput;
}
