package com.msa.product.domain.image.command.dto.response;

import com.msa.product.domain.image.entity.ProductImage;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/** 이미지 메타데이터 HTTP 응답입니다. 업로드 URL은 권한 발급 응답에만 포함됩니다. */
@Builder
public record ImageResponse(
        UUID id,
        String objectKey,
        String contentType,
        long sizeBytes,
        int position,
        boolean representative) {
    /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
    public static class ImageResponseBuilder {
        @Override
        public String toString() {
            return "ImageResponseBuilder[REDACTED]";
        }
    }

    public static ImageResponse from(ProductImage i) {
        return ImageResponse.builder()
                .id(i.id)
                .objectKey(i.objectKey)
                .contentType(i.contentType)
                .sizeBytes(i.sizeBytes)
                .position(i.position)
                .representative(i.representative)
                .build();
    }

    @Builder
    public record Upload(
            UUID imageId, String uploadUrl, String contentType, long sizeBytes, Instant expiresAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class UploadBuilder {
            @Override
            public String toString() {
                return "UploadBuilder[REDACTED]";
            }
        }
    }

    @Builder
    public record Download(String downloadUrl, Instant expiresAt) {
        /** 빌더에 담긴 입력값이 로그에 노출되지 않도록 문자열 표현을 제한합니다. */
        public static class DownloadBuilder {
            @Override
            public String toString() {
                return "DownloadBuilder[REDACTED]";
            }
        }
    }
}
