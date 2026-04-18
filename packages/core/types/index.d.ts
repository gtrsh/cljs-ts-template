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

// --- команды ---
// id и createdAt опциональны — сервис допишет их сам, если не указаны.
// Это облегчает вызов из view: board.dispatch({type: "add-card", title, columnId}).

export type Command =
  | { type: "add-card"; title: string; columnId: string; id?: string; createdAt?: number }
  | { type: "remove-card"; cardId: string }
  | { type: "move-card"; cardId: string; targetColumnId: string; targetIndex: number | null }
  | { type: "rename-card"; cardId: string; title: string }
  | { type: "reorder-columns"; columnId: string; targetIndex: number }
  | { type: "set-wip-limit"; columnId: string; limit: number | null };

export type CommandError =
  | { error: "column-not-found"; "column-id": string }
  | { error: "card-not-found"; "card-id": string }
  | { error: "card-exists"; "card-id": string }
  | { error: "wip-exceeded"; "column-id": string; limit: number }
  | { error: "invalid-title" }
  | { error: "invalid-limit"; limit: unknown }
  | { error: "unknown-command"; type: string }
  | { error: "not-ready" };

export interface BoardApi {
  getState(): Board | null;
  dispatch(command: Command): CommandError | null;
  subscribe(listener: (board: Board | null) => void): () => void;
}

export interface App {
  board: BoardApi;
  shutdown(): void;
}

export interface AppOptions {
  socketUrl?: string;
  dbName?: string;
  storeName?: string;
}

export function createApp(opts?: AppOptions): App;
export function createAppWithDeps(deps: unknown): App;
