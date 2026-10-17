# T06 — Ma trận kiểm thử

[test-cases.json](test-cases.json) chứa 79 ca cho 22 use case (UC-01–23, đã bỏ UC-21), mỗi UC có nhóm hợp lệ, không hợp lệ và biên. Mỗi ca ghi đầu vào, bước, kết quả mong đợi, trạng thái, người thực hiện, bằng chứng và lý do bị chặn. Nguồn T01/T02/T05 nằm đầu file; ca chưa có API là kế hoạch kiểm thử, cần cụ thể hóa theo API được duyệt trước khi chạy.

```powershell
python tests/use-cases/check_matrix.py
```

[Kết quả đã chạy](ket-qua-kiem-thu.md). Validator chỉ kiểm tra cấu trúc/độ phủ ma trận, không chạy nghiệp vụ. Khi chức năng sẵn sàng, thành viên phụ trách cập nhật đầu vào/bước cụ thể, chạy ca, rồi ghi `actual_result`, `executor`, `evidence`; chỉ chuyển Pass khi có bằng chứng.

[Dữ liệu và cách dùng cho frontend](../../database/README.md#section-4). Chưa chuyển T06 Done khi còn ca chưa chạy/bị chặn.
