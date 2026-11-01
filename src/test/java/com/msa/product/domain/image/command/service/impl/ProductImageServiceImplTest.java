package com.msa.product.domain.image.command.service.impl;

import com.msa.product.domain.image.command.dto.request.*;
import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.entity.Product;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.global.config.storage.ImageStorageProperties;
import com.msa.product.global.error.BusinessException;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 이미지 소유권·실제 파일 서명·불변 키 복사를 검증합니다. 실제 S3 호환성 검증을 대체하지 않습니다. */
class ProductImageServiceImplTest {
    ProductRepository products;
    ProductImageRepository images;
    S3Client s3;
    ProductImageServiceImpl service;
    Product p;
    ProductImage image;
    Instant now = Instant.parse("2030-01-01T00:00:00Z");

    @BeforeEach
    void setup() {
        products = mock(ProductRepository.class);
        images = mock(ProductImageRepository.class);
        s3 = mock(S3Client.class);
        var policy =
                new ImageStorageProperties(
                        true,
                        "test-bucket",
                        "ap-northeast-2",
                        "http://localhost:9090",
                        true,
                        10485760L,
                        List.of("image/jpeg", "image/png", "image/webp"));
        service =
                new ProductImageServiceImpl(
                        products,
                        images,
                        policy,
                        s3,
                        mock(S3Presigner.class),
                        Clock.fixed(now, ZoneOffset.UTC));
        p = new Product(UUID.randomUUID());
        image = new ProductImage(p.id, p.sellerId, "image/png", 16, now.plusSeconds(300));
        when(products.lock(p.id)).thenReturn(Optional.of(p));
        when(images.findById(image.id)).thenReturn(Optional.of(image));
        when(images.findByProductIdAndAttachedTrueOrderByPositionAsc(p.id)).thenReturn(List.of());
    }

    @Test
    void refusesOtherSellerAndOtherProductsImage() {
        assertThatThrownBy(() -> service.complete(UUID.randomUUID(), p.id, image.id))
                .isInstanceOf(BusinessException.class);
        image.productId = UUID.randomUUID();
        assertThatThrownBy(() -> service.complete(p.sellerId, p.id, image.id))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void refusesUnsupportedFormatAndOversizeBeforeIssuingUrl() {
        assertThatThrownBy(
                        () ->
                                service.issue(
                                        p.sellerId,
                                        p.id,
                                        new ImageUploadRequest("image/svg+xml", 100)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                service.issue(
                                        p.sellerId,
                                        p.id,
                                        new ImageUploadRequest("image/png", 10485761)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refusesExpiredUploadAndEleventhImage() {
        image.expiresAt = now;
        assertThatThrownBy(() -> service.complete(p.sellerId, p.id, image.id))
                .isInstanceOf(BusinessException.class);
        image.expiresAt = now.plusSeconds(300);
        when(images.countByProductIdAndAttachedTrue(p.id)).thenReturn(10L);
        assertThatThrownBy(() -> service.complete(p.sellerId, p.id, image.id))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(s3);
    }

    @Test
    void checksSizeAndMimeAndMagicBytes() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(
                        HeadObjectResponse.builder()
                                .contentLength(999L)
                                .contentType("image/png")
                                .build());
        assertThatThrownBy(() -> service.complete(p.sellerId, p.id, image.id))
                .isInstanceOf(BusinessException.class);
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(
                        HeadObjectResponse.builder()
                                .contentLength(16L)
                                .contentType("image/png")
                                .eTag("v1")
                                .build());
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(
                        ResponseBytes.fromByteArray(
                                GetObjectResponse.builder().build(), new byte[16]));
        assertThatThrownBy(() -> service.complete(p.sellerId, p.id, image.id))
                .isInstanceOf(BusinessException.class);
        verify(s3, never()).copyObject(any(CopyObjectRequest.class));
    }

    @Test
    void copiesVerifiedImageToServerOwnedKeyAndCompletionIsIdempotent() {
        when(s3.headObject(any(HeadObjectRequest.class)))
                .thenReturn(
                        HeadObjectResponse.builder()
                                .contentLength(16L)
                                .contentType("image/png")
                                .eTag("v1")
                                .build());
        byte[] png = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0, 0, 0, 0, 0};
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), png));
        var result = service.complete(p.sellerId, p.id, image.id);
        service.complete(p.sellerId, p.id, image.id);
        assertThat(result.objectKey()).startsWith("product-images/");
        assertThat(result.objectKey()).isNotEqualTo(image.uploadKey);
        assertThat(image.attached).isTrue();
        assertThat(result.representative()).isTrue();
        verify(s3, times(1))
                .copyObject(argThat((CopyObjectRequest r) -> "v1".equals(r.copySourceIfMatch())));
    }

    @Test
    void orderCannotIncludeForeignOrDuplicateImages() {
        when(images.findByProductIdAndAttachedTrueOrderByPositionAsc(p.id))
                .thenReturn(List.of(image));
        assertThatThrownBy(
                        () ->
                                service.reorder(
                                        p.sellerId,
                                        p.id,
                                        new ImageOrderRequest(
                                                List.of(image.id, image.id), image.id)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () ->
                                service.reorder(
                                        p.sellerId,
                                        p.id,
                                        new ImageOrderRequest(
                                                List.of(UUID.randomUUID()), image.id)))
                .isInstanceOf(BusinessException.class);
    }
}
