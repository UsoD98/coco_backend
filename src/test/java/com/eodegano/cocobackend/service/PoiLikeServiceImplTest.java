package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.domain.UserPoiLike;
import com.eodegano.cocobackend.dto.PoiLikeResponseDto;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.UserPoiLikeRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PoiLikeServiceImplTest {

    @InjectMocks
    private PoiLikeServiceImpl poiLikeService;

    @Mock private UserRepository userRepository;
    @Mock private UserPoiLikeRepository userPoiLikeRepository;
    @Mock private PoiRatingRepository poiRatingRepository;

    private User mockUser;
    private static final String EMAIL = "test@test.com";
    private static final Long CONTENT_ID = 126289L;

    @BeforeEach
    void setUp() {
        mockUser = User.builder().id(1L).email(EMAIL).nickname("테스터").role("USER").build();
    }

    @Test
    @DisplayName("실패 - 존재하지 않는 사용자 → NoSuchElementException")
    void toggleLikeFailWithUserNotFound() {
        given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> poiLikeService.toggleLike(CONTENT_ID, EMAIL))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("존재하지 않는 사용자입니다");
    }

    @Test
    @DisplayName("좋아요 추가 성공 - poi_rating 행이 없으면 on-demand 생성")
    void toggleLikeAddSuccessWithNewPoiRating() {
        given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(mockUser));
        given(userPoiLikeRepository.findByUserIdAndContentId(1L, CONTENT_ID)).willReturn(Optional.empty());
        given(poiRatingRepository.existsById(CONTENT_ID)).willReturn(false);
        given(poiRatingRepository.findById(CONTENT_ID))
                .willReturn(Optional.of(PoiRating.createWithLike(CONTENT_ID)));

        PoiLikeResponseDto result = poiLikeService.toggleLike(CONTENT_ID, EMAIL);

        assertThat(result.isLiked()).isTrue();
        assertThat(result.getLikes()).isEqualTo(1);
        verify(userPoiLikeRepository).save(any(UserPoiLike.class));
        verify(poiRatingRepository).save(any(PoiRating.class));
        verify(poiRatingRepository, never()).incrementLikes(any());
    }

    @Test
    @DisplayName("좋아요 추가 성공 - poi_rating 행이 이미 있으면 원자적 increment")
    void toggleLikeAddSuccessWithExistingPoiRating() {
        given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(mockUser));
        given(userPoiLikeRepository.findByUserIdAndContentId(1L, CONTENT_ID)).willReturn(Optional.empty());
        given(poiRatingRepository.existsById(CONTENT_ID)).willReturn(true);
        given(poiRatingRepository.findById(CONTENT_ID))
                .willReturn(Optional.of(PoiRating.builder().contentid(CONTENT_ID).likes(5).build()));

        PoiLikeResponseDto result = poiLikeService.toggleLike(CONTENT_ID, EMAIL);

        assertThat(result.isLiked()).isTrue();
        assertThat(result.getLikes()).isEqualTo(5);
        verify(poiRatingRepository).incrementLikes(CONTENT_ID);
        verify(poiRatingRepository, never()).save(any());
    }

    @Test
    @DisplayName("좋아요 취소 성공 - 기존 좋아요 삭제 + 원자적 decrement")
    void toggleLikeRemoveSuccess() {
        UserPoiLike existing = UserPoiLike.of(1L, CONTENT_ID);
        given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(mockUser));
        given(userPoiLikeRepository.findByUserIdAndContentId(1L, CONTENT_ID)).willReturn(Optional.of(existing));
        given(poiRatingRepository.findById(CONTENT_ID))
                .willReturn(Optional.of(PoiRating.builder().contentid(CONTENT_ID).likes(2).build()));

        PoiLikeResponseDto result = poiLikeService.toggleLike(CONTENT_ID, EMAIL);

        assertThat(result.isLiked()).isFalse();
        assertThat(result.getLikes()).isEqualTo(2);
        verify(userPoiLikeRepository).delete(existing);
        verify(poiRatingRepository).decrementLikes(CONTENT_ID);
    }

    @Test
    @DisplayName("좋아요 취소 성공 - poi_rating 조회 실패해도 likes 0으로 방어")
    void toggleLikeRemoveSuccessWithMissingPoiRating() {
        UserPoiLike existing = UserPoiLike.of(1L, CONTENT_ID);
        given(userRepository.findByEmailAndDeletedAtIsNull(EMAIL)).willReturn(Optional.of(mockUser));
        given(userPoiLikeRepository.findByUserIdAndContentId(1L, CONTENT_ID)).willReturn(Optional.of(existing));
        given(poiRatingRepository.findById(CONTENT_ID)).willReturn(Optional.empty());

        PoiLikeResponseDto result = poiLikeService.toggleLike(CONTENT_ID, EMAIL);

        assertThat(result.isLiked()).isFalse();
        assertThat(result.getLikes()).isEqualTo(0);
    }
}
