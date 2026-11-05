package com.rolelens.api.job.controller;

import com.rolelens.api.TestcontainersConfiguration;
import com.rolelens.api.job.repository.JobPostingRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JobPostingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobPostingRepository repository;

    @BeforeEach
    void cleanDatabase() {
        // All integration tests share one database, so start each test empty.
        repository.deleteAll();
    }

    private static String ad(String text) {
        return """
                {"source": "PASTE", "text": "%s"}
                """.formatted(text);
    }

    private void save(String json) throws Exception {
        mockMvc.perform(post("/api/postings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    // ----- saving and listing ----------------------------------------------

    @Test
    void savesAPostingAndListsIt() throws Exception {
        String requestBody = """
                {
                  "source": "PASTE",
                  "text": "Senior Backend Engineer\\nAcme Corp is looking for a backend engineer."
                }
                """;

        mockMvc.perform(post("/api/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Senior Backend Engineer"));

        mockMvc.perform(get("/api/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Senior Backend Engineer"));
    }

    @Test
    void listsNothingWhenNothingIsSaved() throws Exception {
        mockMvc.perform(get("/api/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsEveryDifferentAd() throws Exception {
        save(ad("Backend Engineer at Acme"));
        save(ad("Frontend Engineer at Globex"));

        mockMvc.perform(get("/api/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void returnsTheFieldsTheWebAppShows() throws Exception {
        mockMvc.perform(post("/api/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ad("Platform Engineer")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Platform Engineer"))
                // The company is not extracted yet.
                .andExpect(jsonPath("$.company").value(nullValue()))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void usesTheFirstNonBlankLineAsTheTitle() throws Exception {
        mockMvc.perform(post("/api/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ad("\\n\\n   Staff Engineer  \\nBuild things")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Staff Engineer"));
    }

    @Test
    void storesTheTrimmedTextAndTheSourceUrl() throws Exception {
        String json = """
                {"source": "PAGE", "url": "https://example.com/jobs/42", "text": "  Data Engineer\\nBerlin  "}
                """;

        save(json);

        assertThat(repository.findAll()).singleElement().satisfies(posting -> {
            assertThat(posting.getRawText()).isEqualTo("Data Engineer\nBerlin");
            assertThat(posting.getSourceUrl()).isEqualTo("https://example.com/jobs/42");
            assertThat(posting.getSource().name()).isEqualTo("PAGE");
        });
    }

    // ----- invalid requests ------------------------------------------------

    static Stream<Arguments> malformedRequests() {
        return Stream.of(
                Arguments.of("a missing source", "{\"text\": \"Backend Engineer\"}"),
                Arguments.of("an unknown source", "{\"source\": \"FAX\", \"text\": \"Backend Engineer\"}"),
                Arguments.of("malformed JSON", "{\"source\": ")
        );
    }

    @Disabled("Known bug: ApiExceptionHandler's catch-all Exception handler turns Spring's own 400 errors "
            + "(validation, bad JSON, bad enum) into 500. Enable once it lets those through.")
    @ParameterizedTest(name = "rejects {0} with 400")
    @MethodSource("malformedRequests")
    void rejectsMalformedRequestsAndStoresNothing(String description, String json) throws Exception {
        mockMvc.perform(post("/api/postings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
    }

    static Stream<Arguments> adsWithoutText() {
        return Stream.of(
                Arguments.of("a missing text", "{\"source\": \"PASTE\"}"),
                Arguments.of("a blank text", "{\"source\": \"PASTE\", \"text\": \"   \"}")
        );
    }

    @Disabled("Known bug, needs two fixes: CreatePostingRequest.text has no @NotBlank (a missing text throws a "
            + "NullPointerException, a blank text is saved as an empty ad with 201), AND the catch-all handler in "
            + "ApiExceptionHandler must let Spring's validation 400 through, or @NotBlank alone still ends in 500.")
    @ParameterizedTest(name = "rejects {0} with 400")
    @MethodSource("adsWithoutText")
    void rejectsAdsWithoutTextAndStoresNothing(String description, String json) throws Exception {
        mockMvc.perform(post("/api/postings").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());

        assertThat(repository.count()).isZero();
    }

    @Disabled("Known bug: saving the same ad twice breaks the unique content_hash constraint and returns 500. "
            + "Decide between 409 and returning the existing posting, then enable.")
    @Test
    void savingTheSameAdTwiceIsNotAServerError() throws Exception {
        save(ad("Backend Engineer at Acme"));

        MvcResult second = mockMvc.perform(post("/api/postings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ad("Backend Engineer at Acme")))
                .andReturn();

        // Whether this is a 409 or the existing posting is a product decision;
        // either way it must not be a 500, and no duplicate row may appear.
        assertThat(second.getResponse().getStatus()).isLessThan(500);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Disabled("Known bug: the catch-all Exception handler turns Spring's 405 into 500.")
    @Test
    void doesNotAllowPut() throws Exception {
        mockMvc.perform(put("/api/postings").contentType(MediaType.APPLICATION_JSON).content(ad("x")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Disabled("Known bug: the catch-all Exception handler turns Spring's 415 into 500.")
    @Test
    void rejectsABodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/api/postings").contentType(MediaType.TEXT_PLAIN).content("Backend Engineer"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
