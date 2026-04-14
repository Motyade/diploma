# Ручное тестирование WebSocket (Production)

Этот набор предназначен для ручного тестирования WS/STOMP в проде:

- URL: `http://83.147.255.205:8180`
- API base: `http://83.147.255.205:8180/api/v1`

## Что внутри папки

- `ws-production-test.ps1` - сценарий, который генерирует события жизненного цикла заявок.
- `ws-devtools-probe.js` - скрипт для браузерной Console, чтобы ловить и анализировать WS-сообщения.

## 1) Что открыть

1. Терминал PowerShell в корне проекта.
2. Браузер на странице `http://83.147.255.205`.
3. DevTools -> Console.

## 2) Подключить WS-сниффер в браузере

1. Открой файл `ws-devtools-probe.js`.
2. Скопируй весь код.
3. Вставь в Console браузера и выполни.

## 3) Запустить генерацию событий

В PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\diplom\tools\manual-testing\ws-production-test.ps1
```

Если нужно быстрее без долгого ожидания SLA (WAITING/ESCALATED):

```powershell
powershell -ExecutionPolicy Bypass -File .\diplom\tools\manual-testing\ws-production-test.ps1 -SkipLongSlaWait
```

## 4) Подписка в Console

После запуска PS-скрипт выведет `SESSION1/SESSION2/SESSION3`.
Подписывайся так (в Console):

```javascript
wsProbe.start(
  "11111111-1111-1111-1111-111111111111",
  "22222222-2222-2222-2222-222222222222",
  "SESSION1_ИЛИ_SESSION2_ИЛИ_SESSION3"
);
```

## 5) Проверка результатов

Быстрая сводка событий:

```javascript
wsProbe.table();
```

Фильтрация по заявке:

```javascript
wsProbe.byRequest("REQUEST_ID");
```

Ожидаемые события:

- `REQUEST_CREATED`
- `REQUEST_WAITING` (если не пропускал SLA-ожидание)
- `REQUEST_ESCALATED` (если не пропускал SLA-ожидание)
- `REQUEST_ASSIGNED`
- `REQUEST_COMPLETED`
- `REQUEST_CANCELED`
- `REQUEST_REASSIGNED`

Важно: `REQUEST_REMINDED` может не прийти в WS (это допустимо для текущей бизнес-логики).
