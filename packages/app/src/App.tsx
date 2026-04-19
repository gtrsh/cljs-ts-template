import { useEffect, useState } from "react";
import { createApp, type App as CoreApp } from "@myapp/core";
import { Kanban } from "./kanban/Kanban";

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
