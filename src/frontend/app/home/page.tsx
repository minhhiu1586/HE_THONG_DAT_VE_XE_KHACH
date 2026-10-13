"use client";

import { useRouter } from "next/navigation";

import SearchForm from "../../components/booking/SearchForm";
import PopularRoutes from "../../components/ui/PopularRoutes";
import BusStations from "../../components/ui/BusStations";
import type { SearchValues } from "../../types";

export default function Home() {
  const router = useRouter();

  const submit = (values: SearchValues) => router.push(`/trips?from=${encodeURIComponent(values.from)}&to=${encodeURIComponent(values.to)}&date=${values.date}`);

  return (
    <main>
      <section className="hero">
        <div className="hero-copy">
          <span className="eyebrow">
            HÀNH TRÌNH CỦA BẠN, BẮT ĐẦU TỪ ĐÂY
          </span>
          <h1>
            Đi xa hơn,
            <br />
            <em>trọn niềm vui.</em>
          </h1>
          <p>
            Tìm kiếm và đặt vé xe khách dễ dàng, an toàn
            <br />
            với hàng nghìn chuyến xe mỗi ngày.
          </p>
        </div>
        <div className="hero-art">
          <div className="sun" />
          <div className="road" />
          <span className="art-label">
            CHỦ ĐỘNG
            <br />
            MỌI HÀNH TRÌNH
          </span>
        </div>
      </section>
      <section className="search-wrap">
        <div className="section-heading">
          <span className="eyebrow">TÌM KIẾM CHUYẾN XE</span>
          <h2>Bạn muốn đi đâu hôm nay?</h2>
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