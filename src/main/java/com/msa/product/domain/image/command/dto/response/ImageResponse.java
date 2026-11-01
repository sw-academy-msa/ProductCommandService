package com.msa.product.domain.image.command.dto.response;

import com.msa.product.domain.image.entity.ProductImage;
import java.time.Instant;
import java.util.UUID;

/** 이미지 메타데이터 HTTP 응답입니다. 업로드 URL은 권한 발급 응답에만 포함됩니다. */
public record ImageResponse(
        UUID id,
        String objectKey,
        String contentType,
        long sizeBytes,
        int position,
        boolean representative) {
    public static ImageResponse from(ProductImage i) {
        return new ImageResponse(
                i.id, i.objectKey, i.contentType, i.sizeBytes, i.position, i.representative);
    }

    public record Upload(
            UUID imageId,
            String uploadUrl,
            String contentType,
            long sizeBytes,
            Instant expiresAt) {}

    public record Download(String downloadUrl, Instant expiresAt) {}
}
