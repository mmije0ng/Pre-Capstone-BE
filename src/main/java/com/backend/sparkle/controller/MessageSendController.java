package com.backend.sparkle.controller;

import com.backend.sparkle.dto.CommonResponse;
import com.backend.sparkle.dto.MessageDto;
import com.backend.sparkle.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Tag(name = "문자 보내기 페이지", description = "문자 보내기 및 이미지 생성에 관한 API")
@RestController
@RequestMapping("/api/message")
public class MessageSendController {

    private final ImageService imageService;

    @Autowired
    public MessageSendController(ImageService imageService){
        this.imageService = imageService;
    }

    @Operation(
            summary = "발송 목적 및 내용, 키워드 선택(분위기, 계절감), 키워드 입력 후 이미지 생성",
            description = "사용자가 발송 목적 및 내용, 키워드 선택(분위기, 계절감), 키워드 입력 후 이미지 생성하기 버튼을 클릭하여 4개의 이미지를 생성",
            parameters = {
                    @Parameter(name = "userId", description = "사용자 PK", required = true, example = "1")
            }
    )
    @PostMapping("/generate/{userId}")
    public ResponseEntity<CommonResponse<MessageDto.ImageGenerateResponseDto>> createImages(@PathVariable Long userId, @RequestBody MessageDto.ImageGenerateRequestDto requestDto) {
        log.info("이미지 생성 요청 userId: {}", userId);
        try {
            MessageDto.ImageGenerateResponseDto responseDto = imageService.generateImages(requestDto);
            return ResponseEntity.ok(CommonResponse.success("이미지 생성 성공", responseDto));
        } catch (WebClientResponseException e) {
            log.error("Azure Dalle 이미지 생성 요청 오류: {}", e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body(CommonResponse.fail("Azure Dalle 이미지 생성 요청 오류"));
        }  catch (Exception e) {
            log.error("이미지 생성 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(CommonResponse.fail(e.getMessage()));
        }
    }

    @Operation(
            summary = "템플릿 및 발송화면",
            description = "생성된 이미지 3장 중 사용자가 1장을 선택한 후 템플릿 및 발송화면으로 전환",
            parameters = {
                    @Parameter(name = "userId", description = "사용자 PK", required = true, example = "1"),
                    @Parameter(name = "selectedImageURL", description = "선택된 이미지 URL 경로", required = true, example = "https://i.pinimg.com/564x/f0/e0/9c/f0e09cba73d689fc2c0ef01bbbbeae1a.jpg"),
            }
    )
    @GetMapping("/template")
    public ResponseEntity<CommonResponse<MessageDto.TemplateResponseDto>> getTemplateSendPage(@RequestParam Long userId, @RequestParam String selectedImageURL) {
        try {
            log.info("템플릿 및 발송화면 요청 userId: {}", userId);

            List<String> sendPhoneNumbers = new ArrayList<>();
            sendPhoneNumbers.add("010-0000-0000");
            sendPhoneNumbers.add("010-1234-5678");
            sendPhoneNumbers.add("010-5678-1234");

            List<String> addressNames = new ArrayList<>();
            addressNames.add("한성대 주소록");
            addressNames.add("김선생 수학 학원 주소록");

            MessageDto.TemplateResponseDto responseDto = MessageDto.TemplateResponseDto.builder()
                    .selectedImageURL(selectedImageURL)
                    .sendPhoneNumbers(sendPhoneNumbers)
                    .addressNames(addressNames)
                    .build();

            return ResponseEntity.ok(CommonResponse.success("템플릿 및 발송화면 요청 성공", responseDto));
        } catch (Exception e) {
            log.error("템플릿 및 발송화면 요청 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.fail("템플릿 및 발송화면 요청 실패"));
        }
    }

    @Operation(
            summary = "이미지 + 텍스트 문자 발송",
            description = "템플릿 기능을 통해 완성된 이미지 + 텍스트 문자 전송 요청",
            parameters = {
                    @Parameter(name = "userId", description = "사용자 PK", required = true, example = "1")
            }
    )
    @PostMapping("/send/{userId}")
    public ResponseEntity<CommonResponse<Long>> sendMessage(@PathVariable Long userId, @RequestBody MessageDto.SendRequestDto requestDto) {
        try {
            log.info("이미지 + 텍스트 문자 발송 요청 userId: {}", userId);

            return ResponseEntity.ok(CommonResponse.success("이미지 + 텍스트 문자 발송 요청 성공", userId));
        } catch (Exception e) {
            log.error("이미지 + 텍스트 문자 발송 요청 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.fail("이미지 + 텍스트 문자 발송 요청 실패"));
        }
    }

    @Operation(
            summary = "이미지 + 텍스트 문자 테스트 발송 (미완)",
            description = "템플릿 기능을 통해 완성된 이미지 + 텍스트 문자 테스트 발송 요청",
            parameters = {
                    @Parameter(name = "userId", description = "사용자 PK", required = true, example = "1")
            }
    )
    @PostMapping("/test/{userId}")
    public ResponseEntity<CommonResponse<Long>> sendTestMessage(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(CommonResponse.success("이미지 + 텍스트 문자 테스트 발송 요청 성공", userId));
        } catch (Exception e) {
            log.error("이미지 + 텍스트 문자 테스트 발송 요청 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(CommonResponse.fail("이미지 + 텍스트 문자 테스트 발송 요청 실패"));
        }
    }
}