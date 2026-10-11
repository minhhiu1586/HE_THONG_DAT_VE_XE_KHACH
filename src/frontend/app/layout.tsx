import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "../components/Account/AuthProvider";
import Header from "../components/Header/Header";
import Footer from "../components/Footer/Footer";

export const metadata: Metadata = {
  title: "VeXe - Hệ thống đặt vé xe khách trực tuyến",
  description: "Hệ thống đặt vé xe khách trực tuyến hàng đầu Việt Nam. Hàng ngàn tuyến đường, chắc chắn có chỗ, đa dạng thanh toán.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="vi">
      <body>
        <AuthProvider>
        <Header />
        <main style={{ minHeight: "calc(100vh - 300px)" }}>
          {children}
        </main>
        <Footer />
        </AuthProvider>
      </body>
    </html>
  );
}