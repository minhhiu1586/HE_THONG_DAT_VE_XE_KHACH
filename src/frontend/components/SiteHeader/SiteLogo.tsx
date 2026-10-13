import Image from 'next/image';
import siteLogo from '@/public/images/site-logo.jpg';

export default function SiteLogo() {
  return <>
    <Image className="site-logo" src={siteLogo} alt="" width={48} height={48} sizes="48px" />
    <span>Vexexin<span className="brand-dot">.</span></span>
  </>;
}
