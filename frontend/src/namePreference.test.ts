import { afterEach, describe, expect, it, vi } from "vitest";
import { readPlayerName, savePlayerName } from "./namePreference";

afterEach(() => vi.unstubAllGlobals());

describe("optional player-name preference", () => {
  it.each([null, "", "Alice"])(
    "reads stored value %s under the existing key",
    (value) => {
      const getItem = vi.fn(() => value);
      vi.stubGlobal("localStorage", { getItem });
      expect(readPlayerName()).toBe(value ?? "");
      expect(getItem).toHaveBeenCalledExactlyOnceWith("table-name");
    },
  );

  it("saves without changing the name or key", () => {
    const setItem = vi.fn();
    vi.stubGlobal("localStorage", { setItem });
    savePlayerName("  Alice  ");
    expect(setItem).toHaveBeenCalledExactlyOnceWith("table-name", "  Alice  ");
  });

  it.each([undefined, null])(
    "allows missing storage %s without caching names globally",
    (storage) => {
      vi.stubGlobal("localStorage", storage);
      expect(readPlayerName()).toBe("");
      expect(() => savePlayerName("Temporary")).not.toThrow();
      expect(readPlayerName()).toBe("");
    },
  );

  it("handles a throwing storage getter", () => {
    vi.stubGlobal("localStorage", undefined);
    Object.defineProperty(globalThis, "localStorage", {
      configurable: true,
      get: () => {
        throw new DOMException("Denied", "SecurityError");
      },
    });
    expect(readPlayerName()).toBe("");
    expect(() => savePlayerName("Alice")).not.toThrow();
  });

  it("isolates failed reads from working writes", () => {
    const setItem = vi.fn();
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error("Read denied");
      },
      setItem,
    });
    expect(readPlayerName()).toBe("");
    savePlayerName("Alice");
    expect(setItem).toHaveBeenCalledExactlyOnceWith("table-name", "Alice");
  });

  it("isolates failed writes from working reads", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => "Saved",
      setItem: () => {
        throw new DOMException("Full", "QuotaExceededError");
      },
    });
    expect(() => savePlayerName("New")).not.toThrow();
    expect(readPlayerName()).toBe("Saved");
  });
});
