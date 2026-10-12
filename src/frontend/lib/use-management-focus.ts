'use client';

import { useCallback, useRef } from 'react';

/** Scrolls a newly selected management form/detail into view and gives it keyboard focus. */
export function useManagementFocus<T extends HTMLElement = HTMLElement>() {
  const targetRef = useRef<T>(null);

  const focusTarget = useCallback((selector?: string) => {
    window.requestAnimationFrame(() => {
      const target = targetRef.current;
      if (!target) return;

      const focusable = selector
        ? target.querySelector<HTMLElement>(selector)
        : target.querySelector<HTMLElement>('[data-management-focus]') ?? target;
      target.scrollIntoView({
        behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
        block: 'center',
      });
      focusable?.focus({ preventScroll: true });
    });
  }, []);

  return { targetRef, focusTarget };
}
