import type { Card } from "../protocol";
export type Action = (type: string, data?: Record<string, unknown>) => void;
export type Inspect = (card: Card) => void;
export type OpenZone = (seat: number, zone: string) => void;
