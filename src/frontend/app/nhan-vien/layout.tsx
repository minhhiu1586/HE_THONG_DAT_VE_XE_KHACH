import EmployeeShell from '@/components/Employees/EmployeeShell';

export const metadata = {
  title: 'Lịch chuyến – Nhân viên | Vexexin',
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return <EmployeeShell>{children}</EmployeeShell>;
}
