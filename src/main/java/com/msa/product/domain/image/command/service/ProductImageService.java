package com.msa.product.domain.image.command.service;

import com.msa.product.domain.image.command.dto.request.*;
import com.msa.product.domain.image.command.dto.response.ImageResponse;
import java.util.*;

/** 이미지 업로드 권한·연결·구성 변경 계약입니다. 임의 S3 키 연결을 허용하지 않습니다. */
public interface ProductImageService {
    ImageResponse.Upload issue(UUID seller, UUID product, ImageUploadRequest request);

    ImageResponse complete(UUID seller, UUID product, UUID image);

    List<ImageResponse> list(UUID seller, UUID product);

    ImageResponse.Download download(UUID seller, UUID product, UUID image);

    List<ImageResponse> reorder(UUID seller, UUID product, ImageOrderRequest request);

    void detach(UUID seller, UUID product, UUID image);

    void cleanup(UUID image);
}
