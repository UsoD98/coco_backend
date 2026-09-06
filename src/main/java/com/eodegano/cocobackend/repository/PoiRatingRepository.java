package com.eodegano.cocobackend.repository;

import com.eodegano.cocobackend.domain.PoiRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PoiRatingRepository extends JpaRepository<PoiRating, Long> {

    List<PoiRating> findByContentidIn(List<Long> contentIds);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PoiRating p SET p.likes = COALESCE(p.likes, 0) + 1 WHERE p.contentid = :contentId")
    void incrementLikes(@Param("contentId") Long contentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PoiRating p SET p.likes = CASE WHEN COALESCE(p.likes, 0) > 0 THEN COALESCE(p.likes, 0) - 1 ELSE 0 END WHERE p.contentid = :contentId")
    void decrementLikes(@Param("contentId") Long contentId);

    // 회원 탈퇴(하드 삭제) 시 해당 유저가 눌렀던 좋아요 수만큼 일괄 차감
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PoiRating p SET p.likes = CASE WHEN COALESCE(p.likes, 0) > 0 THEN COALESCE(p.likes, 0) - 1 ELSE 0 END WHERE p.contentid IN :contentIds")
    void decrementLikesForContentIds(@Param("contentIds") List<Long> contentIds);
}
