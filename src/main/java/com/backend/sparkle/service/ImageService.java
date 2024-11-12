package com.backend.sparkle.service;

import com.backend.sparkle.dto.DalleRequestDto;
import com.backend.sparkle.dto.MessageDto;
import com.backend.sparkle.strategy.mood.MoodStrategy;
import com.backend.sparkle.strategy.mood.MoodStrategyFactory;
import com.backend.sparkle.strategy.season.SeasonStrategy;
import com.backend.sparkle.strategy.season.SeasonStrategyFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImageService {

    private final WebClient webClient;
    private final BlobService blobService;
    private final ChatGPTService chatGptService;
    private final TextAnalyticsService textAnalyticsService;

    @Value("${azure.dalle.endpoint}")
    private String dalleAzureEndpoint;

    @Value("${azure.dalle.api-version}")
    private String dalleApiVersion;

    @Value("${azure.dalle.key}")
    private String dalleApiKey;

    private String dalleURI;

    // 이미지 생성에 사용될 다양한 스타일 목록 정의
    private static final List<String> styles = List.of(
            "Minimalist illustration style with pastel tones, featuring a simple and clean composition with soft shading",
            "Highly detailed and realistic photographic style, a high-resolution image with vivid realism and fine detail",
            "Animation style with bright and vibrant colors, presenting characters and background in a simplified form"
    );

    @Autowired
    public ImageService(WebClient.Builder webClientBuilder, BlobService blobService, ChatGPTService chatGptService, TextAnalyticsService textAnalyticsService) {
        this.webClient = webClientBuilder.build();
        this.blobService = blobService;
        this.chatGptService = chatGptService;
        this.textAnalyticsService = textAnalyticsService;
    }

    @PostConstruct
    public void init() {
        this.dalleURI = String.format("%s?api-version=%s", dalleAzureEndpoint, dalleApiVersion);
    }

    // 이미지 생성 요청 메소드
    public MessageDto.ImageGenerateResponseDto generateImages(MessageDto.ImageGenerateRequestDto requestDto) {
        MoodStrategy styleStrategy = MoodStrategyFactory.getMoodStrategy(requestDto.getMood());
        SeasonStrategy seasonStrategy = SeasonStrategyFactory.getSeasonStrategy(requestDto.getSeason());

        String transformedMood = styleStrategy.applyMood();
        String transformedSeason = seasonStrategy.applySeason();

        List<String> keyPhrases = requestDto.getKeyWordMessage().stream()
                .map(keyword -> chatGptService.translateText(keyword, "en"))
                .collect(Collectors.toList());

        keyPhrases.addAll(textAnalyticsService.extractKeyPhrases(requestDto.getInputMessage()));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startTime = LocalDateTime.now();

        List<CompletableFuture<String>> imageFutures = styles.parallelStream()
                .map(style -> retryGenerateImage(style, keyPhrases, requestDto.getInputMessage(), transformedMood, transformedSeason))
                .collect(Collectors.toList());

        CompletableFuture<Void> allOf = CompletableFuture.allOf(imageFutures.toArray(new CompletableFuture[0]));

        return allOf.thenApply(v -> {
            List<String> generatedImageUrls = imageFutures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            LocalDateTime endTime = LocalDateTime.now();
            log.info("Dalle 이미지 생성 요청 완료 시간: {}", endTime.format(formatter));
            Duration duration = Duration.between(startTime, endTime);
            log.info("Dalle 이미지 생성 소요 시간: {} 초", duration.getSeconds());

            return MessageDto.ImageGenerateResponseDto.builder()
                    .generatedImageUrls(generatedImageUrls)
                    .build();
        }).join();
    }

    // 이미지 생성 요청 재시도
    private CompletableFuture<String> retryGenerateImage(String imageStyle, List<String> keyPhrases, String inputMessage, String mood, String season) {
        return CompletableFuture.supplyAsync(() -> {
            int maxRetries = 5;
            int retryCount = 0;
            while (retryCount < maxRetries) {
                try {
                    return generateImageWithDalle(imageStyle, keyPhrases, inputMessage, mood, season).join();
                } catch (Exception e) {
                    retryCount++;
                    log.warn(e.getMessage());
                    log.warn("{}초 후 재시도 ({}/{})", retryCount * 5, retryCount, maxRetries);
                    try {
                        TimeUnit.SECONDS.sleep(retryCount * 5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            throw new RuntimeException("Dalle API 요청이 여러 번 실패했습니다.");
        });
    }

    // DALL-E API에 이미지 생성 요청
    private CompletableFuture<String> generateImageWithDalle(String imageStyle, List<String> keyPhrases, String inputMessage, String mood, String season) {
        String prompt = generatePrompt(imageStyle, keyPhrases, inputMessage, mood, season);
        DalleRequestDto dalleRequestDto = DalleRequestDto.builder()
                .prompt(prompt)
                .size("1024x1792")
                .n(1)
                .quality("standard")
                .style("vivid")
                .build();

        log.info("Dalle 이미지 생성 요청, prompt: {}", prompt);

        return CompletableFuture.supplyAsync(() -> {
            try {
                String responseBody = webClient.post()
                        .uri(dalleURI)
                        .header("api-key", dalleApiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(dalleRequestDto)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                if (responseBody == null || responseBody.isEmpty()) {
                    throw new RuntimeException("Dalle API로부터 응답이 null이거나 비어 있습니다.");
                }

                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray dataArray = jsonResponse.getJSONArray("data");
                String url = dataArray.getJSONObject(0).getString("url");
                log.info("Dalle 생성 이미지 url: {}", url);
                log.info("Dalle revised_prompt: {}", dataArray.getJSONObject(0).getString("revised_prompt"));

                return blobService.uploadImageByUrl(url);
            } catch (JSONException e) {
                log.error("JSON 파싱 중 오류 발생: {}", e.getMessage());
                throw new RuntimeException("JSON 파싱 오류", e);
            }
        });
    }

    private String generatePrompt(String imageStyle, List<String> keyPhrases, String inputMessage, String mood, String season) {
        return String.format(
                "Please create an image in a %s style. The image should emphasize a clean and minimalist design and layout. " +
                        "Text and human figures must not be included, and absolutely no letters or characters should appear in the image. " +
                        "Description: %s. " +
                        "Visually express the following keywords: %s. Set the mood to %s and reflect this atmosphere in the image. " +
                        "The background should be based on a %s seasonal theme, kept simple in a solid color. Exclude any complex background elements. " +
                        "The background should not contain any elements other than the objects described and the keywords.",
                imageStyle,
                chatGptService.translateText(inputMessage, "en"),
                String.join(", ", keyPhrases),
                mood,
                season
        );
    }
}
