import { CSS } from "@dnd-kit/utilities";
import { useSortable } from "@dnd-kit/sortable";
import type { Card as CardType } from "@myapp/core";
import type { DragDataCard } from "./types";

interface Props {
  card: CardType;
  isDragOverlay?: boolean;
}

export function Card({ card, isDragOverlay = false }: Props) {
  const data: DragDataCard = { type: "card", columnId: card.columnId };

  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: card.id, data });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging && !isDragOverlay ? 0.4 : 1,
    background: "white",
    border: "1px solid #e0e0e0",
    borderRadius: 6,
    padding: "8px 10px",
    marginBottom: 6,
    fontSize: 14,
    cursor: isDragOverlay ? "grabbing" : "grab",
    boxShadow: isDragOverlay
      ? "0 8px 16px rgba(0,0,0,0.18)"
      : "0 1px 2px rgba(0,0,0,0.06)",
    userSelect: "none",
  };

  return (
    <div ref={setNodeRef} style={style} {...attributes} {...listeners}>
      {card.title}
    </div>
  );
}
