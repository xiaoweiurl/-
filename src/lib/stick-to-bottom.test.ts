import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  createStickState,
  distanceFromBottom,
  finishUserInteraction,
  isNearBottom,
  onScrollerScroll,
  onUserWheel,
  overflowYCanConsume,
  pinScrollTop,
  shouldPin,
} from './stick-to-bottom';

test('distance from the bottom is the unseen tail', () => {
  assert.equal(distanceFromBottom({ scrollHeight: 800, scrollTop: 500, clientHeight: 200 }), 100);
  assert.equal(isNearBottom(80), true);
  assert.equal(isNearBottom(81), false);
});

test('upward wheel pauses following and a later programmatic scroll cannot resume it', () => {
  const paused = onUserWheel(createStickState(), {
    deltaY: -40,
    scrollHeight: 1000,
    clientHeight: 200,
    nestedCanConsume: false,
  });
  assert.equal(paused.following, false);
  assert.equal(shouldPin(paused), false);

  const afterPinScroll = onScrollerScroll(paused, 0);
  assert.equal(afterPinScroll.following, false);
  assert.equal(shouldPin(afterPinScroll), false);
});

test('upward wheel at the top of a short list keeps following', () => {
  const state = onUserWheel(createStickState(), {
    deltaY: -20,
    scrollHeight: 180,
    clientHeight: 200,
    nestedCanConsume: false,
  });
  assert.equal(state.following, true);
  assert.equal(shouldPin(state), true);
});

test('upward wheel that reaches the top of a long list still pauses', () => {
  const state = onUserWheel(createStickState(), {
    deltaY: -800,
    scrollHeight: 1400,
    clientHeight: 400,
    nestedCanConsume: false,
  });
  assert.equal(state.following, false);
  assert.equal(shouldPin(state), false);
});

test('downward wheel resumes only when the scroll lands near the bottom', () => {
  const paused = onUserWheel(createStickState(), {
    deltaY: -40,
    scrollHeight: 1000,
    clientHeight: 200,
    nestedCanConsume: false,
  });
  const wheelingDown = onUserWheel(paused, {
    deltaY: 100,
    scrollHeight: 1000,
    clientHeight: 200,
    nestedCanConsume: false,
  });
  assert.equal(wheelingDown.following, false);

  const stillAway = onScrollerScroll(wheelingDown, 240);
  assert.equal(stillAway.following, false);
  assert.equal(stillAway.wheelDown, false);

  const again = onUserWheel(stillAway, {
    deltaY: 80,
    scrollHeight: 1000,
    clientHeight: 200,
    nestedCanConsume: false,
  });
  const back = onScrollerScroll(again, 40);
  assert.equal(back.following, true);
  assert.equal(shouldPin(back), true);
});

test('nested scroller wheel does not pause the message list', () => {
  const state = onUserWheel(createStickState(), {
    deltaY: -80,
    scrollHeight: 1000,
    clientHeight: 200,
    nestedCanConsume: true,
  });
  assert.equal(state.following, true);
});

test('scrollbar drag updates following from position and blocks pinning while held', () => {
  const dragging = { ...createStickState(), userInteracting: true };
  assert.equal(shouldPin(dragging), false);
  const away = onScrollerScroll(dragging, 300);
  assert.equal(away.following, false);
  const returned = onScrollerScroll({ ...away, userInteracting: true }, 20);
  assert.equal(returned.following, true);
});

test('a click that does not move the list keeps following while streamed text grows', () => {
  const holding = { ...createStickState(), userInteracting: true };
  const released = finishUserInteraction(holding, {
    startScrollTop: 400,
    scrollTop: 400,
    distance: 260,
  });
  assert.equal(released.userInteracting, false);
  assert.equal(released.following, true);
});

test('dragging the list away pauses, and dragging back near the bottom resumes', () => {
  const holding = { ...createStickState(), userInteracting: true };
  const away = finishUserInteraction(holding, {
    startScrollTop: 700,
    scrollTop: 200,
    distance: 500,
  });
  assert.equal(away.following, false);
  const back = finishUserInteraction({ ...away, userInteracting: true }, {
    startScrollTop: 200,
    scrollTop: 680,
    distance: 20,
  });
  assert.equal(back.following, true);
});

test('sending a message can force the pin even after the user scrolled away', () => {
  const paused = onUserWheel(createStickState(), {
    deltaY: -10,
    scrollHeight: 800,
    clientHeight: 200,
    nestedCanConsume: false,
  });
  assert.equal(shouldPin(paused), false);
  assert.equal(shouldPin(paused, true), true);
});

test('pinScrollTop moves only that element and overrides smooth scroll behavior', () => {
  let behaviorWhenAssigned = '';
  const el = {
    scrollHeight: 900,
    clientHeight: 300,
    _top: 40,
    style: { scrollBehavior: 'smooth' },
    get scrollTop() {
      return this._top;
    },
    set scrollTop(value: number) {
      behaviorWhenAssigned = this.style.scrollBehavior;
      this._top = value;
    },
  };
  const max = pinScrollTop(el as unknown as HTMLElement);
  assert.equal(max, 600);
  assert.equal(el.scrollTop, 600);
  assert.equal(behaviorWhenAssigned, 'auto');
  assert.equal(el.style.scrollBehavior, 'smooth');
  assert.equal((el as { scrollIntoView?: unknown }).scrollIntoView, undefined);
});

test('a box consumes the wheel only while it can still scroll that way', () => {
  assert.equal(overflowYCanConsume('auto', 400, 100, 100, -1), true);
  assert.equal(overflowYCanConsume('auto', 400, 0, 100, -1), false);
  assert.equal(overflowYCanConsume('auto', 400, 200, 100, 1), true);
  assert.equal(overflowYCanConsume('auto', 400, 300, 100, 1), false);
  assert.equal(overflowYCanConsume('visible', 400, 100, 100, -1), false);
  assert.equal(overflowYCanConsume('auto', 100, 0, 100, -1), false);
});
