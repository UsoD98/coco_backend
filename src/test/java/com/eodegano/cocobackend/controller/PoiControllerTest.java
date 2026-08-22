package com.eodegano.cocobackend.controller;

import com.eodegano.cocobackend.dto.PoiCurationItemDto;
import com.eodegano.cocobackend.dto.PoiCurationResponseDto;
import com.eodegano.cocobackend.dto.PoiDetailResponseDto;
import com.eodegano.cocobackend.dto.PoiInfoItemDto;
import com.eodegano.cocobackend.dto.PoiLikeResponseDto;
import com.eodegano.cocobackend.security.JwtProvider;
import com.eodegano.cocobackend.service.PoiCurationService;
import com.eodegano.cocobackend.service.PoiDetailService;
import com.eodegano.cocobackend.service.PoiLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PoiController 슬라이스(웹 계층) 테스트.
 * 서비스는 모두 Mock으로 대체하고 Controller → GlobalExceptionHandler → JSON 응답 래핑까지만 검증한다.
 * addFilters=false로 서블릿 필터 체인(JWT/Security)을 생략했으므로, permitAll/hasRole 등
 * 실제 인가 규칙 검증은 이 테스트의 범위가 아니다 (SecurityConfig 별도 검증 필요).
 */
@WebMvcTest(PoiController.class)
@AutoConfigureMockMvc(addFilters = false)
class PoiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PoiCurationService poiCurationService;
    @MockitoBean private PoiDetailService poiDetailService;
    @MockitoBean private PoiLikeService poiLikeService;

    // @WebMvcTest는 Filter 빈(JwtAuthenticationFilter)도 컨텍스트에 올리므로 그 의존성만 목으로 채워준다.
    // addFilters=false라 실제로 실행되지는 않는다.
    @MockitoBean private JwtProvider jwtProvider;

    // ───────────────────────────────────────────────
    // GET /api/v1/poi
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/poi - 성공 200")
    void getPoiListSuccess() throws Exception {
        PoiCurationResponseDto response = PoiCurationResponseDto.builder()
                .available(true)
                .items(List.of(PoiCurationItemDto.builder()
                        .contentId(126289L)
                        .contentTypeId(12)
                        .title("불국사")
                        .mapx(new BigDecimal("129.3316"))
                        .mapy(new BigDecimal("35.7903"))
                        .thumbnail("http://img.jpg")
                        .avgPrice(null)
                        .liked(true)
                        .build()))
                .build();
        given(poiCurationService.getPoiList("35130", 2, null, null)).willReturn(response);

        mockMvc.perform(get("/api/v1/poi")
                        .param("sigunguCode", "35130")
                        .param("peopleCount", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.items[0].contentId").value(126289))
                .andExpect(jsonPath("$.data.items[0].title").value("불국사"))
                .andExpect(jsonPath("$.data.items[0].liked").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/poi - 필수 파라미터(sigunguCode) 누락 시 400")
    void getPoiListFailWithMissingSigunguCode() throws Exception {
        mockMvc.perform(get("/api/v1/poi").param("peopleCount", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    @Test
    @DisplayName("GET /api/v1/poi - 필수 파라미터(peopleCount) 누락 시 400")
    void getPoiListFailWithMissingPeopleCount() throws Exception {
        mockMvc.perform(get("/api/v1/poi").param("sigunguCode", "35130"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"));
    }

    // ───────────────────────────────────────────────
    // GET /api/v1/poi/{contentId} (GBC018)
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/poi/{contentId} - 성공 200")
    void getPoiDetailSuccess() throws Exception {
        PoiDetailResponseDto response = PoiDetailResponseDto.builder()
                .contentId(126289L)
                .contentTypeId(12)
                .title("불국사")
                .tel("054-746-9913")
                .homepage("https://www.bulguksa.or.kr")
                .overview("신라 경덕왕 10년에 창건된 사찰...")
                .firstimage("https://example.com/image1.jpg")
                .firstimage2("https://example.com/image2.jpg")
                .addr1("경상북도 경주시 불국로 385")
                .addr2(null)
                .mapx(new BigDecimal("129.3316"))
                .mapy(new BigDecimal("35.7903"))
                .avgPrice(null)
                .infoList(List.of(PoiInfoItemDto.builder()
                        .infoname("입장료")
                        .infotext("어른 6,000원 / 청소년 4,000원")
                        .build()))
                .liked(true)
                .totalLiked(5)
                .build();
        given(poiDetailService.getPoiDetail(126289L, null)).willReturn(response);

        mockMvc.perform(get("/api/v1/poi/126289"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("200"))
                .andExpect(jsonPath("$.data.contentId").value(126289))
                .andExpect(jsonPath("$.data.title").value("불국사"))
                .andExpect(jsonPath("$.data.tel").value("054-746-9913"))
                .andExpect(jsonPath("$.data.mapx").value(129.3316))
                .andExpect(jsonPath("$.data.infoList[0].infoname").value("입장료"))
                .andExpect(jsonPath("$.data.infoList[0].infotext").value("어른 6,000원 / 청소년 4,000원"))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.totalLiked").value(5));
    }

    @Test
    @DisplayName("GET /api/v1/poi/{contentId} - 존재하지 않는 contentId → 404")
    void getPoiDetailFailWithNotFound() throws Exception {
        given(poiDetailService.getPoiDetail(999L, null))
                .willThrow(new NoSuchElementException("존재하지 않는 POI입니다"));

        mockMvc.perform(get("/api/v1/poi/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"))
                .andExpect(jsonPath("$.msg").value("존재하지 않는 POI입니다"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ───────────────────────────────────────────────
    // POST /api/v1/poi/{contentId}/like
    // ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/poi/{contentId}/like - 좋아요 추가 성공 200")
    void toggleLikeAddSuccess() throws Exception {
        given(poiLikeService.toggleLike(126289L, "test@test.com"))
                .willReturn(new PoiLikeResponseDto(true, 1));

        mockMvc.perform(post("/api/v1/poi/126289/like")
                        .principal(new UsernamePasswordAuthenticationToken("test@test.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("좋아요가 추가되었습니다."))
                .andExpect(jsonPath("$.data.liked").value(true))
                .andExpect(jsonPath("$.data.likes").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/poi/{contentId}/like - 좋아요 취소 성공 200")
    void toggleLikeCancelSuccess() throws Exception {
        given(poiLikeService.toggleLike(126289L, "test@test.com"))
                .willReturn(new PoiLikeResponseDto(false, 0));

        mockMvc.perform(post("/api/v1/poi/126289/like")
                        .principal(new UsernamePasswordAuthenticationToken("test@test.com", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msg").value("좋아요가 취소되었습니다."))
                .andExpect(jsonPath("$.data.liked").value(false))
                .andExpect(jsonPath("$.data.likes").value(0));
    }
}
