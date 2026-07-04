package com.eodegano.cocobackend.repository;

import com.eodegano.cocobackend.domain.Tour;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TourRepository extends JpaRepository<Tour, Long> {
    @Query("SELECT t FROM Tour t WHERE t.lDongSignguCd = :lDongSignguCd")
    List<Tour> findByLDongSignguCd(@Param("lDongSignguCd") String lDongSignguCd);

    @Query("SELECT t FROM Tour t WHERE t.lDongSignguCd IN :codes")
    List<Tour> findByLDongSignguCdIn(@Param("codes") List<String> codes);

    List<Tour> findByContentidIn(List<Long> contentIds);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tour t SET t.likes = COALESCE(t.likes, 0) + 1 WHERE t.contentid = :contentId")
    void incrementLikes(@Param("contentId") Long contentId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Tour t SET t.likes = CASE WHEN COALESCE(t.likes, 0) > 0 THEN COALESCE(t.likes, 0) - 1 ELSE 0 END WHERE t.contentid = :contentId")
    void decrementLikes(@Param("contentId") Long contentId);
}
