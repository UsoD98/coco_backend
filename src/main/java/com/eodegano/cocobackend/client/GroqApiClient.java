package com.eodegano.cocobackend.client;

import com.eodegano.cocobackend.dto.GroqApiRequestDto;
import com.eodegano.cocobackend.dto.GroqApiResponseDto;
import com.eodegano.cocobackend.dto.TourCourseAiResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class GroqApiClient {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "openai/gpt-oss-20b";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long RATE_LIMIT_DELAY_MS = 20_000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GroqApiClient(@Value("${groq.api-key}") String apiKey) {
        this.apiKey = apiKey;

        // Configure timeout settings for Groq API calls
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10 seconds - connection timeout
        factory.setReadTimeout(60000);     // 60 seconds - read timeout (AI processing time)

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();

        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public TourCourseAiResponseDto generateTourCourse(String placesData, String userRequest) {
        String systemPrompt = buildSystemPrompt(placesData);

        GroqApiRequestDto request = GroqApiRequestDto.builder()
                .model(MODEL)
                .messages(buildMessages(systemPrompt, userRequest))
                .temperature(0.7)
                .max_tokens(4000)
                .build();

        GroqApiResponseDto response = callGroqApiWithRetry(request);
        return parseAiResponse(response);
    }

    private String buildSystemPrompt(String placesData) {
        String systemPromptTemplate = loadPromptTemplate("prompts/system-prompt.txt");
        String dailyScheduleTemplate = loadPromptTemplate("prompts/daily-schedule-template.txt");

        return systemPromptTemplate + "\n\n" +
               "AVAILABLE PLACES DATA:\n" + placesData + "\n\n" +
               dailyScheduleTemplate;
    }

    private List<GroqApiRequestDto.Message> buildMessages(String systemPrompt, String userRequest) {
        List<GroqApiRequestDto.Message> messages = new ArrayList<>();

        messages.add(GroqApiRequestDto.Message.builder()
                .role("system")
                .content(systemPrompt)
                .build());

        messages.add(GroqApiRequestDto.Message.builder()
                .role("user")
                .content(userRequest)
                .build());

        return messages;
    }

    private GroqApiResponseDto callGroqApiWithRetry(GroqApiRequestDto request) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("Calling Groq API (attempt {}/{})", attempt, MAX_RETRIES);

                GroqApiResponseDto response = restClient.post()
                        .uri(GROQ_API_URL)
                        .header("Authorization", "Bearer " + apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(GroqApiResponseDto.class);

                if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                    throw new RuntimeException("Empty response from Groq API");
                }

                log.info("Groq API call successful");
                return response;

            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    long waitMs = parseRetryAfterMs(e);
                    log.warn("Groq API rate limit hit (attempt {}/{}). Waiting {}ms before retry.", attempt, MAX_RETRIES, waitMs);
                    if (attempt == MAX_RETRIES) {
                        throw new RuntimeException("Groq API rate limit 초과로 요청에 실패했습니다. 잠시 후 다시 시도해주세요.", e);
                    }
                    sleepQuietly(waitMs);
                } else {
                    log.error("Groq API call failed (attempt {}/{}): HTTP {} - {}", attempt, MAX_RETRIES, e.getStatusCode().value(), e.getMessage());
                    if (attempt == MAX_RETRIES) {
                        throw new RuntimeException("Groq API 호출에 실패했습니다 (최대 재시도 횟수 초과)", e);
                    }
                    sleepQuietly(RETRY_DELAY_MS);
                }

            } catch (Exception e) {
                log.error("Groq API call failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("Groq API 호출에 실패했습니다 (최대 재시도 횟수 초과)", e);
                }
                sleepQuietly(RETRY_DELAY_MS);
            }
        }

        throw new RuntimeException("Groq API 호출에 실패했습니다");
    }

    private long parseRetryAfterMs(HttpClientErrorException e) {
        if (e.getResponseHeaders() != null) {
            String retryAfter = e.getResponseHeaders().getFirst("retry-after");
            if (retryAfter != null) {
                try {
                    return Long.parseLong(retryAfter.trim()) * 1000L;
                } catch (NumberFormatException ignored) {}
            }
        }
        return RATE_LIMIT_DELAY_MS;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("재시도 대기 중 중단되었습니다", ie);
        }
    }

    private TourCourseAiResponseDto parseAiResponse(GroqApiResponseDto response) {
        try {
            String content = response.getChoices().get(0).getMessage().getContent();
            log.debug("AI Response: {}", content);

            // Extract JSON from response (in case there's additional text)
            String jsonContent = extractJson(content);

            return objectMapper.readValue(jsonContent, TourCourseAiResponseDto.class);
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            throw new RuntimeException("AI 응답 파싱에 실패했습니다", e);
        }
    }

    private String extractJson(String content) {
        // Find first '{' and last '}'
        int startIndex = content.indexOf('{');
        int endIndex = content.lastIndexOf('}');

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return content;
        }

        return content.substring(startIndex, endIndex + 1);
    }

    public String loadPromptTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt template: {}", path);
            throw new RuntimeException("프롬프트 템플릿 로드에 실패했습니다: " + path, e);
        }
    }
}
