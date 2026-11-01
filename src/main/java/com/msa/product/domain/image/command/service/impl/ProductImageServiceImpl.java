package com.msa.product.domain.image.command.service.impl;

import com.msa.product.domain.image.command.dto.request.*;
import com.msa.product.domain.image.command.dto.response.ImageResponse;
import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.domain.image.entity.ProductImage;
import com.msa.product.domain.image.repository.ProductImageRepository;
import com.msa.product.domain.product.ProductError;
import com.msa.product.domain.product.repository.ProductRepository;
import com.msa.product.global.config.storage.ImageStorageProperties;
import com.msa.product.global.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import java.time.*;
import java.util.*;

/** 이미지 연결의 DB 원자성과 S3 검증을 담당합니다. S3는 DB 롤백 대상이 아니므로 고아 객체는 내부 작업으로 정리합니다. */
@Service
@Transactional
public class ProductImageServiceImpl implements ProductImageService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final ImageStorageProperties policy;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final Clock clock;

    public ProductImageServiceImpl(
            ProductRepository products,
            ProductImageRepository images,
            ImageStorageProperties policy,
            S3Client s3,
            S3Presigner presigner,
            Clock clock) {
        this.products = products;
        this.images = images;
        this.policy = policy;
        this.s3 = s3;
        this.presigner = presigner;
        this.clock = clock;
    }

    public ImageResponse.Upload issue(UUID seller, UUID product, ImageUploadRequest request) {
        editable(seller, product);
        if (!policy.ready()) {
            throw new BusinessException(ProductError.POLICY);
        }
        if (!policy.allowedTypes().contains(request.contentType())
                || !Set.of("image/jpeg", "image/png", "image/webp").contains(request.contentType())
                || request.sizeBytes() <= 0
                || request.sizeBytes() > policy.maxBytes()) {
            throw new BusinessException(ProductError.INVALID);
        }
        if (images.countByProductIdAndAttachedTrue(product) >= 10) {
            throw new BusinessException(ProductError.STATE);
        }
        var image =
                images.save(
                        new ProductImage(
                                product,
                                seller,
                                request.contentType(),
                                request.sizeBytes(),
                                clock.instant().plusSeconds(300)));
        var put =
                PutObjectRequest.builder()
                        .bucket(policy.bucket())
                        .key(image.uploadKey)
                        .contentType(image.contentType)
                        .contentLength(image.sizeBytes)
                        .build();
        String url =
                presigner
                        .presignPutObject(
                                PutObjectPresignRequest.builder()
                                        .signatureDuration(Duration.ofMinutes(5))
                                        .putObjectRequest(put)
                                        .build())
                        .url()
                        .toExternalForm();
        return new ImageResponse.Upload(
                image.id, url, image.contentType, image.sizeBytes, image.expiresAt);
    }

    /** S3 호출에는 10초 상한이 있습니다. 같은 상품의 이미지 변경·심사 제출과 연결 검증을 직렬화합니다. */
    public ImageResponse complete(UUID seller, UUID product, UUID id) {
        editable(seller, product);
        ProductImage image = owned(seller, product, id);
        if (image.attached) {
            return ImageResponse.from(image);
        }
        if (!policy.ready()) {
            throw new BusinessException(ProductError.POLICY);
        }
        if (!clock.instant().isBefore(image.expiresAt)
                || images.countByProductIdAndAttachedTrue(product) >= 10) {
            throw new BusinessException(ProductError.STATE);
        }
        try {
            var head =
                    s3.headObject(
                            HeadObjectRequest.builder()
                                    .bucket(policy.bucket())
                                    .key(image.uploadKey)
                                    .build());
            if (head.contentLength() != image.sizeBytes
                    || !image.contentType.equals(head.contentType())) {
                throw new BusinessException(ProductError.INVALID);
            }
            // MIME 선언만 믿지 않고 파일 서명을 확인합니다. SVG·GIF 등 능동 콘텐츠/애니메이션 형식은 받지 않습니다.
            byte[] header =
                    s3.getObjectAsBytes(
                                    GetObjectRequest.builder()
                                            .bucket(policy.bucket())
                                            .key(image.uploadKey)
                                            .range("bytes=0-15")
                                            .ifMatch(head.eTag())
                                            .build())
                            .asByteArray();
            if (!matches(image.contentType, header)) {
                throw new BusinessException(ProductError.INVALID);
            }
            image.objectKey = "product-images/" + product + "/" + id;
            // 임시 업로드 URL로 다시 PUT해도 연결된 객체는 바뀌지 않게 서버 전용 키로 복사합니다.
            s3.copyObject(
                    CopyObjectRequest.builder()
                            .sourceBucket(policy.bucket())
                            .sourceKey(image.uploadKey)
                            .destinationBucket(policy.bucket())
                            .destinationKey(image.objectKey)
                            .copySourceIfMatch(head.eTag())
                            .build());
            var current = images.findByProductIdAndAttachedTrueOrderByPositionAsc(product);
            image.position = current.size();
            image.representative = current.isEmpty();
            image.attached = true;
            return ImageResponse.from(image);
        } catch (S3Exception ex) {
            // 객체 미존재·ETag 경합과 S3 장애를 구분하고 원문 주소·서명은 응답/로그에 노출하지 않습니다.
            throw new BusinessException(
                    ex.statusCode() == 404 || ex.statusCode() == 412
                            ? ProductError.INVALID
                            : ProductError.POLICY);
        }
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> list(UUID seller, UUID product) {
        var p =
                products.findById(product)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        p.requireOwner(seller);
        return images.findByProductIdAndAttachedTrueOrderByPositionAsc(product).stream()
                .map(ImageResponse::from)
                .toList();
    }

    public List<ImageResponse> reorder(UUID seller, UUID product, ImageOrderRequest request) {
        editable(seller, product);
        var current = images.findByProductIdAndAttachedTrueOrderByPositionAsc(product);
        var ids = new HashSet<>(request.imageIds());
        if (ids.size() != request.imageIds().size()
                || !ids.contains(request.representativeId())
                || !ids.equals(new HashSet<>(current.stream().map(i -> i.id).toList()))) {
            throw new BusinessException(ProductError.INVALID);
        }
        for (var image : current) {
            image.position = request.imageIds().indexOf(image.id);
            image.representative = image.id.equals(request.representativeId());
        }
        return current.stream()
                .sorted(Comparator.comparingInt(i -> i.position))
                .map(ImageResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImageResponse.Download download(UUID seller, UUID product, UUID id) {
        var p =
                products.findById(product)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        p.requireOwner(seller);
        var image = owned(seller, product, id);
        if (!image.attached) {
            throw new BusinessException(ProductError.NOT_FOUND);
        }
        if (!policy.ready()) {
            throw new BusinessException(ProductError.POLICY);
        }
        var get = GetObjectRequest.builder().bucket(policy.bucket()).key(image.objectKey).build();
        var url =
                presigner
                        .presignGetObject(
                                GetObjectPresignRequest.builder()
                                        .signatureDuration(Duration.ofMinutes(5))
                                        .getObjectRequest(get)
                                        .build())
                        .url()
                        .toExternalForm();
        return new ImageResponse.Download(url, clock.instant().plusSeconds(300));
    }

    public void detach(UUID seller, UUID product, UUID id) {
        editable(seller, product);
        var image = owned(seller, product, id);
        image.attached = false;
        image.representative = false;
        image.storageCleaned = false;
        var remaining = images.findByProductIdAndAttachedTrueOrderByPositionAsc(product);
        for (int n = 0; n < remaining.size(); n++) {
            remaining.get(n).position = n;
            remaining.get(n).representative = n == 0;
        }
        // 즉시 S3를 삭제하지 않습니다. DB 커밋 실패 시 참조가 깨지지 않도록 후속 정리 대상으로 남깁니다.
    }

    private void editable(UUID seller, UUID product) {
        products.lock(product)
                .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND))
                .requireEditable(seller);
    }

    /** 만료 24시간 후 임시 객체와 미연결 객체를 정리합니다. S3 삭제 실패 시 완료 표시도 롤백합니다. */
    public void cleanup(UUID id) {
        if (!policy.ready()) {
            return;
        }
        UUID productId =
                images.findProductId(id)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        products.lock(productId).orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        var image =
                images.findById(id)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        if (image.storageCleaned
                || image.expiresAt.isAfter(clock.instant().minus(Duration.ofHours(24)))) {
            return;
        }
        s3.deleteObject(
                DeleteObjectRequest.builder().bucket(policy.bucket()).key(image.uploadKey).build());
        if (!image.attached) {
            // 복사 성공 후 DB 롤백으로 키가 저장되지 않았어도 결정적 키로 고아 객체를 찾습니다.
            s3.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(policy.bucket())
                            .key("product-images/" + productId + "/" + id)
                            .build());
        }
        image.storageCleaned = true;
    }

    private ProductImage owned(UUID seller, UUID product, UUID id) {
        var image =
                images.findById(id)
                        .orElseThrow(() -> new BusinessException(ProductError.NOT_FOUND));
        if (!image.productId.equals(product) || !image.sellerId.equals(seller)) {
            throw new BusinessException(ProductError.NOT_OWNER);
        }
        return image;
    }

    private boolean matches(String type, byte[] b) {
        if (type.equals("image/jpeg")) {
            return b.length >= 3
                    && (b[0] & 255) == 255
                    && (b[1] & 255) == 216
                    && (b[2] & 255) == 255;
        }
        if (type.equals("image/png")) {
            return b.length >= 8
                    && Arrays.equals(
                            Arrays.copyOf(b, 8),
                            new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        }
        return type.equals("image/webp")
                && b.length >= 12
                && b[0] == 'R'
                && b[1] == 'I'
                && b[2] == 'F'
                && b[3] == 'F'
                && b[8] == 'W'
                && b[9] == 'E'
                && b[10] == 'B'
                && b[11] == 'P';
    }
}
