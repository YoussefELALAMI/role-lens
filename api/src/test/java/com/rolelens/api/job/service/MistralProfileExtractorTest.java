package com.rolelens.api.job.service;

import com.rolelens.api.job.dto.CvProfile;
import com.rolelens.api.job.exception.ModelProcessingException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MistralProfileExtractorTest {

    private static final String CV = "Backend developer\nJava, Spring Boot since 2021\nBuilt REST APIs";

    private static final String VALID = """
            {"experience":[{"title":"Backend developer","employer":"Acme","startDate":"2021-03",
             "endDate":null,"description":null}],
             "education":[],
             "skills":[{"name":"Java","quote":"Java, Spring Boot since 2021"}],
             "languages":[{"name":"French","proficiency":"native"}]}""";

    // endDate before startDate: breaks the date-range rule
    private static final String INVALID = """
            {"experience":[{"title":"Dev","employer":null,"startDate":"2022-05",
             "endDate":"2021-01","description":null}],
             "education":[],"skills":[],"languages":[]}""";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private MockRestServiceServer server;

    private MistralProfileExtractor extractor(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://mistral.test");
        server = MockRestServiceServer.bindTo(builder).build();
        return new MistralProfileExtractor(
                builder.build(), mapper, validator, apiKey, "mistral-small-2603", Duration.ZERO);
    }

    private String chat(String contentJson) {
        return "{\"model\":\"mistral-small-2603\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5},"
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"
                + mapper.writeValueAsString(contentJson) + "}}]}";
    }

    @BeforeEach
    void reset() {
        server = null;
    }

    @Test
    void returnsTheProfileAndSendsAStrictJsonSchemaRequest() {
        MistralProfileExtractor extractor = extractor("key");
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer key"))
                .andExpect(jsonPath("$.model").value("mistral-small-2603"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value(containsString("<cv>")))
                .andExpect(content().string(containsString("Spring Boot since 2021")))
                .andRespond(withSuccess(chat(VALID), MediaType.APPLICATION_JSON));

        CvProfile profile = extractor.extract(CV);

        assertThat(profile.experience()).singleElement().satisfies(e -> {
            assertThat(e.startDate()).isEqualTo(YearMonth.of(2021, 3));
            assertThat(e.isCurrent()).isTrue();
        });
        assertThat(profile.skills()).singleElement()
                .satisfies(s -> assertThat(s.quote()).isEqualTo("Java, Spring Boot since 2021"));
        server.verify();
    }

    @Test
    void retriesOnceWithTheViolationsWhenTheOutputIsInvalid() {
        MistralProfileExtractor extractor = extractor("key");
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andRespond(withSuccess(chat(INVALID), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[3].role").value("user"))
                .andExpect(jsonPath("$.messages[3].content")
                        .value(containsString("endDate must not be before startDate")))
                .andRespond(withSuccess(chat(VALID), MediaType.APPLICATION_JSON));

        assertThat(extractor.extract(CV).skills()).hasSize(1);
        server.verify();
    }

    @Test
    void failsAfterOneRetryWhenTheOutputStaysInvalid() {
        MistralProfileExtractor extractor = extractor("key");
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andRespond(withSuccess(chat(INVALID), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andRespond(withSuccess(chat("not json"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> extractor.extract(CV)).isInstanceOf(ModelProcessingException.class);
        server.verify();
    }

    @Test
    void retriesOnceOnAServerError() {
        MistralProfileExtractor extractor = extractor("key");
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andRespond(withSuccess(chat(VALID), MediaType.APPLICATION_JSON));

        assertThat(extractor.extract(CV).experience()).hasSize(1);
        server.verify();
    }

    @Test
    void doesNotRetryOnAnAuthError() {
        MistralProfileExtractor extractor = extractor("key");
        server.expect(requestTo("https://mistral.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> extractor.extract(CV)).isInstanceOf(ModelProcessingException.class);
        server.verify();
    }

    @Test
    void failsWithoutCallingTheModelWhenTheKeyIsMissing() {
        MistralProfileExtractor extractor = extractor("");

        assertThatThrownBy(() -> extractor.extract(CV)).isInstanceOf(ModelProcessingException.class);
        server.verify();
    }
}
