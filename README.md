# CLJS/TS App Template

Монорепа: CLJS-ядро (`packages/core`) + React/TS-оболочка (`packages/app`).

## Требования

- Node 20.19+ или 22.12+ (требование Vite 8)
- pnpm 10+ (ставится автоматически через corepack, см. ниже)
- JDK 21+ (требование shadow-cljs 3.x / Closure Compiler)

На Fedora: `sudo dnf install java-21-openjdk-devel`.
pnpm подтянется сам из `packageManager`-поля при первом `pnpm`-вызове,
если включён corepack: `corepack enable`.

## Старт

```bash
pnpm install
pnpm --filter @myapp/core compile    # первая сборка ядра, чтобы app увидел dist/
```

Затем два терминала:

```bash
# T1 — пересборка ядра при изменениях
pnpm dev:core

# T2 — Vite dev-сервер
pnpm dev:app
```

## Production-билд

```bash
pnpm build
```

Сначала `shadow-cljs release` для ядра с `:advanced`-оптимизацией, затем `vite build`.

## Стек

- **React 19.2** — UI-слой.
- **Vite 8** — dev-сервер и production-бандл (на Rolldown).
- **TypeScript 6** — типы для границы с ядром.
- **shadow-cljs 3** — компилятор CLJS, таргет `:esm`.
- **pnpm 10** — workspace-менеджер. По умолчанию в pnpm 10 включён
  `minimumReleaseAge: 1440` (1 сутки) — свежие публикации npm не резолвятся
  первые 24 часа. Защита от supply-chain атак. Отключается через
  `minimumReleaseAge: 0` в `pnpm-workspace.yaml`, если срочно нужна свежая версия.
