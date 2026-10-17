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
mvn -B -f apps/backend/pom.xml verify
python tests/use-cases/check_matrix.py
```

Từ thư mục gốc repository:

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

<a id="git-va-push"></a>

## Git và push

Các mục dưới đây được gộp từ tài liệu hiện có; kết quả kiểm thử lịch sử giữ nguyên thời điểm và giới hạn của lần chạy.

## Mục lục

- [Git workflow](#section-1)
- [Huong dan push repo len GitHub](#section-2)

<a id="section-1"></a>

## Git workflow

### Nhanh chinh

- `main`: chứa phiên bản ổn định, dùng để nộp bài hoặc demo.
- `develop`: chứa phiên bản đang tích hợp trong quá trình phát triển.
- `feature/*`: nhánh chức năng của từng thành viên.

### Quy trinh

```bash
git checkout develop
git pull origin develop
git checkout -b feature/ten-chuc-nang
```

Sau khi hoàn thành:

```bash
git add .
git commit -m "feat: mo ta ngan gon chuc nang"
git push -u origin feature/ten-chuc-nang
```

Tạo Pull Request từ `feature/ten-chuc-nang` vào `develop`.

### Luu y

- Không commit trực tiếp lên `main`.
- Không push file `.env` thật.
- Pull code mới từ `develop` trước khi tạo nhánh mới.
- Commit nhỏ, rõ ý, dễ review.

<a id="section-2"></a>

## Huong dan push repo len GitHub

Tài liệu này dùng cho lần đưa repo `HE_THONG_DAT_VE_XE_KHACH` lên GitHub và thiết lập workflow làm việc nhóm.

### 1. Kiem tra Git

Mở terminal tại thư mục repo:

```bash
cd "D:\HK1 - 2026.2027\web-deb\Web-Mai Anh Tho\HE_THONG_DAT_VE_XE_KHACH"
```

Kiểm tra trạng thái:

```bash
git status
```

Nếu Git báo lỗi `detected dubious ownership`, chạy lệnh sau một lần:

```bash
git config --global --add safe.directory "D:/HK1 - 2026.2027/web-deb/Web-Mai Anh Tho/HE_THONG_DAT_VE_XE_KHACH"
```

Sau đó kiểm tra lại:

```bash
git status
```

### 2. Commit lan dau

```bash
git add .
git commit -m "chore: initialize project repository"
```

### 3. Tao repo tren GitHub

Vào GitHub và tạo repository mới:

```text
HE_THONG_DAT_VE_XE_KHACH
```

Khi tạo repo, không chọn tạo sẵn:

- `README.md`
- `.gitignore`
- License

Vì các file này đã có ở repo local.

### 4. Gan remote GitHub

Thay `YOUR_USERNAME` bằng username hoặc organization của nhóm:

```bash
git remote add origin https://github.com/YOUR_USERNAME/HE_THONG_DAT_VE_XE_KHACH.git
```

Kiểm tra remote:

```bash
git remote -v
```

### 5. Push nhanh main

```bash
git branch -M main
git push -u origin main
```

### 6. Tao nhanh develop

```bash
git checkout -b develop
git push -u origin develop
```

### 7. Tao nhanh lam viec cho tung thanh vien

Ví dụ làm chức năng đặt vé:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/booking
```

Sau khi code xong:

```bash
git add .
git commit -m "feat: implement ticket booking"
git push -u origin feature/booking
```

Sau đó lên GitHub tạo Pull Request từ:

```text
feature/booking -> develop
```

Khi `develop` ổn định, tạo Pull Request:

```text
develop -> main
```

### 8. Goi y mo ta repo tren GitHub

Phần About có thể ghi:

```text
Hệ thống đặt vé xe khách trực tuyến hỗ trợ tìm kiếm chuyến xe, chọn ghế, đặt vé, thanh toán và quản lý hoạt động nhà xe.
```

Hoặc bản tiếng Anh:

```text
Online bus ticket booking and bus operator management system.
```
