package com.backend.sparkle.service;

import com.azure.ai.textanalytics.TextAnalyticsClient;
import com.azure.ai.textanalytics.TextAnalyticsClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.backend.sparkle.dto.MessageDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TextAnalyticsService {

    private TextAnalyticsClient textAnalyticsClient; // 텍스트 분석 클라이언트

    private ChatGptService chatGptService; // 한글 => 영어 번역을 위한 chat gpt 서비스

    @Value("${azure.cognitiveservice.endpoint}")
    private String cognitiveAzureEndpoint;

    @Value("${azure.cognitiveservice.key}")
    private String cognitiveApiKey;

    public TextAnalyticsService(ChatGptService chatGptService){
        this.chatGptService = chatGptService;
    }

    @PostConstruct
    public void init() {
        log.info("cognitiveAzureEndpoint: "+ cognitiveAzureEndpoint);
        // 텍스트 분석 클라이언트 생성
        this.textAnalyticsClient = new TextAnalyticsClientBuilder()
                .credential(new AzureKeyCredential(cognitiveApiKey))
                .endpoint(cognitiveAzureEndpoint)
                .buildClient();
        
        log.info("TextAnalyticsClient 생성 완료");
    }

    // Azure의 textAnalytics를 이용하여 핵심 사용자가 입력한 발송 목적 및 내용에서 키워드를 추출하는 메서드
    public List<String> extractKeyPhrases(MessageDto.ImageGenerateRequestDto requestDto) {
        // KeyPhrasesCollection을 반환하며, 이를 List<String>으로 변환
        List<String> keyPhrases = new ArrayList<>();

        textAnalyticsClient.extractKeyPhrases(chatGptService.translateText(requestDto.getInputMessage())).forEach(keyPhrase -> {
            keyPhrases.add(keyPhrase); // 키워드 추가
            log.info("추출된 키워드: {}", keyPhrase); // 키워드 출력
        });


//        textAnalyticsClient.extractKeyPhrases(requestDto.getInputMessage()).forEach(keyPhrase -> {
//            keyPhrasesList.add(keyPhrase); // 키워드 추가
//            log.info("추출된 키워드: {}", keyPhrase); // 키워드 출력
//        });

        log.info("키워드 추출 완료");

        return keyPhrases;
    }
}