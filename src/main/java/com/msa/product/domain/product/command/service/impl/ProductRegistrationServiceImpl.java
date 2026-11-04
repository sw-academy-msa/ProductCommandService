package com.msa.product.domain.product.command.service.impl;

import com.msa.product.domain.outbox.service.ProjectionOutboxService;
import com.msa.product.domain.product.command.dto.request.RegistrationRequests.*;
import com.msa.product.domain.product.command.dto.response.*;
import com.msa.product.domain.product.command.service.ProductRegistrationService;
import com.msa.product.domain.product.entity.*;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.product.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.*;
import java.util.*;

/** 단계마다 상품 잠금·소유권·버전을 확인하고 해당 단계만 원자적으로 갱신합니다. 외부 HTTP는 호출하지 않습니다. */
@Service
@Transactional
public class ProductRegistrationServiceImpl implements ProductRegistrationService {
    private final ProductRepository products;
    private final Clock clock;
    private final ProjectionOutboxService outbox;

    public ProductRegistrationServiceImpl(
            ProductRepository products, Clock clock, ProjectionOutboxService outbox) {
        this.products = products;
        this.clock = clock;
        this.outbox = outbox;
    }

    public ProductResponse basic(UUID seller, UUID id, Basic r) {
        var p = editable(seller, id, r.expectedVersion());
        if (r.sourceUrl() != null && !r.sourceUrl().isBlank()) {
            try {
                var uri = URI.create(r.sourceUrl());
                if (!("https".equalsIgnoreCase(uri.getScheme())
                                || "http".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null
                        || uri.getUserInfo() != null) {
                    fail(ProductErrorCode.INVALID);
                }
            } catch (IllegalArgumentException ex) {
                fail(ProductErrorCode.INVALID);
            }
        }
        p.name = r.name();
        p.description = r.description();
        p.brandOrStore = r.brandOrStore();
        p.category = r.category();
        p.sourceUrl = r.sourceUrl();
        return finish(p);
    }

    public ProductResponse details(UUID seller, UUID id, Details r) {
        var p = editable(seller, id, r.expectedVersion());
        p.disposalReason = r.disposalReason();
        p.attributes.clear();
        r.attributes()
                .forEach(
                        a ->
                                p.attributes.add(
                                        ProductAttribute.builder()
                                                .name(a.name().trim())
                                                .value(a.value().trim())
                                                .build()));
        return finish(p);
    }

    /** 선택지 정의와 실제 조합을 함께 검증합니다. 조합 ID를 유지하며 임의 문자열 분해로 옵션을 복원하지 않습니다. */
    public ProductResponse options(UUID seller, UUID id, Options r) {
        var p = editable(seller, id, r.expectedVersion());
        Map<String, List<String>> dimensions = new LinkedHashMap<>();
        for (var d : r.dimensions()) {
            var values = d.values().stream().map(String::trim).toList();
            if (values.stream().distinct().count() != values.size()
                    || dimensions.putIfAbsent(d.name().trim(), values) != null) {
                fail(ProductErrorCode.INVALID);
            }
        }
        if (r.variants().stream().mapToLong(Variant::initialQuantity).sum() > Integer.MAX_VALUE) {
            fail(ProductErrorCode.INVALID);
        }
        if (dimensions.isEmpty() && r.variants().size() > 1) {
            fail(ProductErrorCode.INVALID);
        }
        Set<Map<String, String>> combinations = new HashSet<>();
        Set<UUID> requestedIds = new HashSet<>();
        var existing = new HashMap<UUID, ProductOption>();
        p.options.forEach(o -> existing.put(o.id, o));
        var desired = new ArrayList<ProductOption>();
        var labels = new HashSet<String>();
        for (var v : r.variants()) {
            var selections = new LinkedHashMap<String, String>();
            v.selections()
                    .forEach(
                            (key, value) -> {
                                if (selections.putIfAbsent(key.trim(), value.trim()) != null) {
                                    fail(ProductErrorCode.INVALID);
                                }
                            });
            if (!selections.keySet().equals(dimensions.keySet()) || !combinations.add(selections)) {
                fail(ProductErrorCode.INVALID);
            }
            for (var entry : selections.entrySet()) {
                if (!dimensions.get(entry.getKey()).contains(entry.getValue())) {
                    fail(ProductErrorCode.INVALID);
                }
            }
            var label = optionLabel(dimensions, selections);
            if (label.length() > 500 || !labels.add(label)) {
                fail(ProductErrorCode.INVALID);
            }
            ProductOption option;
            if (v.id() == null) {
                option =
                        ProductOption.builder()
                                .product(p)
                                .label(label)
                                .quantity(v.initialQuantity())
                                .build();
            } else {
                if (!requestedIds.add(v.id()) || !existing.containsKey(v.id())) {
                    fail(ProductErrorCode.INVALID);
                }
                option = existing.get(v.id());
                if (option.reservedQuantity != 0 || option.confirmedQuantity != 0) {
                    fail(ProductErrorCode.STATE);
                }
            }
            option.label = label;
            option.initialQuantity = v.initialQuantity();
            option.selections.clear();
            option.selections.putAll(selections);
            desired.add(option);
        }
        // 같은 라벨을 다른 ID로 재생성하는 유일 제약 충돌을 피하기 위해 제거/재배치를 두 번에 나눕니다.
        p.options.removeIf(o -> !desired.contains(o));
        String temporaryPrefix = "__editing__" + UUID.randomUUID() + ":";
        for (var o : p.options) {
            o.label = temporaryPrefix + o.id;
        }
        p.optionDimensions.clear();
        products.flush();
        for (int n = 0; n < desired.size(); n++) {
            var o = desired.get(n);
            o.label = optionLabel(dimensions, o.selections);
            if (!p.options.contains(o)) {
                p.options.add(o);
            }
        }
        dimensions.forEach(
                (name, values) ->
                        p.optionDimensions.add(
                                ProductOptionDimension.builder()
                                        .product(p)
                                        .name(name)
                                        .values(values)
                                        .build()));
        return finish(p);
    }

    public ProductResponse pricingShipping(
            UUID seller, UUID id, PricingShipping r, SellerShippingResponse snapshot) {
        var p = editable(seller, id, r.expectedVersion());
        if (snapshot == null
                || !seller.equals(snapshot.accountId())
                || snapshot.dispatchAddress() == null
                || snapshot.returnAddress() == null) {
            fail(ProductErrorCode.POLICY);
        }
        p.price = r.price();
        p.shippingFee = r.shippingFee();
        p.dispatchBusinessDays = r.dispatchBusinessDays();
        p.perAccountLimit = r.perAccountLimit();
        p.shippingNotice = r.shippingNotice();
        p.purchaseNotice = r.purchaseNotice();
        p.dispatchAddress = address(snapshot.dispatchAddress());
        p.returnAddress = address(snapshot.returnAddress());
        p.addressesCapturedAt = clock.instant();
        return finish(p);
    }

    public ProductResponse schedule(UUID seller, UUID id, Schedule r) {
        var p = editable(seller, id, r.expectedVersion());
        if (r.startsAt() != null && !r.startsAt().toInstant().isAfter(clock.instant())) {
            fail(ProductErrorCode.INVALID);
        }
        p.startsAt = r.startsAt() == null ? null : r.startsAt().toInstant();
        p.endsAt = p.startsAt == null ? null : p.startsAt.plus(Duration.ofHours(3));
        return finish(p);
    }

    public ProductResponse faqs(UUID seller, UUID id, Faqs r) {
        var p = editable(seller, id, r.expectedVersion());
        var desired = new ArrayList<ProductFaq>();
        var used = new HashSet<UUID>();
        for (var input : r.faqs()) {
            if (input.id() != null && !used.add(input.id())) {
                fail(ProductErrorCode.INVALID);
            }
            var f = input.id() == null ? newFaq(p, seller, input) : faq(p, input.id());
            update(f, seller, input);
            desired.add(f);
        }
        p.faqs.removeIf(f -> !desired.contains(f));
        // List 순서를 바꾸되 기존 FAQ ID는 유지합니다.
        for (var f : desired) {
            if (!p.faqs.contains(f)) {
                p.faqs.add(f);
            }
        }
        p.faqs.sort(Comparator.comparingInt(desired::indexOf));
        return finish(p);
    }

    public ProductResponse addFaq(UUID seller, UUID id, long version, Faq r) {
        var p = faqEditable(seller, id, version);
        if (r.id() != null) {
            fail(ProductErrorCode.INVALID);
        }
        p.faqs.add(newFaq(p, seller, r));
        return finish(p);
    }

    public ProductResponse updateFaq(UUID seller, UUID id, UUID faqId, long version, Faq r) {
        var p = faqEditable(seller, id, version);
        if (r.id() != null && !r.id().equals(faqId)) {
            fail(ProductErrorCode.INVALID);
        }
        update(faq(p, faqId), seller, r);
        return finish(p);
    }

    public ProductResponse deleteFaq(UUID seller, UUID id, UUID faqId, long version) {
        var p = faqEditable(seller, id, version);
        p.faqs.remove(faq(p, faqId));
        return finish(p);
    }

    private Product editable(UUID seller, UUID id, long version) {
        var p = locked(seller, id, version);
        p.requireEditable(seller);
        p.status = Product.Status.DRAFT;
        p.registrationFormatVersion = 1;
        return p;
    }

    private Product faqEditable(UUID seller, UUID id, long version) {
        var p = locked(seller, id, version);
        if (p.status == Product.Status.IN_REVIEW) {
            fail(ProductErrorCode.STATE);
        }
        return p;
    }

    private Product locked(UUID seller, UUID id, long version) {
        var p =
                products.lock(id)
                        .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
        p.requireOwner(seller);
        if (p.version != version) {
            fail(ProductErrorCode.STATE);
        }
        return p;
    }

    private ProductResponse finish(Product p) {
        p.changeId = UUID.randomUUID();
        p.updatedAt = clock.instant();
        products.flush();
        outbox.productChanged(p);
        return ProductResponse.from(p);
    }

    /** 값 자체의 슬래시·역슬래시를 구분자로 오인해 서로 다른 조합이 같은 DB 라벨이 되지 않게 합니다. */
    private String optionLabel(
            Map<String, List<String>> dimensions, Map<String, String> selections) {
        if (dimensions.isEmpty()) {
            return "기본";
        }
        return String.join(
                " / ",
                dimensions.keySet().stream()
                        .map(key -> selections.get(key).replace("\\", "\\\\").replace("/", "\\/"))
                        .toList());
    }

    private ProductAddress address(SellerShippingResponse.Location l) {
        return ProductAddress.builder()
                .postalCode(l.postalCode())
                .roadAddress(l.roadAddress())
                .detailAddress(l.detailAddress())
                .build();
    }

    private ProductFaq faq(Product p, UUID id) {
        return p.faqs.stream()
                .filter(f -> f.id.equals(id))
                .findFirst()
                .orElseThrow(() -> new ProductException(ProductErrorCode.NOT_FOUND));
    }

    private ProductFaq newFaq(Product p, UUID seller, Faq input) {
        var f =
                ProductFaq.builder()
                        .product(p)
                        .question("")
                        .answer("")
                        .suggestionId(null)
                        .seller(seller)
                        .now(clock.instant())
                        .build();
        update(f, seller, input);
        return f;
    }

    private void update(ProductFaq f, UUID seller, Faq r) {
        if (!r.confirmed()) {
            fail(ProductErrorCode.INVALID);
        }
        f.question = r.question().trim();
        f.answer = r.answer().trim();
        f.suggestionId = r.suggestionId();
        f.confirmedBy = seller;
        f.confirmedAt = clock.instant();
    }

    private void fail(ProductErrorCode error) {
        throw new ProductException(error);
    }
}
