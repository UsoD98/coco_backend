package com.eodegano.cocobackend.service;

import com.eodegano.cocobackend.domain.PoiRating;
import com.eodegano.cocobackend.domain.User;
import com.eodegano.cocobackend.domain.UserPoiLike;
import com.eodegano.cocobackend.dto.PoiLikeResponseDto;
import com.eodegano.cocobackend.repository.PoiRatingRepository;
import com.eodegano.cocobackend.repository.UserPoiLikeRepository;
import com.eodegano.cocobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * v0.5.0: 로컬 Tour 테이블이 없어져 "존재하지 않는 POI" 라이브 재검증은 하지 않는다.
 * 프론트가 이미 TourAPI로 확인된 contentId만 전달한다고 신뢰하고, poi_rating 행은 on-demand로 생성한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PoiLikeServiceImpl implements PoiLikeService {

    private final UserRepository userRepository;
    private final UserPoiLikeRepository userPoiLikeRepository;
    private final PoiRatingRepository poiRatingRepository;

    @Override
    @Transactional
    public PoiLikeResponseDto toggleLike(Long contentId, String userEmail) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(userEmail)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 사용자입니다"));

        log.info("User {} liked contentId {}", user.getEmail(), contentId);
        Optional<UserPoiLike> existing = userPoiLikeRepository.findByUserIdAndContentId(user.getId(), contentId);

        boolean liked;
        int likes;
        if (existing.isPresent()) {
            userPoiLikeRepository.delete(existing.get());
            poiRatingRepository.decrementLikes(contentId);
            liked = false;
            likes = poiRatingRepository.findById(contentId).map(PoiRating::getLikesOrZero).orElse(0);
        } else {
            userPoiLikeRepository.save(UserPoiLike.of(user.getId(), contentId));
            if (poiRatingRepository.existsById(contentId)) {
                poiRatingRepository.incrementLikes(contentId);
            } else {
                poiRatingRepository.save(PoiRating.createWithLike(contentId));
            }
            liked = true;
            likes = poiRatingRepository.findById(contentId).map(PoiRating::getLikesOrZero).orElse(1);
        }

        return new PoiLikeResponseDto(liked, likes);
    }
}
