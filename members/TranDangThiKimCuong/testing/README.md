# Kim Cương — Kiểm thử nhân viên và phân công

- [Checklist tự kiểm tra: 86 mã ca theo từng luồng](manual-checklist.md).
- [Thay đổi và kết quả build/test ngày 20/10/2026](backend-refactor-2026-10-20.md).
- Tài khoản local: [database/tai-khoan-mau.md](../../../database/tai-khoan-mau.md).

<a id="section-1"></a>

Chạy từ thư mục gốc, dùng database phát triển cho bộ HTTP:

```powershell
mvn -B -f apps/backend/pom.xml verify
npm.cmd run build
python tests/backend/run_integration_audit.py --only employees vehicles trip-sale
```

<a id="section-2"></a>
<a id="section-3"></a>
<a id="section-4"></a>
<a id="section-5"></a>
<a id="section-6"></a>

Web: http://localhost:3000/quan-tri/nhan-vien; lịch cá nhân: http://localhost:3000/nhan-vien.
Chỉ ghi Pass khi đã chạy và có bằng chứng; test bỏ qua/chưa chạy không tính đạt.
