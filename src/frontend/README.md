This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.

## T07 — Giao diện tài khoản

Các trang `/dang-ky`, `/dang-nhap`, `/tai-khoan`, `/quan-tri/tai-khoan` gọi backend thật qua `/api`. Chạy backend và migration 003 trước; `BACKEND_URL` mặc định `http://127.0.0.1:8080`, đặt biến này trước `npm run build` nếu dùng host khác. Backend đặt `FRONTEND_ORIGIN` theo URL frontend. Production HTTPS đặt `AUTH_COOKIE_SECURE=true`.

AuthProvider giữ hồ sơ và CSRF trong bộ nhớ; cookie phiên HttpOnly do backend quản lý. 401 kết thúc trạng thái đăng nhập; 403 hiện thông báo thiếu quyền. Xem [hợp đồng](../../docs/api/account-auth.md), [hướng dẫn phân quyền](../../docs/tasks/T07.1-integration.md), [ma trận kiểm thử](../../docs/tasks/T07-test-matrix.md).
