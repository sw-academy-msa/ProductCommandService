package com.msa.product.domain.image.command.dto.request;

import jakarta.validation.constraints.*;
import java.util.*;

/** 연결된 이미지 전체 순서와 대표 이미지 ID를 명시합니다. */
public record ImageOrderRequest(
        @NotEmpty List<@NotNull UUID> imageIds, @NotNull UUID representativeId) {}
