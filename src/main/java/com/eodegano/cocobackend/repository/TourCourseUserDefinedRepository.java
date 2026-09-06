package com.eodegano.cocobackend.repository;

import com.eodegano.cocobackend.domain.TourCourseUserDefined;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TourCourseUserDefinedRepository extends JpaRepository<TourCourseUserDefined, Long> {

    List<TourCourseUserDefined> findByUserId(Long userId);

    /**
     * 코스가 아직 비로그인(userId IS NULL) 상태일 때만 배정한다.
     * 동시 요청 간 lost update 방지 — 조건부 UPDATE의 영향 행 수로 배정 성공 여부를 판단한다.
     */
    @Modifying
    @Query("UPDATE TourCourseUserDefined c SET c.userId = :userId WHERE c.id = :courseId AND c.userId IS NULL")
    int assignUserIfUnassigned(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
