package com.docai.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ParsingService {

    public List<RagService.ParsedPage> parse(Path filePath, String filename) throws IOException {
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new IOException("File does not exist: " + filePath);
        }

        List<RagService.ParsedPage> pages = new ArrayList<>();

        if (filename.toLowerCase().endsWith(".pdf")) {
            try (PDDocument pdDoc = Loader.loadPDF(file)) {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = pdDoc.getNumberOfPages();

                for (int page = 1; page <= totalPages; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(pdDoc);
                    if (pageText == null || pageText.trim().isEmpty()) {
                        continue;
                    }
                    pages.add(new RagService.ParsedPage(pageText, page));
                }
            }
        } else {
            String text = Files.readString(filePath, StandardCharsets.UTF_8);
            pages.add(new RagService.ParsedPage(text, 1));
        }

        return pages;
    }
}
