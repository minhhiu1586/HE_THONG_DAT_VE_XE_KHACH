import ManagementLayout from '@shared/components/Management/ManagementLayout';

export default function Layout({ children }: { children: React.ReactNode }) {
  return <ManagementLayout>{children}</ManagementLayout>;
}
