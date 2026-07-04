package com.eodegano.cocobackend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mst_sigungu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MstSigungu {

    @Id
    @Column(name = "sigunguCode", length = 20)
    private String sigunguCode;

    @Column(name = "sigunguName", nullable = false, length = 20)
    private String sigunguName;
}
