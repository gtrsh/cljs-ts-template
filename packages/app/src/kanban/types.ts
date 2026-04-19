export type DragData =
  | { type: "card"; columnId: string }
  | { type: "column" };

export interface DragDataCard {
  type: "card";
  columnId: string;
}

export interface DragDataColumn {
  type: "column";
}
