import { useState, useMemo, useCallback, useSyncExternalStore } from "react";
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  KeyboardSensor,
  useSensor,
  useSensors,
  closestCorners,
  type DragStartEvent,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  horizontalListSortingStrategy,
  sortableKeyboardCoordinates,
} from "@dnd-kit/sortable";
import type { App as CoreApp, Board, Card as CardType } from "@myapp/core";
import { Column } from "./Column";
import { Card } from "./Card";
import type { DragData } from "./types";

interface Props {
  app: CoreApp;
}

export function Kanban({ app }: Props) {
  // Подписка на состояние ядра
  const subscribe = useCallback(
    (cb: () => void) => app.board.subscribe(cb),
    [app]
  );
  const getSnapshot = useCallback(() => app.board.getState(), [app]);
  const board = useSyncExternalStore<Board | null>(subscribe, getSnapshot, () => null);

  // Что сейчас тащится — для DragOverlay.
  // null = ничего не тащим, иначе {id, data} активного draggable.
  const [activeDrag, setActiveDrag] = useState<{
    id: string;
    data: DragData;
  } | null>(null);

  const [error, setError] = useState<string | null>(null);

  // Сенсоры: мышь/тач + клавиатура для a11y.
  // distance: 5 — нужен чтобы клик по карточке не начинал drag моментально.
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  // Индекс колонки по карточке — считаем один раз за рендер.
  const cardToColumn = useMemo(() => {
    if (!board) return new Map<string, string>();
    const m = new Map<string, string>();
    for (const [colId, col] of Object.entries(board.columns)) {
      for (const cardId of col.cardIds) m.set(cardId, colId);
    }
    return m;
  }, [board]);

  if (!board) {
    return <p style={{ padding: 24, fontFamily: "system-ui" }}>Загрузка из IDB…</p>;
  }

  const dispatchWithToast = (cmd: Parameters<typeof app.board.dispatch>[0]) => {
    const result = app.board.dispatch(cmd);
    if (result) {
      // kebab-case ключи в error-маппинге — мы намеренно не нормализовали
      // их в entry.cljs, см. комментарий в types/index.d.ts
      const errKey = (result as any).error as string;
      const limit = (result as any).limit;
      const msg =
        errKey === "wip-exceeded"
          ? `WIP-лимит превышен (${limit})`
          : errKey === "invalid-title"
          ? "Название не может быть пустым"
          : `Ошибка: ${errKey}`;
      setError(msg);
      setTimeout(() => setError(null), 3000);
    }
  };

  const onDragStart = (e: DragStartEvent) => {
    setActiveDrag({
      id: String(e.active.id),
      data: (e.active.data.current as DragData) ?? { type: "card", columnId: "" },
    });
  };

  const onDragEnd = (e: DragEndEvent) => {
    setActiveDrag(null);
    const { active, over } = e;
    if (!over) return;

    const activeData = active.data.current as DragData | undefined;
    const overData = over.data.current as
      | DragData
      | { type: "column-body"; columnId: string }
      | undefined;

    const activeId = String(active.id);
    const overId = String(over.id);

    // --- 1. Реордер колонок ---
    if (activeData?.type === "column" && overData?.type === "column") {
      if (activeId === overId) return;
      const targetIndex = board.columnOrder.indexOf(overId);
      if (targetIndex === -1) return;
      dispatchWithToast({
        type: "reorder-columns",
        columnId: activeId,
        targetIndex,
      });
      return;
    }

    // --- 2. Перемещение карточки ---
    if (activeData?.type === "card") {
      // Где дропнули: на карточку или на body колонки?
      let targetColumnId: string;
      let targetIndex: number | null;

      if (overData?.type === "card") {
        targetColumnId = cardToColumn.get(overId) ?? activeData.columnId;
        const column = board.columns[targetColumnId];
        targetIndex = column.cardIds.indexOf(overId);
        // Если тащим в ту же колонку и отпустили на карточку, которая
        // была "ниже" нас — dnd-kit уже учёл сдвиг, используем overIndex.
        // Если тащим в другую колонку — вставим перед карточкой under cursor.
      } else if (overData?.type === "column-body") {
        targetColumnId = overData.columnId;
        targetIndex = null; // в конец
      } else {
        // over — сама колонка (не body) — считаем, что это её конец
        targetColumnId = overId;
        targetIndex = null;
      }

      // Избегаем no-op dispatch'а, если карточка уронена сама на себя
      // в том же положении.
      if (activeId === overId) return;

      dispatchWithToast({
        type: "move-card",
        cardId: activeId,
        targetColumnId,
        targetIndex,
      });
    }
  };

  const onDragCancel = () => setActiveDrag(null);

  // Рендер активной карточки для DragOverlay
  const activeCard: CardType | null =
    activeDrag?.data.type === "card" ? board.cards[activeDrag.id] ?? null : null;

  return (
    <main style={{ fontFamily: "system-ui", padding: 24 }}>
      <div style={{ marginBottom: 16, display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h1 style={{ margin: 0 }}>Kanban</h1>
        {error && (
          <span
            style={{
              background: "crimson",
              color: "white",
              padding: "6px 12px",
              borderRadius: 6,
              fontSize: 13,
            }}
          >
            {error}
          </span>
        )}
      </div>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={onDragStart}
        onDragEnd={onDragEnd}
        onDragCancel={onDragCancel}
      >
        <SortableContext
          items={board.columnOrder}
          strategy={horizontalListSortingStrategy}
        >
          <div
            style={{
              display: "flex",
              gap: 12,
              alignItems: "flex-start",
              overflowX: "auto",
              paddingBottom: 8,
            }}
          >
            {board.columnOrder.map(colId => {
              const column = board.columns[colId];
              const cards = column.cardIds
                .map(cid => board.cards[cid])
                .filter(Boolean);
              return (
                <Column
                  key={column.id}
                  column={column}
                  cards={cards}
                  onAddCard={(columnId, title) =>
                    dispatchWithToast({ type: "add-card", title, columnId })
                  }
                />
              );
            })}
          </div>
        </SortableContext>

        <DragOverlay>
          {activeCard && <Card card={activeCard} isDragOverlay />}
        </DragOverlay>
      </DndContext>
    </main>
  );
}
