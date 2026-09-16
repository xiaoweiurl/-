package com.imagemanager.service.impl;

import com.imagemanager.service.DocumentParserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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
            } catch (@SuppressWarnings("unused") Exception fontError) {
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
            } catch (@SuppressWarnings("unused") Exception e) {
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


                if (headers.isEmpty() || headers.stream().allMatch(h -> h.isEmpty())) {
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

        // 1. 预处理：按段落（双换行）分割为段落块
        String[] paragraphs = text.split("\n\\s*\n");
        List<String> normalizedParas = new ArrayList<>();
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;
            // 如果单个段落超过 chunkSize，按句号/换行进一步切分
            if (trimmed.length() > chunkSize) {
                normalizedParas.addAll(splitLongParagraph(trimmed, chunkSize));
            } else {
                normalizedParas.add(trimmed);
            }
        }

        // 2. 滑动窗口组装 chunks
        StringBuilder currentChunk = new StringBuilder();
        for (String para : normalizedParas) {
            if (currentChunk.length() + para.length() + 1 > chunkSize && currentChunk.length() > 0) {
                // 当前 chunk 已满，保存
                chunks.add(currentChunk.toString().trim());

                // overlap: 保留当前 chunk 最后 overlap 个字符的内容
                String chunkStr = currentChunk.toString();
                if (overlap > 0 && chunkStr.length() > overlap) {
                    // 从最后一个换行符开始保留，但不超过 overlap
                    int startIdx = chunkStr.length() - overlap;
                    int newlineIdx = chunkStr.lastIndexOf("\n", startIdx);
                    if (newlineIdx >= 0 && chunkStr.length() - newlineIdx <= overlap * 2) {
                        currentChunk = new StringBuilder(chunkStr.substring(newlineIdx + 1));
                    } else {
                        currentChunk = new StringBuilder(chunkStr.substring(startIdx));
                    }
                } else {
                    currentChunk = new StringBuilder();
                }
            }
            if (currentChunk.length() > 0) {
                currentChunk.append("\n");
            }
            currentChunk.append(para);
        }

        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString().trim();
            if (!lastChunk.isEmpty()) {
                chunks.add(lastChunk);
            }
        }

        return chunks;
    }

    /**
     * 切分超长段落：按句号、换行符进一步切分
     */
    private List<String> splitLongParagraph(String para, int chunkSize) {
        List<String> parts = new ArrayList<>();
        // 按句号、问号、感叹号、换行切分
        String[] sentences = para.split("(?<=[。！？.!?\n])");
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;
            if (current.length() + sentence.length() > chunkSize && current.length() > 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        // 如果句子本身超长，强制按 chunkSize 硬切
        if (parts.isEmpty()) {
            for (int i = 0; i < para.length(); i += chunkSize) {
                parts.add(para.substring(i, Math.min(i + chunkSize, para.length())));
            }
        }
        return parts;
    }
}
