import { useMemo } from "react";
import { createApp, type Board } from "@myapp/core";

export function App() {
  // Создаём приложение один раз на время жизни компонента.
  // На шаге 6 переедет в contexts + subscribe, пока это просто демо.
  const app = useMemo(() => createApp(), []);
  const board: Board = app.board.getState();

  return (
    <main style={{ fontFamily: "system-ui", padding: 24 }}>
      <h1>Kanban</h1>
      <p style={{ color: "#666", fontSize: 14 }}>
        Ядро инициализировано, состояние получено. DnD появится на шаге 6.
      </p>

      <pre
        style={{
          background: "#f5f5f5",
          padding: 16,
          borderRadius: 8,
          fontSize: 12,
          overflow: "auto",
        }}
      >
        {JSON.stringify(board, null, 2)}
      </pre>
    </main>
  );
}
