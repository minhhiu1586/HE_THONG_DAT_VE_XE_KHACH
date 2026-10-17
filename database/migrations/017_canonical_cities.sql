-- Chạy trong transaction của migration runner. Giữ nguyên bến, tuyến, chuyến và vé.
LOCK TABLE tinh_thanh, ben_xe IN SHARE ROW EXCLUSIVE MODE;
DROP INDEX IF EXISTS uq_tinh_thanh_name_key;

CREATE OR REPLACE FUNCTION city_name_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT regexp_replace(
        lower(translate(regexp_replace(normalize(trim(value), NFD),
            U&'[\0300-\036f]', '', 'g'), 'đĐ', 'dD')),
        '[[:space:]]+', ' ', 'g');
$$;

CREATE TEMP TABLE city_canonical ON COMMIT DROP AS
WITH standard(code, name) AS (VALUES
    ('HCM','TP. Hồ Chí Minh'), ('LD','Lâm Đồng'), ('KH','Khánh Hòa'),
    ('CT','Cần Thơ'), ('HN','Hà Nội'), ('DN','Đà Nẵng'), ('NB','Ninh Bình')),
choices AS (
    SELECT city_name_key(t.ten_tinh_thanh) AS name_key,
        coalesce(max(s.code),
            min(t.ma_tinh_thanh) FILTER (WHERE t.ma_tinh_thanh !~ '^(DEMO_|T06_)'),
            min(t.ma_tinh_thanh)) AS canonical_code
    FROM tinh_thanh t LEFT JOIN standard s
        ON city_name_key(t.ten_tinh_thanh) = city_name_key(s.name)
    GROUP BY city_name_key(t.ten_tinh_thanh)
)
SELECT t.ma_tinh_thanh AS old_code, c.canonical_code, c.name_key
FROM tinh_thanh t JOIN choices c
    ON city_name_key(t.ten_tinh_thanh) = c.name_key;

-- Không tự ghi đè nếu mã chuẩn đã được dùng cho một tỉnh khác.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM city_canonical c JOIN tinh_thanh t
        ON t.ma_tinh_thanh=c.canonical_code
        WHERE city_name_key(t.ten_tinh_thanh)<>c.name_key) THEN
        RAISE EXCEPTION 'Mã tỉnh chuẩn đang thuộc tỉnh khác; cần kiểm tra dữ liệu trước khi gộp';
    END IF;
END $$;

INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh,khu_vuc)
SELECT DISTINCT ON (c.canonical_code)
    c.canonical_code,t.ten_tinh_thanh,t.khu_vuc
FROM city_canonical c JOIN tinh_thanh t ON t.ma_tinh_thanh=c.old_code
ORDER BY c.canonical_code,(c.old_code=c.canonical_code) DESC,c.old_code
ON CONFLICT(ma_tinh_thanh) DO NOTHING;

UPDATE ben_xe b SET ma_tinh_thanh=c.canonical_code
FROM city_canonical c
WHERE b.ma_tinh_thanh=c.old_code AND c.old_code<>c.canonical_code;

DELETE FROM tinh_thanh t USING city_canonical c
WHERE t.ma_tinh_thanh=c.old_code AND c.old_code<>c.canonical_code;

-- Chặn tạo lại cùng tỉnh dưới mã khác, kể cả khác hoa/thường, dấu, khoảng trắng.
CREATE UNIQUE INDEX IF NOT EXISTS uq_tinh_thanh_name_key
ON tinh_thanh(city_name_key(ten_tinh_thanh));
