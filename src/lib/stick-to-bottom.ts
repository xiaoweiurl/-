/** How close to the bottom (px) still counts as following the stream. */
export const STICK_BOTTOM_THRESHOLD_PX = 80;

export interface StickState {
  /** When true, new content pins the message list to its own bottom. */
  following: boolean;
  /** A downward wheel happened; the next scroll event may resume following. */
  wheelDown: boolean;
  /** Pointer or touch is down, so a scrollbar drag owns the position. */
  userInteracting: boolean;
}

export function createStickState(): StickState {
  return { following: true, wheelDown: false, userInteracting: false };
}

export function distanceFromBottom(metrics: {
  scrollHeight: number;
  scrollTop: number;
  clientHeight: number;
}): number {
  return metrics.scrollHeight - metrics.scrollTop - metrics.clientHeight;
}

export function isNearBottom(distance: number, threshold = STICK_BOTTOM_THRESHOLD_PX): boolean {
  return distance <= threshold;
}

/**
 * Wheel / trackpad intent.
 * Returns the next stick state. Programmatic scrolls must not call this.
 * An upward wheel pauses following only once the list can actually move.
 * A downward wheel waits for the resulting scroll position before resuming.
 */
export function onUserWheel(
  state: StickState,
  input: {
    deltaY: number;
    scrollHeight: number;
    clientHeight: number;
    nestedCanConsume: boolean;
  },
): StickState {
  if (input.nestedCanConsume || input.deltaY === 0) return state;
  if (input.deltaY < 0) {
    // The wheel may already have been applied, so scrollTop can be 0 even
    // though the user just left the bottom. Only a list that cannot scroll
    // (the reply is still shorter than the panel) should keep following.
    const canScroll = input.scrollHeight - input.clientHeight > 1;
    return {
      ...state,
      wheelDown: false,
      following: canScroll ? false : state.following,
    };
  }
  return { ...state, wheelDown: true };
}

/**
 * Scroll-position sync. Ignores scrolls that were not a user gesture,
 * so a pin to the bottom cannot clear a pause or mark the user as paused.
 */
export function onScrollerScroll(
  state: StickState,
  distance: number,
  threshold = STICK_BOTTOM_THRESHOLD_PX,
): StickState {
  if (state.userInteracting) {
    return { ...state, following: isNearBottom(distance, threshold) };
  }
  if (state.wheelDown) {
    return { ...state, wheelDown: false, following: isNearBottom(distance, threshold) };
  }
  return state;
}

export function shouldPin(state: StickState, force = false): boolean {
  if (!force && state.userInteracting) return false;
  return force || state.following;
}

/**
 * Pointer or touch released. A click that did not move the list must not
 * pause following just because streamed text grew underneath.
 * A drag updates following from where the user actually left the list.
 */
export function finishUserInteraction(
  state: StickState,
  input: { startScrollTop: number; scrollTop: number; distance: number },
  threshold = STICK_BOTTOM_THRESHOLD_PX,
): StickState {
  if (!state.userInteracting) return state;
  const moved = Math.abs(input.scrollTop - input.startScrollTop) > 2;
  return {
    ...state,
    userInteracting: false,
    following: moved ? isNearBottom(input.distance, threshold) : state.following,
  };
}

export function overflowYCanConsume(
  overflowY: string,
  scrollHeight: number,
  scrollTop: number,
  clientHeight: number,
  deltaY: number,
): boolean {
  const scrollable = overflowY === 'auto' || overflowY === 'scroll' || overflowY === 'overlay';
  if (!scrollable || deltaY === 0) return false;
  if (scrollHeight <= clientHeight + 1) return false;
  if (deltaY > 0) return scrollHeight - scrollTop - clientHeight > 1;
  return scrollTop > 1;
}

/** True when an element inside the message list can still scroll in this wheel direction. */
export function nestedScrollerConsumesWheel(
  target: EventTarget | null,
  boundary: HTMLElement,
  deltaY: number,
): boolean {
  let node = target instanceof Element ? target : null;
  while (node && node !== boundary) {
    if (node instanceof HTMLElement) {
      const overflowY = getComputedStyle(node).overflowY;
      if (overflowYCanConsume(overflowY, node.scrollHeight, node.scrollTop, node.clientHeight, deltaY)) {
        return true;
      }
    }
    node = node.parentElement;
  }
  return false;
}

/**
 * Pin one scroller to its own bottom. Does not call scrollIntoView, so ancestor
 * scrollports (the page, the input) stay put. scroll-behavior is forced to auto
 * for the assignment so a smooth-scroll stylesheet cannot animate past the user.
 */
export function pinScrollTop(el: HTMLElement): number {
  const max = Math.max(0, el.scrollHeight - el.clientHeight);
  const previous = el.style.scrollBehavior;
  el.style.scrollBehavior = 'auto';
  if (Math.abs(el.scrollTop - max) > 1) el.scrollTop = max;
  el.style.scrollBehavior = previous;
  return max;
}
