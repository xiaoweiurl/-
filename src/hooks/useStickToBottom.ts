'use client';

import { useCallback, useRef } from 'react';
import {
  STICK_BOTTOM_THRESHOLD_PX,
  createStickState,
  distanceFromBottom,
  finishUserInteraction,
  nestedScrollerConsumesWheel,
  onScrollerScroll,
  onUserWheel,
  pinScrollTop,
  shouldPin,
  type StickState,
} from '@/lib/stick-to-bottom';

/**
 * Keep a chat message list pinned to its own bottom while a reply streams.
 * Scrolling up pauses that pin until the user returns near the bottom.
 * The page and the composer are not scrolled.
 */
export function useStickToBottom() {
  const nodeRef = useRef<HTMLDivElement | null>(null);
  const stateRef = useRef<StickState>(createStickState());
  const detachRef = useRef<(() => void) | null>(null);

  const pin = useCallback((force = false) => {
    const node = nodeRef.current;
    if (!node) return;
    if (force) {
      stateRef.current = { following: true, wheelDown: false, userInteracting: false };
    }
    if (!shouldPin(stateRef.current, force)) return;
    pinScrollTop(node);
  }, []);

  const scrollerRef = useCallback((node: HTMLDivElement | null) => {
    detachRef.current?.();
    detachRef.current = null;
    nodeRef.current = node;
    if (!node) return;

    const onWheel = (event: WheelEvent) => {
      stateRef.current = onUserWheel(stateRef.current, {
        deltaY: event.deltaY,
        scrollHeight: node.scrollHeight,
        clientHeight: node.clientHeight,
        nestedCanConsume: nestedScrollerConsumesWheel(event.target, node, event.deltaY),
      });
    };

    const onScroll = () => {
      stateRef.current = onScrollerScroll(
        stateRef.current,
        distanceFromBottom(node),
        STICK_BOTTOM_THRESHOLD_PX,
      );
    };

    let gestureScrollTop = node.scrollTop;

    const onPointerDown = () => {
      gestureScrollTop = node.scrollTop;
      stateRef.current = { ...stateRef.current, userInteracting: true };
    };

    const finishGesture = () => {
      const next = finishUserInteraction(stateRef.current, {
        startScrollTop: gestureScrollTop,
        scrollTop: node.scrollTop,
        distance: distanceFromBottom(node),
      });
      const catchUp = stateRef.current.userInteracting && next.following;
      stateRef.current = next;
      if (catchUp) pin(false);
    };

    let touchY = 0;
    const onTouchStart = (event: TouchEvent) => {
      touchY = event.touches[0]?.clientY ?? 0;
      gestureScrollTop = node.scrollTop;
      stateRef.current = { ...stateRef.current, userInteracting: true };
    };

    const onTouchMove = (event: TouchEvent) => {
      const y = event.touches[0]?.clientY ?? touchY;
      const fingerDelta = y - touchY;
      touchY = y;
      if (Math.abs(fingerDelta) < 2) return;
      stateRef.current = onUserWheel(stateRef.current, {
        deltaY: -fingerDelta,
        scrollHeight: node.scrollHeight,
        clientHeight: node.clientHeight,
        nestedCanConsume: nestedScrollerConsumesWheel(event.target, node, -fingerDelta),
      });
    };

    node.addEventListener('wheel', onWheel, { passive: true });
    node.addEventListener('scroll', onScroll, { passive: true });
    node.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('pointerup', finishGesture);
    window.addEventListener('pointercancel', finishGesture);
    node.addEventListener('touchstart', onTouchStart, { passive: true });
    node.addEventListener('touchmove', onTouchMove, { passive: true });
    node.addEventListener('touchend', finishGesture);
    node.addEventListener('touchcancel', finishGesture);

    const resizeObserver = new ResizeObserver(() => pin(false));
    const watchChildren = () => {
      resizeObserver.disconnect();
      for (const child of Array.from(node.children)) resizeObserver.observe(child);
    };
    watchChildren();
    const mutationObserver = new MutationObserver(watchChildren);
    mutationObserver.observe(node, { childList: true });
    pin(false);

    const detach = () => {
      if (detachRef.current === detach) detachRef.current = null;
      node.removeEventListener('wheel', onWheel);
      node.removeEventListener('scroll', onScroll);
      node.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('pointerup', finishGesture);
      window.removeEventListener('pointercancel', finishGesture);
      node.removeEventListener('touchstart', onTouchStart);
      node.removeEventListener('touchmove', onTouchMove);
      node.removeEventListener('touchend', finishGesture);
      node.removeEventListener('touchcancel', finishGesture);
      resizeObserver.disconnect();
      mutationObserver.disconnect();
      if (nodeRef.current === node) nodeRef.current = null;
    };
    detachRef.current = detach;
    return detach;
  }, [pin]);

  const resumeFollow = useCallback(() => {
    pin(true);
  }, [pin]);

  return { scrollerRef, resumeFollow };
}
