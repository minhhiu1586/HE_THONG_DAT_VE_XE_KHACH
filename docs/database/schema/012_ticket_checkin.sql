CREATE TABLE IF NOT EXISTS nhat_ky_kiem_ve (
    ma_log varchar(40) PRIMARY KEY,
    ma_ve varchar(40) NOT NULL REFERENCES ve_xe(ma_ve),
    ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
    ma_ghe varchar(40) NOT NULL,
    ket_qua varchar(30) NOT NULL DEFAULT 'DA_LEN_XE',
    thoi_diem_kiem timestamp without time zone NOT NULL DEFAULT (now() at time zone 'Asia/Ho_Chi_Minh'),
    ma_nhan_vien varchar(40)
);

CREATE INDEX IF NOT EXISTS ix_nhat_ky_kiem_ve_chuyen ON nhat_ky_kiem_ve(ma_chuyen, thoi_diem_kiem);
