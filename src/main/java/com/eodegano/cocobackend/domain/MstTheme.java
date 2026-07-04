package com.eodegano.cocobackend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mst_theme")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MstTheme {

    @Id
    @Column(name = "themeCode", length = 20)
    private String themeCode;

    @Column(name = "themeName", nullable = false, length = 20)
    private String themeName;
}
