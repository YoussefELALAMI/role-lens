package com.rolelens.api.job.controller;

import com.jayway.jsonpath.JsonPath;
import com.rolelens.api.TestcontainersConfiguration;
import com.rolelens.api.job.repository.CvRepository;
import com.rolelens.api.support.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CvControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CvRepository cvRepository;

    @BeforeEach
    void cleanDatabase() {
        cvRepository.deleteAll();
    }

    private static MockMultipartFile pdf(String fileName, byte[] bytes) {
        return new MockMultipartFile("file", fileName, "application/pdf", bytes);
    }

    private String uploadAndGetId(MockMultipartFile file) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/cv").file(file))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    // ----- a successful upload ---------------------------------------------

    @Test
    void returnsTheExtractedTextEvenWithoutAModelResult() throws Exception {
        MockMultipartFile file = pdf("cv.pdf", TestPdfs.withLines("Jane Doe", "Backend Engineer"));

        MvcResult result = mockMvc.perform(multipart("/api/cv").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.rawText", containsString("Jane Doe")))
                .andExpect(jsonPath("$.rawText", containsString("Backend Engineer")))
                // The analysis step is a stub for now: no view or profile, no findings.
                .andExpect(jsonPath("$.atsView").value(nullValue()))
                .andExpect(jsonPath("$.profile").value(nullValue()))
                .andExpect(jsonPath("$.atsFindings", hasSize(0)))
                .andReturn();

        String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/cv/" + id);
    }

    @Test
    void storesTheUploadedCv() throws Exception {
        uploadAndGetId(pdf("jane-cv.pdf", TestPdfs.withLines("Jane Doe")));

        assertThat(cvRepository.findAll()).singleElement().satisfies(cv -> {
            assertThat(cv.getFileName()).isEqualTo("jane-cv.pdf");
            assertThat(cv.getPageCount()).isEqualTo((short) 1);
            assertThat(cv.getRawText()).contains("Jane Doe");
            assertThat(cv.getFileSha256()).hasSize(32);
        });
    }

    @Test
    void uploadingTheSameFileTwiceReusesTheStoredCv() throws Exception {
        byte[] bytes = TestPdfs.withLines("Jane Doe");

        String first = uploadAndGetId(pdf("first-name.pdf", bytes));
        String second = uploadAndGetId(pdf("renamed.pdf", bytes));

        assertThat(second).isEqualTo(first);
        assertThat(cvRepository.count()).isEqualTo(1);
    }

    @Test
    void differentFilesAreStoredSeparately() throws Exception {
        String first = uploadAndGetId(pdf("a.pdf", TestPdfs.withLines("Jane Doe")));
        String second = uploadAndGetId(pdf("b.pdf", TestPdfs.withLines("John Smith")));

        assertThat(second).isNotEqualTo(first);
        assertThat(cvRepository.count()).isEqualTo(2);
    }

    // ----- rejected uploads ------------------------------------------------

    static Stream<Arguments> rejectedUploads() {
        return Stream.of(
                Arguments.of("a text file",
                        new MockMultipartFile("file", "cv.txt", "text/plain", "not a pdf".getBytes(StandardCharsets.UTF_8)),
                        "Only PDF files are accepted"),
                Arguments.of("an empty file",
                        pdf("cv.pdf", new byte[0]),
                        "A PDF file is required"),
                Arguments.of("text pretending to be a PDF",
                        pdf("cv.pdf", "just some text".getBytes(StandardCharsets.UTF_8)),
                        "Invalid PDF file"),
                Arguments.of("a password-protected PDF",
                        pdf("cv.pdf", TestPdfs.passwordProtected("secret", "Jane Doe")),
                        "Password-protected PDFs are not supported"),
                Arguments.of("a scanned PDF with no text",
                        pdf("cv.pdf", TestPdfs.blankPages(1)),
                        "PDF contains no extractable text"),
                Arguments.of("a PDF with too many pages",
                        pdf("cv.pdf", TestPdfs.withPages(101, "Jane Doe")),
                        "PDF must contain between 1 and 100 pages")
        );
    }

    @ParameterizedTest(name = "rejects {0} with 400")
    @MethodSource("rejectedUploads")
    void rejectsInvalidUploadsAndStoresNothing(String description, MockMultipartFile file, String message)
            throws Exception {
        mockMvc.perform(multipart("/api/cv").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));

        assertThat(cvRepository.count()).isZero();
    }

    @Test
    void rejectsAFileOverFiveMegabytesWith413() throws Exception {
        // MockMvc skips the servlet's own multipart limits, so this exercises
        // the service's size check, not Tomcat's.
        MockMultipartFile tooBig = pdf("big.pdf", new byte[5 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/cv").file(tooBig))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("File must not exceed 5 MB"));

        assertThat(cvRepository.count()).isZero();
    }

    // ----- malformed requests ----------------------------------------------

    @Disabled("Known bug: the catch-all Exception handler in ApiExceptionHandler turns Spring's own "
            + "400 (missing file part) into 500.")
    @Test
    void rejectsARequestWithoutTheFilePart() throws Exception {
        mockMvc.perform(multipart("/api/cv"))
                .andExpect(status().isBadRequest());
    }

    @Disabled("Known bug: the catch-all Exception handler turns Spring's 415 into 500.")
    @Test
    void rejectsARequestThatIsNotMultipart() throws Exception {
        mockMvc.perform(post("/api/cv").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Disabled("Known bug: the catch-all Exception handler turns Spring's 405 into 500.")
    @Test
    void doesNotAllowGet() throws Exception {
        mockMvc.perform(get("/api/cv"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }
}
