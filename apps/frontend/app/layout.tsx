import '@/app/globals.css';
import { Inter } from 'next/font/google';
import { AuthProvider } from '@members/NguyenMinhHieu/account/components/AuthProvider';
import ZoneShell from '@shared/components/ZoneShell';

const inter = Inter({
  subsets: ['latin', 'vietnamese'],
  variable: '--font-inter',
  display: 'swap',
  weight: ['400', '500', '600', '700', '800', '900'],
});

export const metadata = {
  title: 'Vexexin – Đặt vé xe khách trực tuyến',
  description: 'Tìm kiếm và đặt vé xe khách dễ dàng, an toàn với hàng nghìn chuyến xe mỗi ngày.',
  keywords: 'đặt vé xe, xe khách, vé xe online, vexexin',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi" className={inter.variable}>
      <body className={inter.className}>
        <AuthProvider>
          <ZoneShell>
            {children}
          </ZoneShell>
        </AuthProvider>
      </body>
    </html>
  );
}
