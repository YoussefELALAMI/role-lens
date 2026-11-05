package com.rolelens.api.job.service;

import com.rolelens.api.job.dto.CvAnalysisResult;
import com.rolelens.api.job.dto.CvUploadResponse;
import com.rolelens.api.job.entity.Cv;
import com.rolelens.api.job.exception.ModelProcessingException;
import com.rolelens.api.job.repository.CvRepository;
import com.rolelens.api.support.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvServiceTest {

    private static final String PDF = "application/pdf";

    @Mock
    private CvRepository repository;

    @Mock
    private CvAnalysisService analysisService;

    @Mock
    private ObjectMapper objectMapper;

    private CvService service;

    @BeforeEach
    void setUp() {
        service = new CvService(repository, analysisService, objectMapper);
        // Behave like a real repository: hand back whatever was saved.
        lenient().when(repository.save(any(Cv.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(repository.findByFileSha256(any())).thenReturn(Optional.empty());
    }

    private static MockMultipartFile pdf(byte[] bytes) {
        return new MockMultipartFile("file", "cv.pdf", PDF, bytes);
    }

    private void assertRejected(MockMultipartFile file, HttpStatus status, String message) {
        assertThatThrownBy(() -> service.uploadCV(file))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(status.value());
                    assertThat(e.getReason()).isEqualTo(message);
                });
        // Nothing may be stored for a rejected upload.
        verify(repository, never()).save(any());
    }

    // ----- validation ------------------------------------------------------

    @Test
    void rejectsAMissingFile() {
        assertThatThrownBy(() -> service.uploadCV(null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(400);
                    assertThat(e.getReason()).isEqualTo("A PDF file is required");
                });
        verifyNoInteractions(repository, analysisService);
    }

    @Test
    void rejectsAnEmptyFile() {
        assertRejected(pdf(new byte[0]), HttpStatus.BAD_REQUEST, "A PDF file is required");
    }

    @Test
    void rejectsAFileOverFiveMegabytes() {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];

        assertRejected(pdf(tooBig), HttpStatus.PAYLOAD_TOO_LARGE, "File must not exceed 5 MB");
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "image/png", "application/octet-stream", "application/json"})
    void rejectsAnyContentTypeThatIsNotPdf(String contentType) {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", contentType, TestPdfs.withLines("Jane"));

        assertRejected(file, HttpStatus.BAD_REQUEST, "Only PDF files are accepted");
    }

    @Test
    void rejectsAMissingContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", null, TestPdfs.withLines("Jane"));

        assertRejected(file, HttpStatus.BAD_REQUEST, "Only PDF files are accepted");
    }

    @Test
    void acceptsThePdfContentTypeInAnyCase() {
        MockMultipartFile file =
                new MockMultipartFile("file", "cv.pdf", "Application/PDF", TestPdfs.withLines("Jane Doe"));

        CvUploadResponse response = service.uploadCV(file);

        assertThat(response.rawText()).contains("Jane Doe");
    }

    @Test
    void rejectsContentThatDoesNotStartWithThePdfSignature() {
        byte[] notAPdf = "this is only text".getBytes(StandardCharsets.UTF_8);

        assertRejected(pdf(notAPdf), HttpStatus.BAD_REQUEST, "Invalid PDF file");
    }

    @Test
    void rejectsContentShorterThanThePdfSignature() {
        assertRejected(pdf(new byte[] {'%', 'P'}), HttpStatus.BAD_REQUEST, "Invalid PDF file");
    }

    @Test
    void rejectsAFileWithAPdfHeaderButBrokenContent() {
        byte[] corrupt = "%PDF-1.4 this is not a real document".getBytes(StandardCharsets.UTF_8);

        assertRejected(pdf(corrupt), HttpStatus.BAD_REQUEST, "Could not parse PDF");
    }

    @Test
    void rejectsAPasswordProtectedPdf() {
        byte[] locked = TestPdfs.passwordProtected("secret", "Jane Doe");

        assertRejected(pdf(locked), HttpStatus.BAD_REQUEST, "Password-protected PDFs are not supported");
    }

    @Test
    void rejectsAPdfWithoutExtractableText() {
        // A scanned CV is an image: this is exactly what an ATS cannot read.
        assertRejected(pdf(TestPdfs.blankPages(1)), HttpStatus.BAD_REQUEST, "PDF contains no extractable text");
    }

    @Test
    void rejectsAPdfWithMoreThanOneHundredPages() {
        byte[] tooLong = TestPdfs.withPages(101, "Jane Doe");

        assertRejected(pdf(tooLong), HttpStatus.BAD_REQUEST, "PDF must contain between 1 and 100 pages");
    }

    @Test
    void acceptsAPdfWithExactlyOneHundredPages() {
        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withPages(100, "Jane Doe")));

        assertThat(response.rawText()).contains("Jane Doe");
    }

    // ----- storing a new CV ------------------------------------------------

    @Test
    void storesTheExtractedTextPageCountAndHash() throws Exception {
        byte[] bytes = TestPdfs.withLines("Jane Doe", "Backend Engineer");

        service.uploadCV(pdf(bytes));

        ArgumentCaptor<Cv> saved = ArgumentCaptor.forClass(Cv.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Cv cv = saved.getAllValues().getFirst();

        assertThat(cv.getFileName()).isEqualTo("cv.pdf");
        assertThat(cv.getPageCount()).isEqualTo((short) 1);
        assertThat(cv.getRawText()).contains("Jane Doe").contains("Backend Engineer");
        assertThat(cv.getFileSha256()).isEqualTo(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void returnsTheExtractedTextEvenWhenAnalysisFails() {
        when(analysisService.analyze(any())).thenThrow(new ModelProcessingException("model is down"));

        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withLines("Jane Doe")));

        assertThat(response.rawText()).contains("Jane Doe");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            C:\\Users\\jane\\Documents\\cv.pdf | cv.pdf
            /home/jane/cv.pdf                  | cv.pdf
            ../../etc/passwd                   | passwd
            my cv.pdf                          | my cv.pdf
            """)
    void keepsOnlyTheFileNameWithoutAnyFolders(String original, String expected) {
        service.uploadCV(new MockMultipartFile("file", original, PDF, TestPdfs.withLines("Jane")));

        ArgumentCaptor<Cv> saved = ArgumentCaptor.forClass(Cv.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().getFileName()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "folder/"})
    void usesADefaultNameWhenTheUploadHasNone(String original) {
        service.uploadCV(new MockMultipartFile("file", original, PDF, TestPdfs.withLines("Jane")));

        ArgumentCaptor<Cv> saved = ArgumentCaptor.forClass(Cv.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().getFirst().getFileName()).isEqualTo("uploaded-cv.pdf");
    }

    // ----- uploading the same file again -----------------------------------

    @Test
    void reusesTheStoredCvForIdenticalBytes() {
        Cv existing = new Cv("old-name.pdf", new byte[32], 1, "stored text from the first upload");
        var uploadedBefore = existing.getUploadedAt();
        when(repository.findByFileSha256(any())).thenReturn(Optional.of(existing));

        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withLines("different text now")));

        // The text is not extracted again: the stored row is the source of truth.
        assertThat(response.rawText()).isEqualTo("stored text from the first upload");
        assertThat(existing.getFileName()).isEqualTo("old-name.pdf");
        assertThat(existing.getUploadedAt()).isAfterOrEqualTo(uploadedBefore);
        verify(analysisService).analyze("stored text from the first upload");
    }

    // ----- analysis outcomes -----------------------------------------------

    @Test
    void returnsTheAnalysisAndStoresTheFindings() {
        JsonNode view = JsonNodeFactory.instance.objectNode().put("sections", 3);
        JsonNode findings = JsonNodeFactory.instance.arrayNode().add("two-column layout");
        JsonNode profile = JsonNodeFactory.instance.objectNode().put("name", "Jane Doe");
        when(analysisService.analyze(any())).thenReturn(new CvAnalysisResult(view, findings, profile));

        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withLines("Jane Doe")));

        assertThat(response.atsView()).isEqualTo(view);
        assertThat(response.atsFindings()).isEqualTo(findings);
        assertThat(response.profile()).isEqualTo(profile);

        ArgumentCaptor<Cv> saved = ArgumentCaptor.forClass(Cv.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getAtsFindings()).isEqualTo(findings);
    }

    @Test
    void usesEmptyFindingsWhenTheAnalysisHasNone() {
        when(analysisService.analyze(any())).thenReturn(new CvAnalysisResult(null, null, null));

        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withLines("Jane Doe")));

        assertThat(response.atsFindings().isArray()).isTrue();
        assertThat(response.atsFindings().size()).isZero();
    }

    @Test
    void fallsBackToTheStoredFindingsWhenTheModelFails() {
        JsonNode previous = JsonNodeFactory.instance.arrayNode().add("from an earlier run");
        Cv existing = new Cv("cv.pdf", new byte[32], 1, "some text");
        existing.updateFindings(previous);
        when(repository.findByFileSha256(any())).thenReturn(Optional.of(existing));
        when(analysisService.analyze(any())).thenThrow(new ModelProcessingException("timeout"));

        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withLines("Jane Doe")));

        assertThat(response.atsView()).isNull();
        assertThat(response.profile()).isNull();
        assertThat(response.atsFindings()).isEqualTo(previous);
        assertThat(response.rawText()).isEqualTo("some text");
    }

    @Test
    void treatsAnEmptyAnalysisResultLikeAModelFailure() {
        // CvAnalysisService is still a stub that returns null.
        when(analysisService.analyze(any())).thenReturn(null);

        CvUploadResponse response = service.uploadCV(pdf(TestPdfs.withLines("Jane Doe")));

        assertThat(response.atsView()).isNull();
        assertThat(response.profile()).isNull();
        assertThat(response.rawText()).contains("Jane Doe");
    }
}
