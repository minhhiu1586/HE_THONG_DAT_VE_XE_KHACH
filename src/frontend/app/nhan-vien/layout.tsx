import EmployeeShell from '@/components/Employees/EmployeeShell';

export const metadata = {
  title: 'Không gian nhân viên | Vexexin',
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return <EmployeeShell>{children}</EmployeeShell>;
}
