import type { Locator, Page } from "@playwright/test";
type Scope = Page | Locator;
// Prefix matching stays stable when a label's textarea/option content changes.
export const decklist = (scope: Scope) => scope.getByLabel(/^Decklist/);
export const commander = (scope: Scope) => scope.getByLabel(/^Commander/);
export const secondCommander = (scope: Scope) =>
  scope.getByLabel(/^Second commander/);
export const previewDeck = (scope: Scope) =>
  scope.getByRole("button", { name: "Preview deck", exact: true });
export const useDeck = (scope: Scope) =>
  scope.getByRole("button", { name: "Use this deck", exact: true });
