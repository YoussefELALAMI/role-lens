package com.rolelens.api.job.service;

import com.rolelens.api.job.dto.CvProfile;
import com.rolelens.api.job.exception.ModelProcessingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ProfileExtractor on Mistral (ADR-002), through its chat completions API with a JSON schema
 * response format. Output is parsed and validated client-side; one retry, either with the
 * violations fed back (invalid output) or after a short backoff (timeout, 5xx, 429).
 * <p>
 * Logs ids-free technical facts only: attempt, outcome, model id, token counts, latency and
 * violation paths. Never the CV text, the model output or a field value.
 */
@Service
public class MistralProfileExtractor implements ProfileExtractor {

    public static final String PROMPT_VERSION = "cv-profile-v1";

    private static final Logger log = LoggerFactory.getLogger(MistralProfileExtractor.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final int MAX_FEEDBACK_VIOLATIONS = 10;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final String apiKey;
    private final String model;
    private final Duration retryBackoff;
    private final String systemPrompt;
    private final JsonNode schema;

    @Autowired
    public MistralProfileExtractor(
            ObjectMapper objectMapper,
            Validator validator,
            @Value("${mistral.api-key:}") String apiKey,
            @Value("${mistral.base-url:https://api.mistral.ai}") String baseUrl,
            @Value("${mistral.model:mistral-small-2603}") String model,
            @Value("${mistral.timeout:30s}") Duration timeout,
            @Value("${mistral.retry-backoff:1s}") Duration retryBackoff
    ) {
        this(buildClient(baseUrl, timeout), objectMapper, validator, apiKey, model, retryBackoff);
    }

    /** For tests: bring your own RestClient (for example bound to a MockRestServiceServer). */
    MistralProfileExtractor(
            RestClient client,
            ObjectMapper objectMapper,
            Validator validator,
            String apiKey,
            String model,
            Duration retryBackoff
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.apiKey = apiKey;
        this.model = model;
        this.retryBackoff = retryBackoff;
        this.systemPrompt = readResource("prompts/cv-profile.system.txt");
        this.schema = objectMapper.readTree(readResource("prompts/cv-profile.schema.json"));
    }

    @Override
    public String promptVersion() {
        return PROMPT_VERSION;
    }

    @Override
    public CvProfile extract(String cvText) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ModelProcessingException("MISTRAL_API_KEY is not set");
        }
        if (cvText == null || cvText.isBlank()) {
            throw new ModelProcessingException("No CV text to read");
        }

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", "<cv>\n" + cvText + "\n</cv>"));

        ModelProcessingException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long started = System.nanoTime();
            try {
                JsonNode response = call(messages);
                String content = content(response);
                logCall(attempt, "RESPONDED", response, started);

                List<String> problems = new java.util.ArrayList<>();
                CvProfile profile = parseAndValidate(content, problems);
                if (profile != null) {
                    return profile;
                }

                log.warn("CV profile invalid, attempt {}: {}", attempt, problems);
                last = new ModelProcessingException("Model returned an invalid profile");
                // Feed the violations back, not the values.
                messages.add(message("assistant", content));
                messages.add(message("user",
                        "Your answer was invalid. Fix these problems and return the full corrected JSON: "
                                + String.join("; ", problems)));
            } catch (HttpStatusCodeException e) {
                logCall(attempt, "HTTP_" + e.getStatusCode().value(), null, started);
                last = new ModelProcessingException(
                        "Model call failed with status " + e.getStatusCode().value(), e);
                if (!isTransient(e.getStatusCode())) {
                    throw last;
                }
                backoff();
            } catch (ResourceAccessException e) {
                logCall(attempt, "TIMEOUT_OR_IO", null, started);
                last = new ModelProcessingException("Model call timed out or was unreachable", e);
                backoff();
            }
        }
        throw last;
    }

    private JsonNode call(ArrayNode messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.set("messages", messages);

        ObjectNode jsonSchema = objectMapper.createObjectNode();
        jsonSchema.put("name", "cv_profile");
        jsonSchema.put("strict", true);
        jsonSchema.set("schema", schema);
        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchema);
        body.set("response_format", responseFormat);

        JsonNode response = client.post()
                .uri("/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        if (response == null) {
            throw new ModelProcessingException("Model returned an empty response");
        }
        return response;
    }

    private String content(JsonNode response) {
        JsonNode content = response.path("choices").path(0).path("message").path("content");
        if (!content.isString() || content.asString().isBlank()) {
            throw new ModelProcessingException("Model returned no content");
        }
        return content.asString();
    }

    /** Returns the profile when valid; otherwise null, with the problems (paths and rules only). */
    private CvProfile parseAndValidate(String content, List<String> problems) {
        CvProfile profile;
        try {
            profile = objectMapper.readValue(content, CvProfile.class);
        } catch (JacksonException e) {
            problems.add("the output is not valid JSON for the schema ("
                    + e.getClass().getSimpleName() + ")");
            return null;
        }
        Set<ConstraintViolation<CvProfile>> violations = validator.validate(profile);
        if (violations.isEmpty()) {
            return profile;
        }
        violations.stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .limit(MAX_FEEDBACK_VIOLATIONS)
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .forEach(problems::add);
        return null;
    }

    private void logCall(int attempt, String outcome, JsonNode response, long startedNanos) {
        long ms = (System.nanoTime() - startedNanos) / 1_000_000;
        if (response == null) {
            log.info("STRUCTURE_CV attempt={} outcome={} latencyMs={}", attempt, outcome, ms);
            return;
        }
        log.info("STRUCTURE_CV attempt={} outcome={} model={} inputTokens={} outputTokens={} latencyMs={}",
                attempt, outcome, response.path("model").asString(""),
                response.path("usage").path("prompt_tokens").asInt(0),
                response.path("usage").path("completion_tokens").asInt(0), ms);
    }

    private boolean isTransient(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429;
    }

    private void backoff() {
        if (retryBackoff.isZero()) {
            return;
        }
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelProcessingException("Interrupted while waiting to retry", e);
        }
    }

    private ObjectNode message(String role, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", role);
        node.put("content", content);
        return node;
    }

    private static RestClient buildClient(String baseUrl, Duration timeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(timeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private static String readResource(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing resource " + path, e);
        }
    }
}
