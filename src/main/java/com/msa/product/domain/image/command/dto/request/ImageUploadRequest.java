package com.msa.product.domain.image.command.dto.request;

import jakarta.validation.constraints.*;

/** 사용자 파일명으로 객체 키를 만들지 않습니다. 서버 설정의 형식·용량과도 대조합니다. */
public record ImageUploadRequest(@NotBlank String contentType, @Positive long sizeBytes) {}
