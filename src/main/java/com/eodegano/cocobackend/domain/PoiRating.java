package com.eodegano.cocobackend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "poi_rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class PoiRating {

    @Id
    @Column(name = "contentid")
    private Long contentid;

    @Column(name = "stars", precision = 3, scale = 1)
    private BigDecimal stars;

    @Column(name = "likes", nullable = false)
    private Integer likes;

    public static PoiRating createWithLike(Long contentId) {
        return PoiRating.builder()
                .contentid(contentId)
                .stars(null)
                .likes(1)
                .build();
    }

    public int getLikesOrZero() {
        return likes == null ? 0 : likes;
    }
}
