export function sayHello(name: string): string;

export interface Stats {
  count: number;
  sum: number;
  avg: number | null;
  min: number | null;
  max: number | null;
}

export function computeStats(numbers: readonly number[]): Stats;
