// ============================================
// Mock Data — Hệ thống đặt vé xe khách
// ============================================

// --- Locations & Stations ---
export interface Station {
  id: string;
  name: string;
  address: string;
}

export interface Location {
  id: string;
  name: string;
  stations: Station[];
}

export const locations: Location[] = [
  {
    id: 'hanoi',
    name: 'Hà Nội',
    stations: [
      { id: 'mydinh', name: 'Bến xe Mỹ Đình', address: '20 Phạm Hùng, Nam Từ Liêm, Hà Nội' },
      { id: 'giapbat', name: 'Bến xe Giáp Bát', address: '553 Giải Phóng, Hoàng Mai, Hà Nội' },
      { id: 'gialam', name: 'Bến xe Gia Lâm', address: '1 Ngô Gia Khảm, Long Biên, Hà Nội' },
      { id: 'nuocngam', name: 'Bến xe Nước Ngầm', address: 'Pháp Vân, Hoàng Mai, Hà Nội' },
    ],
  },
  {
    id: 'hcm',
    name: 'TP. Hồ Chí Minh',
    stations: [
      { id: 'miendong', name: 'Bến xe Miền Đông', address: '292 Đinh Bộ Lĩnh, Bình Thạnh, TP.HCM' },
      { id: 'mientay', name: 'Bến xe Miền Tây', address: '395 Kinh Dương Vương, Bình Tân, TP.HCM' },
    ],
  },
  {
    id: 'danang',
    name: 'Đà Nẵng',
    stations: [
      { id: 'danang-st', name: 'Bến xe Đà Nẵng', address: '33 Điện Biên Phủ, Thanh Khê, Đà Nẵng' },
    ],
  },
  {
    id: 'haiphong',
    name: 'Hải Phòng',
    stations: [
      { id: 'niemnghia', name: 'Bến xe Niệm Nghĩa', address: 'Trần Nguyên Hãn, Lê Chân, Hải Phòng' },
    ],
  },
  {
    id: 'sapa',
    name: 'Sapa',
    stations: [
      { id: 'sapa-st', name: 'Bến xe Sapa', address: 'TT. Sa Pa, Lào Cai' },
    ],
  },
  {
    id: 'dalat',
    name: 'Đà Lạt',
    stations: [
      { id: 'dalat-st', name: 'Bến xe Đà Lạt', address: '1 Tô Hiến Thành, Phường 6, Đà Lạt' },
    ],
  },
  {
    id: 'nhatrang',
    name: 'Nha Trang',
    stations: [
      { id: 'phiahnam', name: 'Bến xe phía Nam Nha Trang', address: 'Lê Hồng Phong, Nha Trang' },
    ],
  },
  {
    id: 'hue',
    name: 'Huế',
    stations: [
      { id: 'hue-st', name: 'Bến xe Huế', address: '97 An Dương Vương, Huế' },
    ],
  },
  {
    id: 'cantho',
    name: 'Cần Thơ',
    stations: [
      { id: 'cantho-st', name: 'Bến xe Cần Thơ', address: 'Nguyễn Trãi, Ninh Kiều, Cần Thơ' },
    ],
  },
  {
    id: 'vungtau',
    name: 'Vũng Tàu',
    stations: [
      { id: 'vungtau-st', name: 'Bến xe Vũng Tàu', address: '192 Nam Kỳ Khởi Nghĩa, Vũng Tàu' },
    ],
  },
];

// --- Bus Companies ---
export interface BusCompany {
  id: string;
  name: string;
  logo: string;
  rating: number;
  totalTrips: number;
}

export const busCompanies: BusCompany[] = [
  { id: 'hoanglong', name: 'Hoàng Long', logo: '🚌', rating: 4.5, totalTrips: 15000 },
  { id: 'phuongtrang', name: 'Phương Trang (FUTA)', logo: '🚍', rating: 4.3, totalTrips: 25000 },
  { id: 'thanhbuoi', name: 'Thành Bưởi', logo: '🚎', rating: 4.2, totalTrips: 12000 },
  { id: 'saoviet', name: 'Sao Việt', logo: '🚐', rating: 4.0, totalTrips: 8000 },
  { id: 'kumho', name: 'Kumho Samco', logo: '🚌', rating: 4.4, totalTrips: 10000 },
  { id: 'camel', name: 'Camel Travel', logo: '🚍', rating: 4.6, totalTrips: 5000 },
  { id: 'thesinhtourist', name: 'The Sinh Tourist', logo: '🚎', rating: 4.1, totalTrips: 9000 },
];

// --- Trip Types ---
export type VehicleType = 'sleeper' | 'seat' | 'limousine';

export interface Trip {
  id: string;
  companyId: string;
  company: BusCompany;
  from: Location;
  to: Location;
  fromStation: Station;
  toStation: Station;
  departureTime: string;
  arrivalTime: string;
  duration: string;
  vehicleType: VehicleType;
  vehicleLabel: string;
  totalSeats: number;
  availableSeats: number;
  price: number;
  originalPrice?: number;
  amenities: string[];
  rating: number;
  reviewCount: number;
}

// --- Seat Types ---
export type SeatStatus = 'available' | 'selected' | 'booked';

export interface Seat {
  id: string;
  label: string;
  floor?: number; // 1 or 2 for sleeper buses
  row: number;
  col: number;
  status: SeatStatus;
  price: number;
}

// --- Popular Routes ---
export interface PopularRoute {
  id: string;
  from: string;
  to: string;
  image: string;
  price: number;
  originalPrice: number;
}

export const popularRoutes: PopularRoute[] = [
  { id: '1', from: 'Hà Nội', to: 'Sapa', image: '/images/sapa.jpg', price: 195000, originalPrice: 230000 },
  { id: '2', from: 'Hà Nội', to: 'Hải Phòng', image: '/images/haiphong.jpg', price: 130000, originalPrice: 150000 },
  { id: '3', from: 'Sài Gòn', to: 'Đà Lạt', image: '/images/dalat.jpg', price: 200000, originalPrice: 250000 },
  { id: '4', from: 'Sài Gòn', to: 'Nha Trang', image: '/images/nhatrang.jpg', price: 249000, originalPrice: 300000 },
  { id: '5', from: 'Hà Nội', to: 'Đà Nẵng', image: '/images/danang.jpg', price: 350000, originalPrice: 420000 },
  { id: '6', from: 'Sài Gòn', to: 'Vũng Tàu', image: '/images/vungtau.jpg', price: 120000, originalPrice: 150000 },
];

// --- Featured Deals ---
export interface Deal {
  id: string;
  title: string;
  description: string;
  image: string;
  bgColor: string;
}

export const featuredDeals: Deal[] = [
  { id: '1', title: 'Ngày đôi giá tốt', description: 'Chốt vé siêu hời cùng VeXe', image: '🎫', bgColor: '#FFF3E0' },
  { id: '2', title: 'Flash Sale Thứ 3', description: 'Giảm đến 50% mỗi thứ 3 hàng tuần', image: '⚡', bgColor: '#E3F2FD' },
  { id: '3', title: 'Nhà xe mới', description: 'Giảm 20% khi đặt vé nhà xe mới', image: '🆕', bgColor: '#E8F5E9' },
  { id: '4', title: 'Giới thiệu bạn mới', description: 'Nhận quà khủng từ VeXe', image: '🎁', bgColor: '#F3E5F5' },
];

// --- Promo Codes ---
export interface PromoCode {
  code: string;
  description: string;
  discountType: 'percent' | 'fixed';
  discountValue: number;
  maxDiscount?: number;
  minOrder?: number;
  isActive: boolean;
}

export const promoCodes: PromoCode[] = [
  { code: 'GIAM10', description: 'Giảm 10% cho đơn đầu tiên', discountType: 'percent', discountValue: 10, maxDiscount: 50000, minOrder: 100000, isActive: true },
  { code: 'MOIVE50K', description: 'Giảm 50K cho đơn từ 200K', discountType: 'fixed', discountValue: 50000, minOrder: 200000, isActive: true },
  { code: 'NEWUSER', description: 'Giảm 15% cho người dùng mới', discountType: 'percent', discountValue: 15, maxDiscount: 80000, minOrder: 150000, isActive: true },
  { code: 'FLASHSALE', description: 'Giảm 30K Flash Sale', discountType: 'fixed', discountValue: 30000, minOrder: 100000, isActive: true },
  { code: 'EXPIRED01', description: 'Mã đã hết hạn', discountType: 'percent', discountValue: 20, isActive: false },
];

// --- Platform Features ---
export interface PlatformFeature {
  icon: string;
  title: string;
  description: string;
}

export const platformFeatures: PlatformFeature[] = [
  { icon: '🚌', title: '2000+ nhà xe chất lượng cao', description: '5000+ tuyến đường trên toàn quốc, chủ động và đa dạng lựa chọn.' },
  { icon: '📱', title: 'Đặt xe dễ dàng', description: 'Đặt chỗ vé nhanh. Chọn xe yêu thích cực nhanh và thuận tiện.' },
  { icon: '✅', title: 'Chắc chắn có chỗ', description: 'Hoàn ngay 150% nếu nhà xe không cung cấp dịch vụ vận chuyển.' },
  { icon: '🎉', title: 'Nhiều ưu đãi', description: 'Hàng ngàn ưu đãi cực chất độc quyền tại VeXe.' },
];

// --- Media Mentions ---
export const mediaMentions = [
  { name: 'VnExpress', logo: '📰' },
  { name: 'VTV', logo: '📺' },
  { name: 'Dân Trí', logo: '📋' },
  { name: 'Tuổi Trẻ', logo: '📄' },
  { name: 'FBNC', logo: '💼' },
];

// --- Bus Stations (with images) ---
export const busStationCards = [
  { id: '1', name: 'Bến xe Miền Đông', image: '/images/benxe-miendong.jpg', city: 'TP.HCM' },
  { id: '2', name: 'Bến xe Gia Lâm', image: '/images/benxe-gialam.jpg', city: 'Hà Nội' },
  { id: '3', name: 'Bến xe Nước Ngầm', image: '/images/benxe-nuocngam.jpg', city: 'Hà Nội' },
  { id: '4', name: 'Bến xe Mỹ Đình', image: '/images/benxe-mydinh.jpg', city: 'Hà Nội' },
];

// --- Generate mock trips ---
export function generateMockTrips(fromId: string, toId: string, date: string): Trip[] {
  const from = locations.find(l => l.id === fromId) || locations[0];
  const to = locations.find(l => l.id === toId) || locations[1];

  const tripTemplates: Omit<Trip, 'id' | 'from' | 'to' | 'fromStation' | 'toStation'>[] = [
    {
      companyId: 'hoanglong',
      company: busCompanies[0],
      departureTime: '06:00',
      arrivalTime: '12:30',
      duration: '6h30',
      vehicleType: 'sleeper',
      vehicleLabel: 'Giường nằm 40 chỗ',
      totalSeats: 40,
      availableSeats: 15,
      price: 350000,
      originalPrice: 420000,
      amenities: ['WiFi', 'Nước uống', 'Khăn lạnh', 'Điều hòa'],
      rating: 4.5,
      reviewCount: 234,
    },
    {
      companyId: 'phuongtrang',
      company: busCompanies[1],
      departureTime: '08:00',
      arrivalTime: '15:00',
      duration: '7h00',
      vehicleType: 'seat',
      vehicleLabel: 'Ghế ngồi 45 chỗ',
      totalSeats: 45,
      availableSeats: 22,
      price: 280000,
      amenities: ['Điều hòa', 'Nước uống', 'TV'],
      rating: 4.3,
      reviewCount: 456,
    },
    {
      companyId: 'thanhbuoi',
      company: busCompanies[2],
      departureTime: '10:30',
      arrivalTime: '17:00',
      duration: '6h30',
      vehicleType: 'sleeper',
      vehicleLabel: 'Giường nằm 40 chỗ',
      totalSeats: 40,
      availableSeats: 8,
      price: 320000,
      originalPrice: 380000,
      amenities: ['WiFi', 'Nước uống', 'Chăn', 'Điều hòa', 'Sạc USB'],
      rating: 4.2,
      reviewCount: 189,
    },
    {
      companyId: 'kumho',
      company: busCompanies[4],
      departureTime: '13:00',
      arrivalTime: '19:30',
      duration: '6h30',
      vehicleType: 'limousine',
      vehicleLabel: 'Limousine 22 chỗ',
      totalSeats: 22,
      availableSeats: 5,
      price: 480000,
      amenities: ['WiFi', 'Nước uống', 'Snack', 'Điều hòa', 'Ghế massage', 'Sạc USB'],
      rating: 4.7,
      reviewCount: 312,
    },
    {
      companyId: 'camel',
      company: busCompanies[5],
      departureTime: '15:00',
      arrivalTime: '21:30',
      duration: '6h30',
      vehicleType: 'sleeper',
      vehicleLabel: 'Giường nằm 40 chỗ',
      totalSeats: 40,
      availableSeats: 18,
      price: 300000,
      amenities: ['WiFi', 'Nước uống', 'Khăn lạnh', 'Điều hòa'],
      rating: 4.6,
      reviewCount: 167,
    },
    {
      companyId: 'saoviet',
      company: busCompanies[3],
      departureTime: '18:00',
      arrivalTime: '00:30',
      duration: '6h30',
      vehicleType: 'seat',
      vehicleLabel: 'Ghế ngồi 45 chỗ',
      totalSeats: 45,
      availableSeats: 30,
      price: 250000,
      amenities: ['Điều hòa', 'Nước uống'],
      rating: 4.0,
      reviewCount: 98,
    },
    {
      companyId: 'thesinhtourist',
      company: busCompanies[6],
      departureTime: '20:00',
      arrivalTime: '02:30',
      duration: '6h30',
      vehicleType: 'sleeper',
      vehicleLabel: 'Giường nằm 40 chỗ',
      totalSeats: 40,
      availableSeats: 12,
      price: 340000,
      originalPrice: 400000,
      amenities: ['WiFi', 'Nước uống', 'Chăn', 'Điều hòa', 'Gối'],
      rating: 4.1,
      reviewCount: 276,
    },
    {
      companyId: 'phuongtrang',
      company: busCompanies[1],
      departureTime: '22:00',
      arrivalTime: '04:30',
      duration: '6h30',
      vehicleType: 'limousine',
      vehicleLabel: 'Limousine VIP 9 chỗ',
      totalSeats: 9,
      availableSeats: 3,
      price: 550000,
      amenities: ['WiFi', 'Nước uống', 'Snack', 'Điều hòa', 'Ghế massage', 'Sạc USB', 'Rèm riêng'],
      rating: 4.8,
      reviewCount: 89,
    },
  ];

  return tripTemplates.map((t, index) => {
    // Lấy xoay vòng các bến xe hiện có của địa điểm
    const currentFromStation = from.stations[index % from.stations.length];
    const currentToStation = to.stations[index % to.stations.length];

    return {
      ...t,
      id: `trip-${fromId}-${toId}-${index + 1}`,
      from,
      to,
      fromStation: currentFromStation,
      toStation: currentToStation,
    };
  });
}


// --- Generate Seat Map ---
export function generateSeatMap(vehicleType: VehicleType, totalSeats: number): Seat[] {
  const seats: Seat[] = [];
  const bookedPercentage = 0.3;

  if (vehicleType === 'sleeper') {
    // 2 floors, 20 seats each (3 columns: left-2, aisle, right-1 pattern)
    for (let floor = 1; floor <= 2; floor++) {
      let seatIndex = 0;
      for (let row = 0; row < 7; row++) {
        for (let col = 0; col < 3; col++) {
          if (seatIndex >= 20) break;
          seatIndex++;
          const seatNum = (floor - 1) * 20 + seatIndex;
          const label = `${floor === 1 ? 'A' : 'B'}${seatNum.toString().padStart(2, '0')}`;
          seats.push({
            id: `seat-${label}`,
            label,
            floor,
            row,
            col,
            status: Math.random() < bookedPercentage ? 'booked' : 'available',
            price: floor === 2 ? 350000 : 320000,
          });
        }
      }
    }
  } else if (vehicleType === 'seat') {
    // Standard bus layout: 2-2 (4 columns, ~12 rows)
    const rows = Math.ceil(totalSeats / 4);
    let seatNum = 0;
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < 4; col++) {
        seatNum++;
        if (seatNum > totalSeats) break;
        const label = `${String.fromCharCode(65 + col)}${row + 1}`;
        seats.push({
          id: `seat-${label}`,
          label,
          row,
          col,
          status: Math.random() < bookedPercentage ? 'booked' : 'available',
          price: 280000,
        });
      }
    }
  } else {
    // Limousine: 2 columns, spread layout
    const rows = Math.ceil(totalSeats / 2);
    let seatNum = 0;
    for (let row = 0; row < rows; row++) {
      for (let col = 0; col < 2; col++) {
        seatNum++;
        if (seatNum > totalSeats) break;
        const label = `VIP${seatNum.toString().padStart(2, '0')}`;
        seats.push({
          id: `seat-${label}`,
          label,
          row,
          col,
          status: Math.random() < bookedPercentage ? 'booked' : 'available',
          price: 480000,
        });
      }
    }
  }

  return seats;
}

// --- Booking ---
export interface BookingInfo {
  tripId: string;
  trip: Trip;
  seats: Seat[];
  passenger: {
    fullName: string;
    phone: string;
    email: string;
  };
  pickupPoint: string;
  dropoffPoint: string;
  promoCode?: string;
  discount: number;
  totalPrice: number;
  bookingCode?: string;
}

// --- Format helpers ---
export function formatPrice(price: number): string {
  return price.toLocaleString('vi-VN') + 'đ';
}

export function formatDate(dateStr: string): string {
  const date = new Date(dateStr);
  const days = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
  return `${days[date.getDay()]}, ${date.getDate().toString().padStart(2, '0')}/${(date.getMonth() + 1).toString().padStart(2, '0')}/${date.getFullYear()}`;
}

export function generateBookingCode(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let code = 'VX';
  for (let i = 0; i < 8; i++) {
    code += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return code;
}
