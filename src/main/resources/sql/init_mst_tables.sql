-- =============================================
-- mst_sigungu : 시군구 마스터
-- =============================================
CREATE TABLE IF NOT EXISTS mst_sigungu (
    sigunguCode VARCHAR(20) NOT NULL,
    sigunguName VARCHAR(20) NOT NULL,
    PRIMARY KEY (sigunguCode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO mst_sigungu (sigunguCode, sigunguName) VALUES
('111', '포항시 남구'),
('113', '포항시 북구'),
('130', '경주시'),
('150', '김천시'),
('170', '안동시'),
('190', '구미시'),
('210', '영주시'),
('230', '영천시'),
('250', '상주시'),
('280', '문경시'),
('290', '경산시'),
('730', '의성군'),
('750', '청송군'),
('760', '영양군'),
('770', '영덕군'),
('820', '청도군'),
('830', '고령군'),
('840', '성주군'),
('850', '칠곡군'),
('900', '예천군'),
('920', '봉화군'),
('930', '울진군'),
('940', '울릉군');

-- =============================================
-- mst_theme : 테마 마스터
-- =============================================
CREATE TABLE IF NOT EXISTS mst_theme (
    themeCode VARCHAR(20) NOT NULL,
    themeName VARCHAR(20) NOT NULL,
    PRIMARY KEY (themeCode)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO mst_theme (themeCode, themeName) VALUES
('001', '어드벤처'),
('002', '휴식'),
('003', '문화'),
('004', '음식');
