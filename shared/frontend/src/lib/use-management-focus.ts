'use client';

import { useCallback, useRef } from 'react';

/** Scrolls a newly selected management form/detail into view and gives it keyboard focus. */
export function useManagementFocus<T extends HTMLElement = HTMLElement>() {
  const targetElement = useRef<T | null>(null);
  const attachTarget = useCallback((element: T | null) => {
    targetElement.current = element;
  }, []);

  const focusTarget = useCallback((selector?: string) => {
    window.requestAnimationFrame(() => {
      const target = targetElement.current;
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

  return [attachTarget, focusTarget] as const;
}
