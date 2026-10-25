package com.rolelens.api.job.service;


import com.rolelens.api.job.dto.CvAnalysisResult;
import com.rolelens.api.job.dto.CvUploadResponse;
import com.rolelens.api.job.entity.Cv;
import com.rolelens.api.job.exception.ModelProcessingException;
import com.rolelens.api.job.repository.CvRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class CvService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_PAGES = 100;

    private final CvRepository cvRepository;
    private final CvAnalysisService analysisService;
    private final ObjectMapper objectMapper;

    public CvService(
            CvRepository cvRepository,
            CvAnalysisService analysisService,
            ObjectMapper objectMapper
    ) {
        this.cvRepository = cvRepository;
        this.analysisService = analysisService;
        this.objectMapper = objectMapper;
    }

    public CvUploadResponse uploadCV(MultipartFile file) {
        validateFile(file);

        byte[] pdfBytes;

        try {
            pdfBytes = file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Could not read uploaded file"
            );
        }

        validatePdfSignature(pdfBytes);

        byte[] hash = calculateSha256(pdfBytes);

        Cv cv = cvRepository.findByFileSha256(hash).orElse(null);

        if (cv == null) {
            ExtractedCv extracted = extractText(pdfBytes);

            cv = new Cv(
                    safeFileName(file.getOriginalFilename()),
                    hash,
                    extracted.pageCount(),
                    extracted.rawText()
            );

            cv = cvRepository.save(cv);
        } else {
            // Same PDF: reuse its existing row and text.
            cv.touchUploadedAt();
            cv = cvRepository.save(cv);
        }

        // Always retry analysis on upload, including repeated uploads.
        try {
            CvAnalysisResult result =
                    analysisService.analyze(cv.getRawText());

            if (result == null) {
                throw new ModelProcessingException(
                        "Model returned no result");
            }

            JsonNode findings = result.atsFindings() != null
                    ? result.atsFindings()
                    : JsonNodeFactory.instance.arrayNode();

            cv.updateFindings(findings);
            cvRepository.save(cv);

            return new CvUploadResponse(
                    cv.getId(),
                    result.atsView(),
                    findings,
                    result.profile()
            );

        } catch (ModelProcessingException e) {
            // Keep existing findings if this retry fails.
            JsonNode previousFindings = cv.getAtsFindings();

            return new CvUploadResponse(
                    cv.getId(),
                    null,
                    previousFindings,
                    null
            );
        }
    }


    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A PDF file is required");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "File must not exceed 5 MB");
        }

        if (!"application/pdf".equalsIgnoreCase(
                file.getContentType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only PDF files are accepted");
        }
    }

    private void validatePdfSignature(byte[] bytes) {
        byte[] signature = {'%', 'P', 'D', 'F', '-'};

        if (bytes.length < signature.length) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid PDF file");
        }

        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid PDF file");
            }
        }
    }

    private byte[] calculateSha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", e);
        }
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "uploaded-cv.pdf";
        }

        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(
                normalized.lastIndexOf('/') + 1);

        return name.isBlank() ? "uploaded-cv.pdf" : name;
    }

    private ExtractedCv extractText(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();

            if (pageCount < 1 || pageCount > MAX_PAGES) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PDF must contain between 1 and "
                                + MAX_PAGES + " pages");
            }

            String rawText =
                    new PDFTextStripper().getText(document);

            if (rawText == null || rawText.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PDF contains no extractable text");
            }

            return new ExtractedCv(rawText, pageCount);

        } catch (org.apache.pdfbox.pdmodel.encryption
                         .InvalidPasswordException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password-protected PDFs are not supported");
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Could not parse PDF");
        }
    }

    private record ExtractedCv(String rawText, int pageCount) {}
}
