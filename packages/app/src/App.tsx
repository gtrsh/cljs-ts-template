import { useSyncExternalStore, useState, useEffect, useCallback } from "react";
import { createApp, type App as CoreApp, type Board } from "@myapp/core";

export function App() {
  const [app, setApp] = useState<CoreApp | null>(null);

  useEffect(() => {
    const instance = createApp();
    setApp(instance);
    return () => {
      instance.shutdown();
      setApp(null);
    };
  }, []);

  if (!app) {
    return <p style={{ padding: 24, fontFamily: "system-ui" }}>Инициализация…</p>;
  }

  return <Kanban app={app} />;
}

function Kanban({ app }: { app: CoreApp }) {
  const subscribe = useCallback(
    (cb: () => void) => app.board.subscribe(cb),
    [app]
  );
  const getSnapshot = useCallback(() => app.board.getState(), [app]);

  const board = useSyncExternalStore<Board | null>(
    subscribe,
    getSnapshot,
    () => null
  );

  const [lastError, setLastError] = useState<string | null>(null);

  if (!board) {
    return <p style={{ padding: 24, fontFamily: "system-ui" }}>Загрузка из IDB…</p>;
  }

  const addCard = () => {
    const err = app.board.dispatch({
      type: "add-card",
      title: `Карточка ${Date.now() % 10000}`,
      columnId: "todo",
    });
    setLastError(err ? JSON.stringify(err) : null);
  };

  return (
    <main style={{ fontFamily: "system-ui", padding: 24 }}>
      <h1>Kanban (step 4)</h1>
      <p style={{ color: "#666", fontSize: 14 }}>
        Сервис + IDB-персист + подписка. dnd-kit появится на шаге 6.
      </p>
      <button onClick={addCard} style={{ marginBottom: 16 }}>
        + добавить карточку в «To Do»
      </button>
      {lastError && (
        <p style={{ color: "crimson", fontSize: 14 }}>error: {lastError}</p>
      )}

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
