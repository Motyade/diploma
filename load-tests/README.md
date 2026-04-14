# RetailHub Load Tests

Нагрузочные тесты на базе [k6](https://k6.io/).

## Установка k6

- macOS: `brew install k6`
- Linux: https://k6.io/docs/get-started/installation/
- Windows: `choco install k6` или скачать бинарник
- Docker: `docker run --rm -i grafana/k6 run - <script.js`

## Запуск платформы

```bash
cd ../
docker compose up -d
```

## Запуск тестов

### Аутентификация
```bash
k6 run auth-load.js
```

### Жизненный цикл заявки
```bash
k6 run request-lifecycle.js
```

### Аналитика
```bash
k6 run analytics-load.js
```

### Смешанная нагрузка
```bash
k6 run mixed-workload.js
```

### Указание адреса сервера
```bash
k6 run -e BASE_URL=http://your-server:8180 auth-load.js
```

## Сценарии

| Скрипт | Цель | VUs | Длительность | Пороги |
|--------|------|-----|-------------|--------|
| auth-load.js | Эндпоинты аутентификации | 50 | 3 мин | p95 < 500ms |
| request-lifecycle.js | CRUD заявок | 30 | 3 мин | p95 < 1000ms |
| analytics-load.js | Запросы аналитики | 100 | 3 мин | p95 < 500ms |
| mixed-workload.js | Все эндпоинты | 50 | 4 мин | p95 < 800ms |

## Кастомные метрики (mixed-workload.js)

- `read_operations` / `read_duration` — операции чтения (60% трафика)
- `write_operations` / `write_duration` — операции записи (30% трафика)
- `analytics_operations` / `analytics_duration` — аналитика (10% трафика)
