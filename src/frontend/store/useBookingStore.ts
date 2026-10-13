"use client";

import { useCallback, useSyncExternalStore } from "react";

import type { Passenger, TripDetail } from "../types";

type State = {
  trip?: TripDetail;
  selectedSeats: string[];
  passenger?: Passenger;
  orderId?: string;
  expiredAt?: string;
};

let state: State = { selectedSeats: [] };
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((listener) => listener());

export const bookingStore = {
  get: () => state,
  set: (patch: Partial<State>) => {
    state = { ...state, ...patch };
    emit();
  },
  toggleSeat: (id: string) => {
    state = {
      ...state,
      selectedSeats: state.selectedSeats.includes(id)
        ? state.selectedSeats.filter((seat) => seat !== id)
        : [...state.selectedSeats, id],
    };
    emit();
  },
  setHoldSession: (orderId: string, expiredAt: string) => {
    state = { ...state, orderId, expiredAt };
    emit();
  },
  clear: () => {
    state = { selectedSeats: [] };
    emit();
  },
};

export function useBookingStore() {
  const subscribe = useCallback((listener: () => void) => {
    listeners.add(listener);
    return () => listeners.delete(listener);
  }, []);

  return useSyncExternalStore(subscribe, bookingStore.get, bookingStore.get);
}
