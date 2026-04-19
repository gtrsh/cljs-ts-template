import { useDroppable } from "@dnd-kit/core";
import {
  SortableContext,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import type { Column as ColumnType, Card as CardType } from "@myapp/core";
import { Card } from "./Card";
import { AddCardForm } from "./AddCardForm";
import type { DragDataColumn } from "./types";

interface Props {
  column: ColumnType;
  cards: CardType[];
  onAddCard: (columnId: string, title: string) => void;
}

export function Column({ column, cards, onAddCard }: Props) {
  const columnDragData: DragDataColumn = { type: "column" };

  // Колонка сама — sortable (для реордера колонок).
  const {
    attributes,
    listeners,
    setNodeRef: setSortableRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: column.id, data: columnDragData });

  // Колонка также — droppable для карточек из других колонок.
  // Важно: useDroppable на том же элементе, что и useSortable не нужно —
  // useSortable уже даёт droppable. Но нам нужен droppable на ВСЁ тело колонки
  // (не только на header), чтобы можно было уронить карточку в пустую колонку.
  // Поэтому используем отдельный ref для body-droppable.
  const { setNodeRef: setDroppableRef, isOver } = useDroppable({
    id: `column-body:${column.id}`,
    data: { type: "column-body", columnId: column.id },
  });

  const overLimit =
    column.wipLimit !== null && cards.length >= column.wipLimit;

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    width: 260,
    flexShrink: 0,
    background: "#f0f1f3",
    borderRadius: 8,
    padding: 10,
    display: "flex",
    flexDirection: "column",
    maxHeight: "calc(100vh - 120px)",
  };

  return (
    <div ref={setSortableRef} style={style}>
      {/* Header — grab handle для реордера колонок */}
      <div
        {...attributes}
        {...listeners}
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: 8,
          padding: "4px 6px",
          cursor: "grab",
          userSelect: "none",
        }}
      >
        <strong style={{ fontSize: 14 }}>{column.title}</strong>
        <span
          style={{
            fontSize: 12,
            color: overLimit ? "crimson" : "#888",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          {cards.length}
          {column.wipLimit !== null && ` / ${column.wipLimit}`}
        </span>
      </div>

      {/* Body — droppable-зона для карточек, scroll-контейнер */}
      <div
        ref={setDroppableRef}
        style={{
          flex: 1,
          overflowY: "auto",
          minHeight: 40,
          padding: 2,
          borderRadius: 4,
          background: isOver ? "rgba(80, 130, 240, 0.08)" : "transparent",
          transition: "background 120ms",
        }}
      >
        <SortableContext
          items={column.cardIds}
          strategy={verticalListSortingStrategy}
        >
          {cards.map(card => (
            <Card key={card.id} card={card} />
          ))}
        </SortableContext>
      </div>

      <AddCardForm onSubmit={title => onAddCard(column.id, title)} />
    </div>
  );
}
