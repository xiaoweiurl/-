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
@Table(name = "accessory_purchase")
public class AccessoryPurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "accessory_name")
    private String accessoryName;

    @Column(name = "accessory_category")
    private String accessoryCategory;

    private String unit;
    private String supplier;

    @Column(name = "accessory_unit_price")
    private BigDecimal accessoryUnitPrice;
}
