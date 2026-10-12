# Quy trình làm việc của nhóm

## Bắt đầu một task

1. Đọc task trên Plane, các phụ thuộc và tiêu chí nghiệm thu.
2. Từ nhánh `develop` sạch, chạy `git pull --ff-only origin develop`.
3. Tạo nhánh riêng, ví dụ `feat/datvexe-17-operator-management`.
4. Cập nhật code, tài liệu, migration và kiểm thử liên quan trong cùng Pull Request.

Không commit trực tiếp lên `main` hoặc `develop`. Không đưa `.env`, mật khẩu, token hay dữ liệu thật vào repo.

## Trước khi mở Pull Request

Từ thư mục gốc repository:

```bash
mvn -B -f src/backend/pom.xml verify
python tests/use-cases/check_matrix.py
```

Trong `src/frontend`:

```bash
npm ci
npm run lint
npm run build
```

Chạy thêm smoke test thích hợp khi thay đổi API hoặc giao diện. Ghi lệnh và kết quả vào PR. Bộ kiểm tra trên CI chỉ chạy các bài test không cần PostgreSQL; smoke test tích hợp cần môi trường riêng và bằng chứng trong PR.

## Review và hoàn thành

- PR hướng vào `develop`, có mã `DATVEXE-...`, mô tả thay đổi và kết quả kiểm thử.
- Reviewer khác người viết. Xử lý nhận xét và đợi CI xanh trước khi merge.
- Chỉ đánh dấu Done trên Plane khi mã đã merge và bằng chứng nghiệm thu được ghi lại.
- Sau merge, đồng bộ `develop` trước task kế tiếp.

Khi thay đổi API, sửa hợp đồng trong `docs/api/`. Khi thay đổi bảng/cột, thêm migration mới và hướng dẫn áp dụng cho database đã tồn tại; không sửa âm thầm schema cũ đã dùng. Khi thay đổi quy tắc nghiệp vụ, cập nhật Use Case, ma trận quyền và test liên quan.
