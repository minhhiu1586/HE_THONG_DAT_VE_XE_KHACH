"use client";

import { useRouter } from "next/navigation";
import Image from "next/image";
import homeBanner from "@/public/images/home-banner.jpg";

import SearchForm from "@members/NguyenTheAn/booking/components/SearchForm";
import PopularRoutes from "@members/NguyenTheAn/trip-search/components/PopularRoutes";
import BusStations from "@members/NguyenTheAn/trip-search/components/BusStations";
import type { SearchValues } from "@shared/types/index";

export default function Home() {
  const router = useRouter();

  const submit = (values: SearchValues) => router.push(`/trips?from=${encodeURIComponent(values.from)}&to=${encodeURIComponent(values.to)}&date=${values.date}`);

  return (
    <main>
      <section className="hero-banner" aria-label="Banner giới thiệu Vexexin">
        <Image src={homeBanner} alt="Vexexin – banner giới thiệu dịch vụ di chuyển" priority unoptimized sizes="100vw" />
      </section>
      <section className="search-wrap">
        <div className="section-heading">
          <span className="eyebrow">TÌM KIẾM CHUYẾN XE</span>
          <h1>Bạn muốn đi đâu hôm nay?</h1>
        </div>
        <SearchForm onSubmit={submit} />
      </section>

      {/* Popular Routes Carousel */}
      <PopularRoutes />

      {/* Bus Stations Carousel */}
      <BusStations />

      <section className="benefits">
        <div>
          <span>01</span>
          <h3>An toàn & Tiện lợi</h3>
          <p>Đặt vé nhanh chóng, thanh toán an toàn với nhiều phương thức.</p>
        </div>
        <div>
          <span>02</span>
          <h3>Đa dạng lựa chọn</h3>
          <p>Hàng nghìn chuyến xe mỗi ngày từ các nhà xe uy tín.</p>
        </div>
        <div>
          <span>03</span>
          <h3>Giá tốt mỗi ngày</h3>
          <p>Nhiều ưu đãi hấp dẫn dành riêng cho bạn.</p>
        </div>
      </section>
    </main>
  );
}