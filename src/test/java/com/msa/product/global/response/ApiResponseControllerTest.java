package com.msa.product.global.response;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.msa.product.domain.image.command.controller.ProductImageController;
import com.msa.product.domain.image.command.service.ProductImageService;
import com.msa.product.domain.product.command.controller.ProductCommandController;
import com.msa.product.domain.product.command.dto.response.ProductResponse;
import com.msa.product.domain.product.command.service.ProductCommandService;
import com.msa.product.domain.product.exception.ProductErrorCode;
import com.msa.product.domain.product.exception.ProductException;
import com.msa.product.domain.stock.command.controller.StockCommandController;
import com.msa.product.domain.stock.command.dto.response.StockReservationResponse;
import com.msa.product.domain.stock.command.service.StockCommandService;
import com.msa.product.domain.stock.query.service.StockStatusQueryService;
import com.msa.product.global.error.GlobalExceptionHandler;

import org.junit.jupiter.api.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

/** 실제 컨트롤러의 응답 형식만 검증합니다. JWT·역할·소유권 검증은 기존 전체 MVC 통합 테스트에서 확인합니다. */
class ApiResponseControllerTest {
    private final ProductCommandService products = mock(ProductCommandService.class);
    private final ProductImageService images = mock(ProductImageService.class);
    private final StockStatusQueryService stocks = mock(StockStatusQueryService.class);
    private final UUID seller = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var jwt =
                Jwt.withTokenValue("fixture-token")
                        .header("alg", "RS256")
                        .subject(seller.toString())
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new JwtAuthenticationToken(
                                jwt, List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new ProductCommandController(products),
                                new ProductImageController(images),
                                new StockCommandController(mock(StockCommandService.class), stocks))
                        .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                        .setControllerAdvice(new ApiResponseAdvice(), new GlobalExceptionHandler())
                        .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createdProductUsesEnvelopeAndPreserves201() throws Exception {
        var id = UUID.randomUUID();
        when(products.create(seller))
                .thenReturn(ProductResponse.builder().id(id).sellerId(seller).build());
        mvc.perform(post("/api/v1/seller/products"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_CREATED"))
                .andExpect(jsonPath("$.message").value("생성이 완료되었습니다."))
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.sellerId").value(seller.toString()));
    }

    @Test
    void imageDeletionReturns200AndExplicitNullData() throws Exception {
        var product = UUID.randomUUID();
        var image = UUID.randomUUID();
        mvc.perform(delete("/api/v1/seller/products/" + product + "/images/" + image))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(
                        content()
                                .json(
                                        """
{"success":true,"code":"COMMON_SUCCESS","message":"요청이 성공했습니다.","data":null}
"""));
        verify(images).detach(seller, product, image);
    }

    @Test
    void internalStockResponseKeepsExistingOrderContract() throws Exception {
        var id = UUID.randomUUID();
        when(stocks.get(id)).thenReturn(StockReservationResponse.builder().id(id).build());
        mvc.perform(get("/internal/v1/stock-reservations/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void errorResponseIsNotWrappedAsSuccess() throws Exception {
        when(products.create(seller)).thenThrow(new ProductException(ProductErrorCode.POLICY));
        mvc.perform(post("/api/v1/seller/products"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PRODUCT_POLICY"))
                .andExpect(jsonPath("$.success").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
