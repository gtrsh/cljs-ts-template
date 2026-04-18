export interface Card {
  id: string;
  title: string;
  createdAt: number;
  columnId: string;
}

export interface Column {
  id: string;
  title: string;
  wipLimit: number | null;
  cardIds: string[];
}

export interface Board {
  columns: Record<string, Column>;
  columnOrder: string[];
  cards: Record<string, Card>;
}

export interface BoardApi {
  getState(): Board;
  // Появятся на следующих шагах:
  // moveCard(cmd: MoveCardCommand): void | { error: string };
  // subscribe(fn: (board: Board) => void): () => void;
  // addCard(cmd: AddCardCommand): void;
}

export interface App {
  board: BoardApi;
  shutdown(): void;
}

export interface AppOptions {
  socketUrl?: string;
  idbStoreName?: string;
}

export function createApp(opts?: AppOptions): App;

/** Для тестов/Storybook. Типизация deps появится на шаге 5. */
export function createAppWithDeps(deps: unknown): App;
