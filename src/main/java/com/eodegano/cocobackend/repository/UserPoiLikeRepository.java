package com.eodegano.cocobackend.repository;

import com.eodegano.cocobackend.domain.UserPoiLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserPoiLikeRepository extends JpaRepository<UserPoiLike, UserPoiLike.UserPoiLikeId> {
    Optional<UserPoiLike> findByUserIdAndContentId(Long userId, Long contentId);
    boolean existsByUserIdAndContentId(Long userId, Long contentId);

    @Query("SELECT u.contentId FROM UserPoiLike u WHERE u.userId = :userId AND u.contentId IN :contentIds")
    List<Long> findContentIdsByUserIdAndContentIdIn(@Param("userId") Long userId, @Param("contentIds") List<Long> contentIds);

    // 회원 탈퇴 시 poi_rating.likes 차감 대상을 먼저 조회
    @Query("SELECT u.contentId FROM UserPoiLike u WHERE u.userId = :userId")
    List<Long> findContentIdsByUserId(@Param("userId") Long userId);

    // 회원 탈퇴(하드 삭제) 시 해당 유저의 좋아요 기록 정리
    void deleteByUserId(Long userId);
}
