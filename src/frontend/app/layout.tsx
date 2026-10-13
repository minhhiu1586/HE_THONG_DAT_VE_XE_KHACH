import "./globals.css";

import Link from "next/link";
import { AuthProvider } from "@/components/Account/AuthProvider";
import AccountActions from "@/components/Account/AccountActions";

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body>
        <AuthProvider>
        <header className="topbar">
          <Link className="brand" href="/home">
            <span className="brand-mark">V</span>
            <span>
              Vexere<span className="brand-dot">.</span>
            </span>
          </Link>
          <nav>
            <Link href="/home">Trang chủ</Link>
            <Link href="/trips">Tìm chuyến</Link>
            <a href="#support">Hỗ trợ</a>
          </nav>
          <AccountActions />
        </header>
        {children}
        <footer id="support">
          <div>
            <strong>Vexere.</strong>
            <p>Đặt vé nhanh chóng, an tâm trên mọi hành trình.</p>
          </div>
          <div>
            <strong>Hỗ trợ</strong>
            <p>Trung tâm trợ giúp · Chính sách hoàn vé</p>
          </div>
          <div>
            <strong>Hotline</strong>
            <p className="hotline">1900 6067</p>
          </div>
        </footer>
        </AuthProvider>
      </body>
    </html>
  );
}
