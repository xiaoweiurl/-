package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.*;
import com.imagemanager.exception.AuthException;
import com.imagemanager.repository.*;
import com.imagemanager.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/supply-chain")
@Tag(name = "供应链管理", description = "产品报价、原料入库、原料采购、生产计划、辅料采购")
public class SupplyChainController {

    @Autowired private ProductQuotationRepository productQuotationRepository;
    @Autowired private RawMaterialWarehouseRepository rawMaterialWarehouseRepository;
    @Autowired private RawMaterialPurchaseRepository rawMaterialPurchaseRepository;
    @Autowired private ProductionPlanRepository productionPlanRepository;
    @Autowired private AccessoryPurchaseRepository accessoryPurchaseRepository;
    @Autowired private AuthService authService;

    // ====== 认证辅助方法 ======

    private LoginResponse.UserInfo getCurrentUser(HttpServletRequest request) {
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId == null && request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("session_id".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        if (sessionId == null) {
            throw new AuthException("未登录");
        }
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            throw new AuthException("会话已过期");
        }
        return user;
    }

    // ====== 通用分页查询 ======

    private <T> Map<String, Object> pageResult(Page<T> page) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", page.getContent());
        result.put("total", page.getTotalElements());
        result.put("page", page.getNumber() + 1);
        result.put("pageSize", page.getSize());
        result.put("totalPages", page.getTotalPages());
        return result;
    }

    // ====== 产品报价单 ======

    @GetMapping("/quotations")
    @Operation(summary = "查询产品报价单列表")
    public ResponseEntity<?> listQuotations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String customer,
            HttpServletRequest request) {
        getCurrentUser(request);

        Specification<ProductQuotation> spec = Specification.where(null);
        if (keyword != null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("productCode")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("customer")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("salesperson")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("documentNo")), "%" + keyword.toLowerCase() + "%")
            ));
        }
        if (productCode != null && !productCode.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("productCode"), productCode));
        }
        if (customer != null && !customer.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(root.get("customer"), "%" + customer + "%"));
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(pageResult(productQuotationRepository.findAll(spec, pageable)));
    }

    @PostMapping("/quotations")
    @Operation(summary = "创建产品报价单")
    public ResponseEntity<?> createQuotation(@RequestBody ProductQuotation quotation, HttpServletRequest request) {
        getCurrentUser(request);
        return ResponseEntity.ok(productQuotationRepository.save(quotation));
    }

    @PutMapping("/quotations/{id}")
    @Operation(summary = "更新产品报价单")
    public ResponseEntity<?> updateQuotation(@PathVariable Integer id, @RequestBody ProductQuotation quotation, HttpServletRequest request) {
        getCurrentUser(request);
        quotation.setId(id);
        return ResponseEntity.ok(productQuotationRepository.save(quotation));
    }

    @DeleteMapping("/quotations/{id}")
    @Operation(summary = "删除产品报价单")
    public ResponseEntity<?> deleteQuotation(@PathVariable Integer id, HttpServletRequest request) {
        getCurrentUser(request);
        productQuotationRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ====== 原料入库 ======

    @GetMapping("/warehouse")
    @Operation(summary = "查询原料入库列表")
    public ResponseEntity<?> listWarehouse(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        getCurrentUser(request);

        Specification<RawMaterialWarehouse> spec = Specification.where(null);
        if (keyword != null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("productCode")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("batchNo")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("color")), "%" + keyword.toLowerCase() + "%")
            ));
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(pageResult(rawMaterialWarehouseRepository.findAll(spec, pageable)));
    }

    @PostMapping("/warehouse")
    @Operation(summary = "创建原料入库记录")
    public ResponseEntity<?> createWarehouse(@RequestBody RawMaterialWarehouse warehouse, HttpServletRequest request) {
        getCurrentUser(request);
        return ResponseEntity.ok(rawMaterialWarehouseRepository.save(warehouse));
    }

    @PutMapping("/warehouse/{id}")
    @Operation(summary = "更新原料入库记录")
    public ResponseEntity<?> updateWarehouse(@PathVariable Integer id, @RequestBody RawMaterialWarehouse warehouse, HttpServletRequest request) {
        getCurrentUser(request);
        warehouse.setId(id);
        return ResponseEntity.ok(rawMaterialWarehouseRepository.save(warehouse));
    }

    @DeleteMapping("/warehouse/{id}")
    @Operation(summary = "删除原料入库记录")
    public ResponseEntity<?> deleteWarehouse(@PathVariable Integer id, HttpServletRequest request) {
        getCurrentUser(request);
        rawMaterialWarehouseRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ====== 原料采购 ======

    @GetMapping("/purchases")
    @Operation(summary = "查询原料采购列表")
    public ResponseEntity<?> listPurchases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        getCurrentUser(request);

        Specification<RawMaterialPurchase> spec = Specification.where(null);
        if (keyword != null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("materialCode")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("supplier")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("batchNo")), "%" + keyword.toLowerCase() + "%")
            ));
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(pageResult(rawMaterialPurchaseRepository.findAll(spec, pageable)));
    }

    @PostMapping("/purchases")
    @Operation(summary = "创建原料采购记录")
    public ResponseEntity<?> createPurchase(@RequestBody RawMaterialPurchase purchase, HttpServletRequest request) {
        getCurrentUser(request);
        return ResponseEntity.ok(rawMaterialPurchaseRepository.save(purchase));
    }

    @PutMapping("/purchases/{id}")
    @Operation(summary = "更新原料采购记录")
    public ResponseEntity<?> updatePurchase(@PathVariable Integer id, @RequestBody RawMaterialPurchase purchase, HttpServletRequest request) {
        getCurrentUser(request);
        purchase.setId(id);
        return ResponseEntity.ok(rawMaterialPurchaseRepository.save(purchase));
    }

    @DeleteMapping("/purchases/{id}")
    @Operation(summary = "删除原料采购记录")
    public ResponseEntity<?> deletePurchase(@PathVariable Integer id, HttpServletRequest request) {
        getCurrentUser(request);
        rawMaterialPurchaseRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ====== 生产计划 ======

    @GetMapping("/plans")
    @Operation(summary = "查询生产计划列表")
    public ResponseEntity<?> listPlans(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        getCurrentUser(request);

        Specification<ProductionPlan> spec = Specification.where(null);
        if (keyword != null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("semiProductCode")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("productCode")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("machineType")), "%" + keyword.toLowerCase() + "%")
            ));
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(pageResult(productionPlanRepository.findAll(spec, pageable)));
    }

    @PostMapping("/plans")
    @Operation(summary = "创建生产计划")
    public ResponseEntity<?> createPlan(@RequestBody ProductionPlan plan, HttpServletRequest request) {
        getCurrentUser(request);
        return ResponseEntity.ok(productionPlanRepository.save(plan));
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "更新生产计划")
    public ResponseEntity<?> updatePlan(@PathVariable Integer id, @RequestBody ProductionPlan plan, HttpServletRequest request) {
        getCurrentUser(request);
        plan.setId(id);
        return ResponseEntity.ok(productionPlanRepository.save(plan));
    }

    @DeleteMapping("/plans/{id}")
    @Operation(summary = "删除生产计划")
    public ResponseEntity<?> deletePlan(@PathVariable Integer id, HttpServletRequest request) {
        getCurrentUser(request);
        productionPlanRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ====== 辅料采购 ======

    @GetMapping("/accessories")
    @Operation(summary = "查询辅料采购列表")
    public ResponseEntity<?> listAccessories(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        getCurrentUser(request);

        Specification<AccessoryPurchase> spec = Specification.where(null);
        if (keyword != null && !keyword.isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("accessoryName")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("accessoryCategory")), "%" + keyword.toLowerCase() + "%"),
                cb.like(cb.lower(root.get("supplier")), "%" + keyword.toLowerCase() + "%")
            ));
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(pageResult(accessoryPurchaseRepository.findAll(spec, pageable)));
    }

    @PostMapping("/accessories")
    @Operation(summary = "创建辅料采购记录")
    public ResponseEntity<?> createAccessory(@RequestBody AccessoryPurchase accessory, HttpServletRequest request) {
        getCurrentUser(request);
        return ResponseEntity.ok(accessoryPurchaseRepository.save(accessory));
    }

    @PutMapping("/accessories/{id}")
    @Operation(summary = "更新辅料采购记录")
    public ResponseEntity<?> updateAccessory(@PathVariable Integer id, @RequestBody AccessoryPurchase accessory, HttpServletRequest request) {
        getCurrentUser(request);
        accessory.setId(id);
        return ResponseEntity.ok(accessoryPurchaseRepository.save(accessory));
    }

    @DeleteMapping("/accessories/{id}")
    @Operation(summary = "删除辅料采购记录")
    public ResponseEntity<?> deleteAccessory(@PathVariable Integer id, HttpServletRequest request) {
        getCurrentUser(request);
        accessoryPurchaseRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ====== 数据统计概览 ======

    @GetMapping("/stats")
    @Operation(summary = "获取供应链数据统计")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        getCurrentUser(request);
        Map<String, Object> stats = new HashMap<>();
        // 产品数量 = 报价单中不同产品编码数
        long productCount = productQuotationRepository.count();
        stats.put("productCount", productCount);
        // 原料种类 = 原料入库中不同原料编码数
        long materialCount = rawMaterialWarehouseRepository.countDistinctProductCode();
        stats.put("materialCount", materialCount);
        // 供应商数 = 原料采购中不同供应商数 + 辅料采购中不同供应商数
        long supplierCount = rawMaterialPurchaseRepository.countDistinctSupplier() + accessoryPurchaseRepository.countDistinctSupplier();
        stats.put("supplierCount", supplierCount);
        // 平均利润率 = 从智能报价中计算
        try {
            ResponseEntity<?> quoteResult = getSmartQuoteProductList(0.3, 0.15, request);
            Object body = quoteResult.getBody();
            List<Object> quotes = null;
            if (body instanceof Map) {
                Map<String, Object> bodyMap = (Map<String, Object>) body;
                Object productsObj = bodyMap.get("products");
                if (productsObj instanceof List) {
                    quotes = (List<Object>) productsObj;
                }
            }
            if (quotes != null && !quotes.isEmpty()) {
                double totalProfit = 0;
                int count = 0;
                for (Object q : quotes) {
                    if (q instanceof Map) {
                        Map<String, Object> qm = (Map<String, Object>) q;
                        Object pr = qm.get("profitRate");
                        if (pr instanceof Number) {
                            totalProfit += ((Number) pr).doubleValue();
                            count++;
                        }
                    }
                }
                stats.put("avgProfitRate", count > 0 ? Math.round(totalProfit / count * 10.0) / 10.0 : 0);
            } else {
                stats.put("avgProfitRate", 0);
            }
        } catch (Exception e) {
            stats.put("avgProfitRate", 0);
        }
        return ResponseEntity.ok(stats);
    }

    // ====== Excel导入 ======

    @PostMapping("/import")
    @Operation(summary = "导入Excel数据")
    public ResponseEntity<?> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            HttpServletRequest request) {
        getCurrentUser(request);
        try {
            int count = importExcelData(file, type);
            return ResponseEntity.ok(Map.of("success", true, "count", count, "message", "成功导入" + count + "条数据"));
        } catch (Exception e) {
            log.error("导入Excel失败", e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private int importExcelData(MultipartFile file, String type) throws Exception {
        org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(file.getInputStream());
        org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
        int count = 0;
        switch (type) {
            case "quotation":
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                    if (row == null) continue;
                    ProductQuotation q = new ProductQuotation();
                    q.setProductCode(getCellStr(row, 0));
                    q.setProductionCode(getCellStr(row, 1));
                    q.setDocumentNo(getCellStr(row, 2));
                    q.setPeriod(getCellStr(row, 3));
                    q.setCustomer(getCellStr(row, 4));
                    q.setSalesperson(getCellStr(row, 5));
                    q.setProductCategory(getCellStr(row, 6));
                    q.setFrontQuotationNo(getCellStr(row, 7));
                    q.setApprovalStatus(getCellStr(row, 8));
                    q.setSalesType(getCellStr(row, 9));
                    q.setRawMaterialName1(getCellStr(row, 10));
                    q.setMaterialUsage1(getCellDecimal(row, 11));
                    q.setMaterialUnitPrice1(getCellDecimal(row, 12));
                    q.setRawMaterialName2(getCellStr(row, 13));
                    q.setMaterialUsage2(getCellDecimal(row, 14));
                    q.setMaterialUnitPrice2(getCellDecimal(row, 15));
                    q.setRawMaterialName3(getCellStr(row, 16));
                    q.setMaterialUsage3(getCellDecimal(row, 17));
                    q.setMaterialUnitPrice3(getCellDecimal(row, 18));
                    q.setRawMaterialName4(getCellStr(row, 19));
                    q.setMaterialUsage4(getCellDecimal(row, 20));
                    q.setMaterialUnitPrice4(getCellDecimal(row, 21));
                    q.setRawMaterialName5(getCellStr(row, 22));
                    q.setMaterialUsage5(getCellDecimal(row, 23));
                    q.setMaterialUnitPrice5(getCellDecimal(row, 24));
                    q.setRawMaterialName6(getCellStr(row, 25));
                    q.setMaterialUsage6(getCellDecimal(row, 26));
                    q.setMaterialUnitPrice6(getCellDecimal(row, 27));
                    q.setAccessoryName(getCellStr(row, 28));
                    q.setAccessoryPrice(getCellDecimal(row, 29));
                    productQuotationRepository.save(q);
                    count++;
                }
                break;
            case "warehouse":
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                    if (row == null) continue;
                    RawMaterialWarehouse w = new RawMaterialWarehouse();
                    w.setProductCode(getCellStr(row, 0));
                    w.setColor(getCellStr(row, 1));
                    w.setBatchNo(getCellStr(row, 2));
                    w.setUnit(getCellStr(row, 3));
                    w.setUnitPrice(getCellDecimal(row, 4));
                    rawMaterialWarehouseRepository.save(w);
                    count++;
                }
                break;
            case "purchase":
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                    if (row == null) continue;
                    RawMaterialPurchase p = new RawMaterialPurchase();
                    p.setMaterialCode(getCellStr(row, 0));
                    p.setUnit(getCellStr(row, 1));
                    p.setSupplier(getCellStr(row, 2));
                    p.setBatchNo(getCellStr(row, 3));
                    p.setUnitPrice(getCellDecimal(row, 4));
                    rawMaterialPurchaseRepository.save(p);
                    count++;
                }
                break;
            case "plan":
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                    if (row == null) continue;
                    ProductionPlan p = new ProductionPlan();
                    p.setSemiProductCode(getCellStr(row, 0));
                    p.setProductCode(getCellStr(row, 1));
                    p.setSewingWeight(getCellDecimal(row, 2));
                    p.setMachineType(getCellStr(row, 3));
                    p.setNeedleCount(getCellStr(row, 4));
                    p.setSeconds(getCellDecimal(row, 5));
                    p.setMachineCount(getCellInt(row, 6));
                    p.setSingleMachineOutput(getCellDecimal(row, 7));
                    productionPlanRepository.save(p);
                    count++;
                }
                break;
            case "accessory":
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                    if (row == null) continue;
                    AccessoryPurchase a = new AccessoryPurchase();
                    a.setAccessoryName(getCellStr(row, 0));
                    a.setAccessoryCategory(getCellStr(row, 1));
                    a.setUnit(getCellStr(row, 2));
                    a.setSupplier(getCellStr(row, 3));
                    a.setAccessoryUnitPrice(getCellDecimal(row, 4));
                    accessoryPurchaseRepository.save(a);
                    count++;
                }
                break;
            default:
                throw new RuntimeException("不支持的导入类型: " + type);
        }
        wb.close();
        return count;
    }

    private String getCellStr(org.apache.poi.ss.usermodel.Row row, int col) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(col);
        if (cell == null) return null;
        cell.setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        String val = cell.getStringCellValue();
        return (val == null || val.trim().isEmpty()) ? null : val.trim();
    }

    private BigDecimal getCellDecimal(org.apache.poi.ss.usermodel.Row row, int col) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            double d = cell.getNumericCellValue();
            return BigDecimal.valueOf(d);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getCellInt(org.apache.poi.ss.usermodel.Row row, int col) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            return (int) cell.getNumericCellValue();
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== 智能报价 ====================

    @GetMapping("/smart-quote/calculate")
    @Operation(summary = "智能报价计算", description = "根据产品编码和利润率自动计算报价")
    public ResponseEntity<?> calculateSmartQuote(
            @RequestParam String productCode,
            @RequestParam(defaultValue = "30") double profitMargin,
            HttpServletRequest request) {
        try {
            getCurrentUser(request);

            Optional<ProductQuotation> optQ = productQuotationRepository.findByProductCode(productCode);
            if (optQ.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "产品不存在: " + productCode));
            }
            ProductQuotation q = optQ.get();

            // 计算原料成本
            BigDecimal materialCost = BigDecimal.ZERO;
            java.util.List<Map<String, Object>> materialBreakdown = new java.util.ArrayList<>();

            String[] matNames = {q.getRawMaterialName1(), q.getRawMaterialName2(), q.getRawMaterialName3(),
                    q.getRawMaterialName4(), q.getRawMaterialName5(), q.getRawMaterialName6()};
            BigDecimal[] matUsages = {q.getMaterialUsage1(), q.getMaterialUsage2(), q.getMaterialUsage3(),
                    q.getMaterialUsage4(), q.getMaterialUsage5(), q.getMaterialUsage6()};
            BigDecimal[] matPrices = {q.getMaterialUnitPrice1(), q.getMaterialUnitPrice2(), q.getMaterialUnitPrice3(),
                    q.getMaterialUnitPrice4(), q.getMaterialUnitPrice5(), q.getMaterialUnitPrice6()};

            for (int i = 0; i < 6; i++) {
                if (matNames[i] != null && matUsages[i] != null && matPrices[i] != null) {
                    BigDecimal cost = matUsages[i].multiply(matPrices[i]);
                    materialCost = materialCost.add(cost);
                    materialBreakdown.add(Map.of(
                            "name", matNames[i],
                            "usage", matUsages[i],
                            "unitPrice", matPrices[i],
                            "cost", cost
                    ));
                }
            }

            // 辅料成本
            BigDecimal accessoryCost = q.getAccessoryPrice() != null ? q.getAccessoryPrice() : BigDecimal.ZERO;

            // 加工成本（从生产计划获取）
            BigDecimal processingCost = BigDecimal.ZERO;
            Optional<ProductionPlan> optP = productionPlanRepository.findByProductCode(productCode);
            java.util.Map<String, Object> productionInfo = new java.util.HashMap<>();
            if (optP.isPresent()) {
                ProductionPlan p = optP.get();
                // 假设加工费 = 缝头重量(g) × 0.005元/g + 秒数 × 0.001元/秒
                BigDecimal sewingCost = p.getSewingWeight() != null ?
                        p.getSewingWeight().multiply(BigDecimal.valueOf(0.005)) : BigDecimal.ZERO;
                BigDecimal timeCost = p.getSeconds() != null ?
                        p.getSeconds().multiply(BigDecimal.valueOf(0.001)) : BigDecimal.ZERO;
                processingCost = sewingCost.add(timeCost);
                productionInfo.put("semiProductCode", p.getSemiProductCode());
                productionInfo.put("sewingWeight", p.getSewingWeight());
                productionInfo.put("machineType", p.getMachineType());
                productionInfo.put("needleCount", p.getNeedleCount());
                productionInfo.put("seconds", p.getSeconds());
                productionInfo.put("singleMachineOutput", p.getSingleMachineOutput());
            }

            // 总成本
            BigDecimal totalCost = materialCost.add(accessoryCost).add(processingCost);

            // 报价 = 总成本 × (1 + 利润率/100)
            BigDecimal quotePrice = totalCost.multiply(
                    BigDecimal.ONE.add(BigDecimal.valueOf(profitMargin / 100.0)));

            // 查找最优供应商
            java.util.List<Map<String, Object>> supplierSuggestions = new java.util.ArrayList<>();
            for (int i = 0; i < 6; i++) {
                if (matNames[i] != null) {
                    List<RawMaterialPurchase> purchases = rawMaterialPurchaseRepository.findByMaterialCode(matNames[i]);
                    BigDecimal bestPrice = null;
                    String bestSupplier = null;
                    for (RawMaterialPurchase rp : purchases) {
                        if (rp.getUnitPrice() != null && (bestPrice == null || rp.getUnitPrice().compareTo(bestPrice) < 0)) {
                            bestPrice = rp.getUnitPrice();
                            bestSupplier = rp.getSupplier();
                        }
                    }
                    if (bestPrice != null) {
                        BigDecimal currentPrice = matPrices[i] != null ? matPrices[i] : BigDecimal.ZERO;
                        BigDecimal savings = currentPrice.subtract(bestPrice).multiply(matUsages[i] != null ? matUsages[i] : BigDecimal.ONE);
                        supplierSuggestions.add(Map.of(
                                "materialCode", matNames[i],
                                "currentPrice", currentPrice,
                                "bestPrice", bestPrice,
                                "bestSupplier", bestSupplier != null ? bestSupplier : "",
                                "savings", savings
                        ));
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("productCode", productCode);
            result.put("productionCode", q.getProductionCode());
            result.put("customer", q.getCustomer());
            result.put("materialCost", materialCost);
            result.put("accessoryCost", accessoryCost);
            result.put("processingCost", processingCost);
            result.put("totalCost", totalCost);
            result.put("profitMargin", profitMargin);
            result.put("quotePrice", quotePrice);
            result.put("profitPerUnit", quotePrice.subtract(totalCost));
            result.put("materialBreakdown", materialBreakdown);
            result.put("productionInfo", productionInfo);
            result.put("supplierSuggestions", supplierSuggestions);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("智能报价计算失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/smart-quote/product-list")
    @Operation(summary = "获取可报价产品列表及成本概览")
    public ResponseEntity<?> getSmartQuoteProductList(
            @RequestParam(required = false, defaultValue = "0.3") double targetProfitRate,
            @RequestParam(required = false, defaultValue = "0.15") double processingCost,
            HttpServletRequest request) {
        try {
            getCurrentUser(request);
            List<ProductQuotation> quotations = productQuotationRepository.findAll();
            java.util.List<Map<String, Object>> productList = new java.util.ArrayList<>();

            BigDecimal profitRate = BigDecimal.valueOf(targetProfitRate);
            BigDecimal procCost = BigDecimal.valueOf(processingCost);

            for (ProductQuotation q : quotations) {
                BigDecimal materialCost = BigDecimal.ZERO;
                java.util.List<Map<String, Object>> materialDetails = new java.util.ArrayList<>();
                String[] matNames = {q.getRawMaterialName1(), q.getRawMaterialName2(), q.getRawMaterialName3(),
                        q.getRawMaterialName4(), q.getRawMaterialName5(), q.getRawMaterialName6()};
                BigDecimal[] matUsages = {q.getMaterialUsage1(), q.getMaterialUsage2(), q.getMaterialUsage3(),
                        q.getMaterialUsage4(), q.getMaterialUsage5(), q.getMaterialUsage6()};
                for (int i = 0; i < 6; i++) {
                    if (matNames[i] != null && !matNames[i].isEmpty() && matUsages[i] != null) {
                        // 使用报价表自带单价（已经是元/克），同时从采购表获取参考信息
                        BigDecimal[] matPrices = {q.getMaterialUnitPrice1(), q.getMaterialUnitPrice2(), q.getMaterialUnitPrice3(),
                                q.getMaterialUnitPrice4(), q.getMaterialUnitPrice5(), q.getMaterialUnitPrice6()};
                        BigDecimal unitPrice = matPrices[i];
                        
                        // 从采购表获取最低采购价作为参考
                        BigDecimal purchaseRefPrice = rawMaterialPurchaseRepository.findMinPriceByMaterialCode(matNames[i]);
                        String bestSupplier = "";
                        String purchaseUnit = "";
                        if (purchaseRefPrice != null) {
                            java.util.List<String> cheapestSuppliers = rawMaterialPurchaseRepository.findCheapestSupplierByMaterialCode(matNames[i]);
                            if (!cheapestSuppliers.isEmpty()) {
                                bestSupplier = cheapestSuppliers.get(0);
                            }
                            java.util.List<String> cheapestUnits = rawMaterialPurchaseRepository.findCheapestUnitByMaterialCode(matNames[i]);
                            if (!cheapestUnits.isEmpty()) {
                                purchaseUnit = cheapestUnits.get(0);
                            }
                        }
                        
                        // 成本 = 用料量(克) × 单价(元/克)
                        if (unitPrice != null) {
                            BigDecimal cost = matUsages[i].multiply(unitPrice);
                            materialCost = materialCost.add(cost);
                            Map<String, Object> detail = new LinkedHashMap<>();
                            detail.put("name", matNames[i]);
                            detail.put("usage", matUsages[i]);
                            detail.put("unitPrice", unitPrice);
                            detail.put("purchaseRefPrice", purchaseRefPrice);
                            detail.put("purchaseUnit", purchaseUnit);
                            detail.put("cost", cost);
                            detail.put("bestSupplier", bestSupplier);
                            materialDetails.add(detail);
                        }
                    }
                }
                BigDecimal accessoryCost = q.getAccessoryPrice() != null ? q.getAccessoryPrice() : BigDecimal.ZERO;
                String accessoryName = q.getAccessoryName() != null ? q.getAccessoryName() : "";
                // === 完整成本计算 ===
                // 1. 原料成本 = Σ(用料量 × 单价(元/克))
                // 2. 辅料成本
                // 3. 织造成本 = (织时/3600 × 机台日费率) / 日产量
                // 4. 后整理成本 = 缝拼 + 染色 + 定型 + 包装
                // 5. 净成本 = (原料 + 辅料 + 织造 + 后整理) / 正品率

                BigDecimal weavingCostVal = q.getWeavingCost() != null ? q.getWeavingCost() : BigDecimal.ZERO;
                BigDecimal sewingCostVal = q.getSewingCost() != null ? q.getSewingCost() : BigDecimal.ZERO;
                BigDecimal dyeingCostVal = q.getDyeingCost() != null ? q.getDyeingCost() : BigDecimal.ZERO;
                BigDecimal settingCostVal = q.getSettingCost() != null ? q.getSettingCost() : BigDecimal.ZERO;
                BigDecimal packagingCostVal = q.getPackagingCost() != null ? q.getPackagingCost() : BigDecimal.ZERO;
                BigDecimal yieldRate = q.getYieldRate() != null ? q.getYieldRate() : new BigDecimal("100");

                BigDecimal postProcessCost = sewingCostVal.add(dyeingCostVal).add(settingCostVal).add(packagingCostVal);
                BigDecimal manufacturingCost = weavingCostVal.add(postProcessCost);
                // 净成本 = (原料 + 辅料 + 制造) / 正品率
                BigDecimal costBeforeYield = materialCost.add(accessoryCost).add(manufacturingCost);
                BigDecimal netCostVal = yieldRate.compareTo(BigDecimal.ZERO) > 0
                        ? costBeforeYield.multiply(new BigDecimal("100")).divide(yieldRate, 4, /* ROUND_HALF_UP */4)
                        : costBeforeYield;

                BigDecimal totalCost = netCostVal;

                // 建议报价 = 净成本 / (1 - 利润率)
                BigDecimal suggestedPrice = BigDecimal.ONE.subtract(profitRate).compareTo(BigDecimal.ZERO) > 0
                        ? totalCost.divide(BigDecimal.ONE.subtract(profitRate), 4, /* ROUND_HALF_UP */4)
                        : totalCost;
                // 实际利润率 = (建议报价 - 净成本) / 建议报价
                BigDecimal actualProfitRate = suggestedPrice.compareTo(BigDecimal.ZERO) > 0
                        ? suggestedPrice.subtract(totalCost).divide(suggestedPrice, 4, /* ROUND_HALF_UP */4)
                        : BigDecimal.ZERO;

                // 日产能来自报价表
                BigDecimal dailyCapacity = q.getDailyOutput() != null ? new BigDecimal(q.getDailyOutput()) : BigDecimal.ZERO;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", q.getId());
                item.put("productCode", q.getProductCode());
                item.put("productionCode", q.getProductionCode());
                item.put("customer", q.getCustomer());
                item.put("salesperson", q.getSalesperson());
                item.put("approvalStatus", q.getApprovalStatus());
                item.put("salesType", q.getSalesType());
                item.put("materialCost", materialCost);
                item.put("accessoryCost", accessoryCost);
                item.put("accessoryName", accessoryName);
                item.put("weavingCost", weavingCostVal);
                item.put("sewingCost", sewingCostVal);
                item.put("dyeingCost", dyeingCostVal);
                item.put("settingCost", settingCostVal);
                item.put("packagingCost", packagingCostVal);
                item.put("postProcessCost", postProcessCost);
                item.put("manufacturingCost", manufacturingCost);
                item.put("yieldRate", yieldRate);
                item.put("netCost", netCostVal);
                item.put("totalCost", totalCost);
                item.put("suggestedPrice", suggestedPrice);
                item.put("actualProfitRate", actualProfitRate);
                item.put("profitRate", actualProfitRate);
                item.put("dailyCapacity", dailyCapacity);
                item.put("sewingWeight", q.getSewingWeight() != null ? q.getSewingWeight() : "");
                item.put("weavingSeconds", q.getWeavingSeconds());
                item.put("equipmentDailyCost", q.getEquipmentDailyCost());
                item.put("materialDetails", materialDetails);
                productList.add(item);
            }
            return ResponseEntity.ok(Map.of("products", productList));
        } catch (Exception e) {
            log.error("获取产品列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/smart-quote/supplier-comparison")
    @Operation(summary = "供应商价格对比")
    public ResponseEntity<?> getSupplierComparison(
            @RequestParam(required = false) String materialCode,
            HttpServletRequest request) {
        try {
            getCurrentUser(request);
            List<RawMaterialPurchase> purchases;
            if (materialCode != null && !materialCode.isEmpty()) {
                purchases = rawMaterialPurchaseRepository.findByMaterialCode(materialCode);
            } else {
                purchases = rawMaterialPurchaseRepository.findAll();
            }

            // 按原料编码分组
            Map<String, java.util.List<Map<String, Object>>> grouped = new java.util.LinkedHashMap<>();
            for (RawMaterialPurchase p : purchases) {
                String code = p.getMaterialCode();
                if (!grouped.containsKey(code)) {
                    grouped.put(code, new java.util.ArrayList<>());
                }
                grouped.get(code).add(Map.of(
                        "supplier", p.getSupplier() != null ? p.getSupplier() : "",
                        "batchNo", p.getBatchNo() != null ? p.getBatchNo() : "",
                        "unitPrice", p.getUnitPrice() != null ? p.getUnitPrice() : BigDecimal.ZERO,
                        "unit", p.getUnit() != null ? p.getUnit() : ""
                ));
            }
            return ResponseEntity.ok(Map.of("comparison", grouped));
        } catch (Exception e) {
            log.error("供应商对比失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/smart-quote/cost-analysis")
    @Operation(summary = "全产品成本分析")
    public ResponseEntity<?> getCostAnalysis(HttpServletRequest request) {
        try {
            getCurrentUser(request);

            List<ProductQuotation> quotations = productQuotationRepository.findAll();
            java.util.List<Map<String, Object>> analysis = new java.util.ArrayList<>();

            BigDecimal totalMaterialCost = BigDecimal.ZERO;
            BigDecimal totalAccessoryCost = BigDecimal.ZERO;
            BigDecimal totalCost = BigDecimal.ZERO;

            for (ProductQuotation q : quotations) {
                BigDecimal matCost = BigDecimal.ZERO;
                String[] matNames = {q.getRawMaterialName1(), q.getRawMaterialName2(), q.getRawMaterialName3(),
                        q.getRawMaterialName4(), q.getRawMaterialName5(), q.getRawMaterialName6()};
                BigDecimal[] matUsages = {q.getMaterialUsage1(), q.getMaterialUsage2(), q.getMaterialUsage3(),
                        q.getMaterialUsage4(), q.getMaterialUsage5(), q.getMaterialUsage6()};
                BigDecimal[] matPrices = {q.getMaterialUnitPrice1(), q.getMaterialUnitPrice2(), q.getMaterialUnitPrice3(),
                        q.getMaterialUnitPrice4(), q.getMaterialUnitPrice5(), q.getMaterialUnitPrice6()};

                java.util.List<Map<String, Object>> breakdown = new java.util.ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    if (matNames[i] != null && matUsages[i] != null && matPrices[i] != null) {
                        BigDecimal cost = matUsages[i].multiply(matPrices[i]);
                        matCost = matCost.add(cost);
                        breakdown.add(Map.of("name", matNames[i], "cost", cost,
                                "percentage", BigDecimal.ZERO)); // 先填0，后面算比例
                    }
                }

                BigDecimal accCost = q.getAccessoryPrice() != null ? q.getAccessoryPrice() : BigDecimal.ZERO;
                BigDecimal prodCost = BigDecimal.ZERO;

                // 加工成本
                Optional<ProductionPlan> optP = productionPlanRepository.findByProductCode(q.getProductCode());
                if (optP.isPresent()) {
                    ProductionPlan p = optP.get();
                    BigDecimal sewingCost = p.getSewingWeight() != null ?
                            p.getSewingWeight().multiply(BigDecimal.valueOf(0.005)) : BigDecimal.ZERO;
                    BigDecimal timeCost = p.getSeconds() != null ?
                            p.getSeconds().multiply(BigDecimal.valueOf(0.001)) : BigDecimal.ZERO;
                    prodCost = sewingCost.add(timeCost);
                }

                BigDecimal rowTotal = matCost.add(accCost).add(prodCost);

                // 计算各项百分比
                java.util.List<Map<String, Object>> breakdownWithPct = new java.util.ArrayList<>();
                for (Map<String, Object> b : breakdown) {
                    BigDecimal c = (BigDecimal) b.get("cost");
                    BigDecimal pct = rowTotal.compareTo(BigDecimal.ZERO) > 0 ?
                            c.divide(rowTotal, 4, /* ROUND_HALF_UP */4).multiply(BigDecimal.valueOf(100)) :
                            BigDecimal.ZERO;
                    breakdownWithPct.add(Map.of("name", b.get("name"), "cost", c, "percentage", pct));
                }

                totalMaterialCost = totalMaterialCost.add(matCost);
                totalAccessoryCost = totalAccessoryCost.add(accCost);
                totalCost = totalCost.add(rowTotal);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("productCode", q.getProductCode());
                item.put("customer", q.getCustomer());
                item.put("materialCost", matCost);
                item.put("accessoryCost", accCost);
                item.put("processingCost", prodCost);
                item.put("totalCost", rowTotal);
                item.put("breakdown", breakdownWithPct);
                analysis.add(item);
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalMaterialCost", totalMaterialCost);
            summary.put("totalAccessoryCost", totalAccessoryCost);
            summary.put("totalCost", totalCost);
            summary.put("avgCostPerProduct", quotations.isEmpty() ? BigDecimal.ZERO :
                    totalCost.divide(BigDecimal.valueOf(quotations.size()), 4, /* ROUND_HALF_UP */4));
            summary.put("materialCostRatio", totalCost.compareTo(BigDecimal.ZERO) > 0 ?
                    totalMaterialCost.divide(totalCost, 4, /* ROUND_HALF_UP */4).multiply(BigDecimal.valueOf(100)) :
                    BigDecimal.ZERO);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", summary);
            result.put("analysis", analysis);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("成本分析失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
