package com.imagemanager.service.impl;

import com.imagemanager.service.DocumentParserService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
            return stripper.getText(document);
        }
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
        try (Workbook workbook = new XSSFWorkbook(is)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                sb.append("【工作表: ").append(sheet.getSheetName()).append("】\n");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(getCellValue(cell));
                    }
                    sb.append(String.join(" | ", cells)).append("\n");
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
