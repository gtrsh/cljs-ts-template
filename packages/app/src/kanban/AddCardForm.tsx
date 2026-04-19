import { useState } from "react";

interface Props {
  onSubmit: (title: string) => void;
}

export function AddCardForm({ onSubmit }: Props) {
  const [open, setOpen] = useState(false);
  const [value, setValue] = useState("");

  if (!open) {
    return (
      <button
        onClick={() => setOpen(true)}
        style={{
          width: "100%",
          padding: "6px 10px",
          marginTop: 6,
          border: "1px dashed #bbb",
          borderRadius: 6,
          background: "transparent",
          color: "#666",
          fontSize: 13,
          cursor: "pointer",
        }}
      >
        + добавить
      </button>
    );
  }

  const submit = () => {
    const title = value.trim();
    if (title) onSubmit(title);
    setValue("");
    setOpen(false);
  };

  return (
    <div style={{ marginTop: 6 }}>
      <textarea
        autoFocus
        value={value}
        onChange={e => setValue(e.target.value)}
        onKeyDown={e => {
          if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            submit();
          }
          if (e.key === "Escape") {
            setValue("");
            setOpen(false);
          }
        }}
        placeholder="Название карточки"
        style={{
          width: "100%",
          minHeight: 50,
          padding: 6,
          border: "1px solid #bbb",
          borderRadius: 6,
          fontSize: 13,
          fontFamily: "inherit",
          resize: "vertical",
          boxSizing: "border-box",
        }}
      />
      <div style={{ display: "flex", gap: 6, marginTop: 4 }}>
        <button
          onClick={submit}
          style={{ padding: "4px 10px", fontSize: 12, cursor: "pointer" }}
        >
          Добавить
        </button>
        <button
          onClick={() => { setValue(""); setOpen(false); }}
          style={{ padding: "4px 10px", fontSize: 12, cursor: "pointer" }}
        >
          Отмена
        </button>
      </div>
    </div>
  );
}
