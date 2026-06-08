package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.*;
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
            throw new RuntimeException("未登录");
        }
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            throw new RuntimeException("会话已过期");
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
        stats.put("quotationCount", productQuotationRepository.count());
        stats.put("warehouseCount", rawMaterialWarehouseRepository.count());
        stats.put("purchaseCount", rawMaterialPurchaseRepository.count());
        stats.put("planCount", productionPlanRepository.count());
        stats.put("accessoryCount", accessoryPurchaseRepository.count());
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

    private java.math.BigDecimal getCellDecimal(org.apache.poi.ss.usermodel.Row row, int col) {
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            double d = cell.getNumericCellValue();
            return java.math.BigDecimal.valueOf(d);
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
}
