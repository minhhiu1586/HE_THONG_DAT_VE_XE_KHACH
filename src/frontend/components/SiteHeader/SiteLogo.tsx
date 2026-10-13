import Image from 'next/image';

export default function SiteLogo() {
  return <>
    <Image className="site-logo" src="/images/site-logo.jpg" alt="" width={48} height={48} />
    <span>Vexere<span className="brand-dot">.</span></span>
  </>;
}
