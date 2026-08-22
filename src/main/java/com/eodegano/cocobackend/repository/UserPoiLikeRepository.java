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
}
