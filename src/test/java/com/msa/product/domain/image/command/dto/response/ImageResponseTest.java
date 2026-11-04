package com.msa.product.domain.image.command.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 빌더의 기본 문자열 표현이 새롭게 서명 URL을 노출하지 않도록 검증합니다. */
class ImageResponseTest {
    @Test
    void builderToStringDoesNotExposePresignedUrl() {
        var upload =
                ImageResponse.Upload.builder()
                        .uploadUrl("https://example.invalid/upload?signature=fixture");
        var download =
                ImageResponse.Download.builder()
                        .downloadUrl("https://example.invalid/download?signature=fixture");

        assertThat(upload.toString()).isEqualTo("UploadBuilder[REDACTED]");
        assertThat(download.toString()).isEqualTo("DownloadBuilder[REDACTED]");
    }
}
