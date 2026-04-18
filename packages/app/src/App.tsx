import { useMemo, useState } from "react";
import { sayHello, computeStats, type Stats } from "@myapp/core";

export function App() {
  const [name, setName] = useState("Jhon Dorn");
  const [input, setInput] = useState("1, 2, 3, 4, 5, 10, 42");

  const stats: Stats = useMemo(() => {
    const nums = input
      .split(",")
      .map(s => Number(s.trim()))
      .filter(n => Number.isFinite(n));
    return computeStats(nums);
  }, [input]);

  return (
    <main style={{ fontFamily: "system-ui", padding: 24, maxWidth: 640 }}>
      <h1>CLJS core × React shell</h1>

      <section style={{ marginBottom: 24 }}>
        <h2>sayHello</h2>
        <input value={name} onChange={e => setName(e.target.value)} />
        <p>{sayHello(name)}</p>
      </section>

      <section>
        <h2>computeStats</h2>
        <input
          style={{ width: "100%" }}
          value={input}
          onChange={e => setInput(e.target.value)}
        />
        <pre>{JSON.stringify(stats, null, 2)}</pre>
      </section>
    </main>
  );
}
