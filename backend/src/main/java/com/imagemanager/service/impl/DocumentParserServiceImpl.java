package com.imagemanager.service.impl;

import com.imagemanager.service.DocumentParserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    @Override
    public String parseDocument(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();

        return switch (ext) {
            case "pdf" -> parsePdf(file);
            case "doc", "docx" -> parseWord(file.getInputStream());
            case "xls", "xlsx" -> parseExcel(file.getInputStream());
            case "txt", "text", "md", "csv" -> parseText(file);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + ext + "，支持: PDF/Word/Excel/TXT/MD/CSV");
        };
    }

    private String parsePdf(MultipartFile file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setSuppressDuplicateOverlappingText(true);
            String text;
            try {
                text = stripper.getText(document);
            } catch (Exception fontError) {
                // 字体相关错误降级处理：尝试逐页提取
                text = extractTextPageByPage(document);
            }
            return text != null ? text : "";
        }
    }

    private String extractTextPageByPage(PDDocument document) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    sb.append(pageText).append("\n");
                }
            } catch (Exception e) {
                // 跳过无法解析的页面
                sb.append("[第").append(i + 1).append("页无法解析]\n");
            }
        }
        return sb.toString();
    }

    private String parseWord(InputStream is) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString();
        }
    }

    private String parseExcel(InputStream is) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(is)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("【工作表: ").append(sheet.getSheetName()).append("】\n");

                // 提取表头
                List<String> headers = new ArrayList<>();
                Row headerRow = sheet.getRow(0);
                if (headerRow != null) {
                    for (Cell cell : headerRow) {
                        headers.add(getCellValue(cell).trim());
                    }
                }

                log.info("Excel解析[v2]: sheet={}, headerCount={}, headers={}, format={}", 
                    sheet.getSheetName(), headers.size(), headers, 
                    (headers.isEmpty() || headers.stream().allMatch(String::isEmpty)) ? "原始|分隔" : "自然语言描述句");

                if (headers.isEmpty() || headers.stream().allMatch(String::isEmpty)) {
                    // 无表头，退化为原始 | 分隔格式
                    log.warn("Excel解析[v2]: 未检测到表头，使用原始|分隔格式, sheet={}", sheet.getSheetName());
                    for (Row row : sheet) {
                        List<String> cells = new ArrayList<>();
                        for (Cell cell : row) {
                            cells.add(getCellValue(cell));
                        }
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                } else {
                    // 有表头：将每行数据转为自然语言描述句
                    // 格式：列名1值1，列名2值2，列名3值3
                    log.info("Excel解析[v2]: 使用自然语言描述句格式, sheet={}", sheet.getSheetName());
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Row row = sheet.getRow(r);
                        if (row == null) continue;

                        List<String> parts = new ArrayList<>();
                        boolean hasData = false;
                        for (int c = 0; c < headers.size(); c++) {
                            String header = headers.get(c);
                            if (header.isEmpty()) continue;
                            String value = getCellValue(row.getCell(c)).trim();
                            if (!value.isEmpty()) {
                                parts.add(header + value);
                                hasData = true;
                            }
                        }
                        if (hasData) {
                            String line = String.join("，", parts);
                            sb.append(line).append("\n");
                            if (r <= 2) {
                                log.info("Excel解析[v2]: 第{}行输出: {}", r, line);
                            }
                        }
                    }
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String parseText(MultipartFile file) throws Exception {
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    @Override
    public List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        // 按段落分割
        String[] paragraphs = text.split("\n+");
        StringBuilder currentChunk = new StringBuilder();

        for (String para : paragraphs) {
            if (para.isBlank()) continue;

            if (currentChunk.length() + para.length() + 1 > chunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                // overlap: 保留最后一段
                String lastPart = currentChunk.toString();
                int lastNewline = lastPart.lastIndexOf("\n");
                if (lastNewline > 0 && lastPart.length() - lastNewline <= overlap) {
                    currentChunk = new StringBuilder(lastPart.substring(lastNewline + 1));
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            currentChunk.append(para).append("\n");
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
