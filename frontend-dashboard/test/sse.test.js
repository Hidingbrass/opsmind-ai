import assert from "node:assert/strict";
import test from "node:test";

import { createResilientEventSubscription } from "../src/sse.js";

class FakeEventSource {
  constructor(url) {
    this.url = url;
    this.listeners = new Map();
    this.closed = false;
  }

  addEventListener(name, listener) {
    this.listeners.set(name, listener);
  }

  close() {
    this.closed = true;
  }
}

test("reconnects after a transient disconnect while task is running", async () => {
  const sources = [];
  const scheduled = [];
  const subscription = createResilientEventSubscription({
    url: "/events/task-1",
    eventNames: ["RUNNING", "SUCCESS"],
    onEvent: () => {},
    readCurrent: async () => ({ status: "RUNNING" }),
    onTerminal: () => {},
    eventSourceFactory: (url) => {
      const source = new FakeEventSource(url);
      sources.push(source);
      return source;
    },
    schedule: (callback, delay) => {
      scheduled.push({ callback, delay });
      return scheduled.length;
    },
    cancelSchedule: () => {},
  });

  await sources[0].onerror();
  assert.equal(sources[0].closed, true);
  assert.equal(scheduled.length, 1);
  assert.equal(scheduled[0].delay, 500);

  scheduled[0].callback();
  assert.equal(sources.length, 2);
  subscription.close();
  assert.equal(sources[1].closed, true);
});

test("uses the persisted terminal state instead of reconnecting", async () => {
  const sources = [];
  const terminalStates = [];
  const scheduled = [];
  createResilientEventSubscription({
    url: "/events/task-1",
    eventNames: ["FAILED"],
    onEvent: () => {},
    readCurrent: async () => ({ status: "FAILED" }),
    onTerminal: (task) => terminalStates.push(task),
    eventSourceFactory: (url) => {
      const source = new FakeEventSource(url);
      sources.push(source);
      return source;
    },
    schedule: (callback) => scheduled.push(callback),
    cancelSchedule: () => {},
  });

  await sources[0].onerror();
  assert.deepEqual(terminalStates, [{ status: "FAILED" }]);
  assert.equal(scheduled.length, 0);
});
