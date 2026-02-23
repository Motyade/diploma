# RetailHub: Kafka — Полное руководство по событиям и жизненному циклу заявки

> **Назначение документа:** справочник для разработчика и материал для дипломной работы.  
> Описывает архитектуру событийной шины, все события в топике `request-events`, жизненный цикл заявки и полные модели сообщений.

---

## 1. Зачем Kafka в RetailHub?

В системе несколько модулей должны реагировать на одно событие (создание заявки, назначение консультанта и т.д.):
- **Notification Module** — рассылает push-уведомления
- **User Module** — обновляет статус консультанта (ACTIVE ↔ BUSY)
- **Analytics Module** — считает время реакции, нагрузку по отделам

Без Kafka `RequestService` пришлось бы вызывать каждый из них напрямую. Добавление нового модуля = изменение `RequestService`. С Kafka `RequestService` просто публикует событие и не знает о потребителях.

### Kafka vs. альтернативы

| Критерий | Apache Kafka | Spring Events (`@Async`) | RabbitMQ | Redis Pub/Sub |
|---|---|---|---|---|
| Хранение на диске | ✅ Да | ❌ Только в памяти JVM | ⚠️ Ограниченно | ❌ Нет |
| Replay истории | ✅ Да | ❌ Нет | ❌ Нет | ❌ Нет |
| Работает при рестарте сервиса | ✅ Да | ❌ Сообщения теряются | ✅ Да | ❌ Нет |
| Переход на микросервисы | ✅ Без изменений кода | ❌ Только in-process | ✅ Да | ⚠️ Ограниченно |
| Сложность настройки | Средняя | Минимальная | Низкая | Низкая |

**Вывод:** Kafka выбрана стратегически: `RequestService` уже пишет в топик, при выделении Notification/Analytics в отдельные микросервисы — код producer не меняется.

---

## 2. Конфигурация Kafka в проекте

### `application.yaml`
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: retailhub-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "ru.retailhub.*"

app:
  kafka:
    topics:
      request-events: "request-events"
```

### Топология

```
Producer: RequestService / RequestSchedulingService
    │
    ▼
Kafka Topic: "request-events"
    │  partitions: по умолчанию 1
    │  key: requestId (String UUID)
    │  value: RequestEvent (JSON)
    │
    ├──► Consumer: Notification Module (НЕ РЕАЛИЗОВАН — будущее)
    ├──► Consumer: User Module (НЕ РЕАЛИЗОВАН — будущее)
    └──► Consumer: Analytics Module (НЕ РЕАЛИЗОВАН — будущее)
```

> **Ключ = requestId.** Kafka гарантирует, что все события одной заявки попадают в один и тот же partition и обрабатываются строго по порядку.

---

## 3. Жизненный цикл заявки (State Machine)

```
Легенда:  👤  Клиент + session-токен
          🔐  Консультант/Менеджер + JWT
          ⏱   RequestSchedulingService (каждые 60 сек)
```

```
             👤 POST /requests
                  📨 CREATED
                    │
                    ▼
              ┌───────────┐
         ┌───▶│  CREATED  │────────────────────────────────────┐
         │    └───────────┘                                    │
         │         │                                           │
         │    ⏱ >3мин                                         │ 👤 POST /cancel
         │    📨 WAITING                                       │ 📨 CANCELED
         │    (псевдо-статус,                                  │
         │    escalationLevel=1)                               │
         │         │                                           │
         │    ⏱ >5мин                                         ▼
         │    📨 ESCALATED                              ┌───────────┐
         │         │                                   │  CANCELED  │
         │         ▼                                   └───────────┘
         │    ┌────────────┐                                ▲   ▲
         │    │ ESCALATED  │── 👤 POST /cancel ─────────────┘   │
         │    └────────────┘   📨 CANCELED                       │
         │         │                                             │
         │    🔐 POST /assign                                    │
         │    📨 ASSIGNED                                        │
         │         │                                             │
         │    ┌────────────┐                                     │
         └────│  ASSIGNED  │── 👤 POST /cancel ─────────────────┘
              └────────────┘   📨 CANCELED
                   │   ▲
                   │   │ 👤 POST /remind (>1 мин)
                   │   │ 📨 REMINDED (статус не меняется)
                   │   └─────(пунктир)────┐
                   │                       │
                   │   👤 POST /reassign (>3 мин)
                   │   📨 REASSIGNED
                   │   (статус → CREATED)
                   │
                   │   🔐 POST /complete
                   │   📨 COMPLETED
                   ▼
              ┌───────────┐
              │ COMPLETED │
              └───────────┘
```

### Таблица переходов

| Откуда | Куда | Кто | Эндпоинт | 📨 Kafka-событие |
|---|---|---|---|---|
| (старт) | `CREATED` | 👤 Клиент | `POST /requests` | `CREATED` |
| `CREATED` | `WAITING`* | ⏱ Scheduler | авто, каждую мин | `WAITING` |
| `WAITING`* | `ESCALATED` | ⏱ Scheduler | авто, каждую мин | `ESCALATED` |
| `CREATED` | `ASSIGNED` | 🔐 Консультант | `POST /requests/{id}/assign` | `ASSIGNED` |
| `ESCALATED` | `ASSIGNED` | 🔐 Консультант/Менеджер | `POST /requests/{id}/assign` | `ASSIGNED` |
| `ASSIGNED` | `COMPLETED` | 🔐 Консультант | `POST /requests/{id}/complete` | `COMPLETED` |
| `CREATED` | `CANCELED` | 👤 Клиент + session | `POST /requests/{id}/cancel` | `CANCELED` |
| `WAITING`* | `CANCELED` | 👤 Клиент + session | `POST /requests/{id}/cancel` | `CANCELED` |
| `ESCALATED` | `CANCELED` | 👤 Клиент + session | `POST /requests/{id}/cancel` | `CANCELED` |
| `ASSIGNED` | `CANCELED` | 👤 Клиент + session | `POST /requests/{id}/cancel` | `CANCELED` |
| `ASSIGNED` | `CREATED` | 👤 Клиент + session (>3мин) | `POST /requests/{id}/reassign` | `REASSIGNED` |
| `ASSIGNED` | `ASSIGNED` | 👤 Клиент + session (>1мин) | `POST /requests/{id}/remind` | `REMINDED` |

> *WAITING — не отдельный статус в БД. `status = CREATED`, `escalation_level = 1`. Kafka-событие `WAITING` отправляется как сигнал для мягкого напоминания консультантам.

---

## 4. Полные модели Kafka-событий

Все события публикуются в один топик: **`request-events`**.  
Используется **Unified Event Model** — единый класс `RequestEvent` для всех типов.

### Структура сообщения целиком

```
┌─────────────────────────────────────────────┐
│              KAFKA MESSAGE                  │
├───────────────┬───────────────────────────┤
│ Topic         │ request-events             │
│ Partition     │ hash(requestId) % N        │
│ Offset        │ авто-инкремент             │
│ Key (String)  │ "550e8400-e29b-41d4-..."  │  ← requestId
│ Timestamp     │ устанавливает брокер       │
│ Headers       │ (не заполняются сейчас)    │
├───────────────┴───────────────────────────┤
│              VALUE (JSON)                  │
│  {                                          │
│    "type":             "...",               │
│    "requestId":        "UUID",              │
│    "storeId":          "UUID",              │
│    "departmentId":     "UUID",              │
│    "departmentName":   "String",            │
│    "assignedUserId":   "UUID | null",       │
│    "assignedUserName": "String | null",     │
│    "reason":           "String | null",     │
│    "timestamp":        1708700000000        │  ← millis
│  }                                          │
└─────────────────────────────────────────────┘
```

---

### 4.1. `CREATED` — заявка создана

**Триггер:** `POST /requests` (клиент сканирует QR)  
**Кто публикует:** `RequestService.createRequest()`

```json
{
  "type": "CREATED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   null,
  "assignedUserName": null,
  "reason":    null,
  "timestamp": 1708700000000
}
```

**Что делают потребители (план):**
- Notification Module → push всем консультантам отдела `Электроника` со статусом `ACTIVE`
- Analytics Module → фиксирует `created_at` для расчёта времени реакции

---

### 4.2. `ASSIGNED` — консультант взял заявку

**Триггер:** `POST /requests/{id}/assign` (консультант с JWT)  
**Кто публикует:** `RequestService.assignRequest()`

```json
{
  "type": "ASSIGNED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "assignedUserName": "Иван Петров",
  "reason":    null,
  "timestamp": 1708700060000
}
```

**Что делают потребители (план):**
- Notification Module → push клиенту: «К вам идёт Иван Петров»
- User Module → `users SET current_status = 'BUSY' WHERE id = assignedUserId`
- Analytics Module → фиксирует `assigned_at`, считает время реакции = `assigned_at - created_at`

---

### 4.3. `COMPLETED` — обслуживание завершено

**Триггер:** `POST /requests/{id}/complete` (консультант с JWT)  
**Кто публикует:** `RequestService.completeRequest()`

```json
{
  "type": "COMPLETED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "assignedUserName": "Иван Петров",
  "reason":    null,
  "timestamp": 1708700360000
}
```

**Что делают потребители (план):**
- User Module → `users SET current_status = 'ACTIVE' WHERE id = assignedUserId`
- Analytics Module → считает время обслуживания = `completed_at - assigned_at`

---

### 4.4. `CANCELED` — заявка отменена

**Триггер:** `POST /requests/{id}/cancel` (клиент + session-токен, любой статус кроме COMPLETED/CANCELED)  
**Кто публикует:** `RequestService.cancelRequest()`

```json
{
  "type": "CANCELED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   null,
  "assignedUserName": null,
  "reason":    null,
  "timestamp": 1708700120000
}
```

> Если заявка была в `ASSIGNED` перед отменой — `assignedUserId` будет заполнен, и User Module должен вернуть консультанту статус `ACTIVE`.

---

### 4.5. `REASSIGNED` — клиент сменил консультанта

**Триггер:** `POST /requests/{id}/reassign?reason=...` (клиент + session, >3 мин с момента назначения)  
**Кто публикует:** `RequestService.reassignRequest()`  
**Статус заявки:** `ASSIGNED` → `CREATED` (заявка снова в очереди)

```json
{
  "type": "REASSIGNED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   null,
  "assignedUserName": null,
  "reason":    "CONSULTANT_LATE",
  "timestamp": 1708700240000
}
```

> **Важно:** `assignedUserId` и `assignedUserName` в событии `null` — потому что к моменту публикации поля уже сброшены (`request.setAssignedUser(null)`).  
> **Проблема:** User Module не знает, кого освободить. **Нужно передавать предыдущего консультанта в `reason` или добавить поле `previousUserId` в `RequestEvent`.**

---

### 4.6. `REMINDED` — клиент напомнил консультанту

**Триггер:** `POST /requests/{id}/remind` (клиент + session, >1 мин после назначения)  
**Кто публикует:** `RequestService.remindRequest()`  
**Статус заявки:** не меняется (остаётся `ASSIGNED`)

```json
{
  "type": "REMINDED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "assignedUserName": "Иван Петров",
  "reason":    null,
  "timestamp": 1708700180000
}
```

**Что делают потребители (план):**
- Notification Module → повторный push конкретному консультанту (`assignedUserId`): «Клиент ждёт, поторопитесь»

---

### 4.7. `WAITING` — заявка ждёт >3 минут (уровень 1)

**Триггер:** `RequestSchedulingService.checkRequestSla()`, каждую минуту  
**Кто публикует:** `RequestSchedulingService`  
**Статус заявки:** остаётся `CREATED`, `escalation_level = 1`

```json
{
  "type": "WAITING",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   null,
  "assignedUserName": null,
  "reason":    null,
  "timestamp": 1708700190000
}
```

**Что делают потребители (план):**
- Notification Module → мягкое напоминание всем консультантам отдела: «Клиент ждёт уже 3 минуты»

---

### 4.8. `ESCALATED` — SLA нарушен (уровень 2, >5 минут)

**Триггер:** `RequestSchedulingService.checkRequestSla()`, каждую минуту  
**Кто публикует:** `RequestSchedulingService`  
**Статус заявки:** `CREATED` → `ESCALATED`, `escalation_level = 2`

```json
{
  "type": "ESCALATED",
  "requestId": "550e8400-e29b-41d4-a716-446655440001",
  "storeId":   "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "departmentId":   "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "departmentName": "Электроника",
  "assignedUserId":   null,
  "assignedUserName": null,
  "reason":    null,
  "timestamp": 1708700310000
}
```

**Что делают потребители (план):**
- Notification Module → **алерт менеджеру**: «⚠ В отделе Электроника никто не отвечает уже 5 минут!»

---

## 5. Описание полей `RequestEvent`

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `type` | `String` | ✅ | Тип события. Одно из: `CREATED`, `ASSIGNED`, `COMPLETED`, `CANCELED`, `REASSIGNED`, `REMINDED`, `WAITING`, `ESCALATED` |
| `requestId` | `UUID` | ✅ | ID заявки. Используется как **Kafka-ключ** — гарантирует порядок событий одной заявки в partition |
| `storeId` | `UUID` | ✅ | ID магазина. Позволяет фильтровать события по точке продаж |
| `departmentId` | `UUID` | ✅ | ID отдела. Нужен для адресных push-уведомлений консультантам отдела |
| `departmentName` | `String` | ✅ | Название отдела. **Денормализация** — потребитель не делает запрос в БД |
| `assignedUserId` | `UUID` | ❌ | ID консультанта. Заполнен при `ASSIGNED`, `COMPLETED`, `REMINDED`. `null` в остальных |
| `assignedUserName` | `String` | ❌ | Имя + фамилия консультанта. **Денормализация** — для push: «К вам идёт Иван» |
| `reason` | `String` | ❌ | Причина. Заполняется в `REASSIGNED` (передаётся клиентом). В остальных — `null` |
| `timestamp` | `Long` | ✅ | `System.currentTimeMillis()` в момент публикации. Для аналитики и дедупликации |

---

## 6. Известные проблемы и рекомендации

### 🔴 Проблема: в `REASSIGNED` не известно кого освободить
В событии `REASSIGNED` поля `assignedUserId` и `assignedUserName` уже `null` (пользователь сброшен до публикации). User Module не знает, чей статус менять на `ACTIVE`.

**Решение:** Сохранять предыдущего пользователя перед сбросом и добавить поле в событие:
```java
// В RequestService.reassignRequest():
UUID previousUserId = request.getAssignedUser() != null
    ? request.getAssignedUser().getId() : null;
// ... сброс полей ...
publishEvent(saved, RequestEvent.TYPE_REASSIGNED, reason, previousUserId);
```
И добавить поле `previousAssignedUserId` в `RequestEvent`.

### 🟡 Рекомендация: добавить `requestStatus` в payload
Потребителям иногда нужно знать итоговый статус (например Analytics). Сейчас его нет в событии — нужен дополнительный запрос к БД.

### 🟡 Рекомендация: headers для идемпотентности
При двойной доставке потребитель должен уметь пропускать уже обработанные события. Добавить в Kafka headers:
```
eventId: UUID  ← уникальный ID события (можно брать requestId + type + timestamp)
source: request-module
```

---

## 7. Как наблюдать события (мониторинг)

### Через IntelliJ IDEA (Ultimate / плагин Kafka)
1. **Streaming Data** → **+** → Bootstrap: `localhost:9092`
2. Правый клик на `request-events` → **Browse Messages**

### Через Docker (терминал)

```bash
# Список топиков
docker exec -it retailhub-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Читать все события с начала
docker exec -it retailhub-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic request-events \
  --from-beginning \
  --property print.key=true \
  --property key.separator=" → "
```

Пример вывода:
```
550e8400-e29b-41d4-a716-446655440001 → {"type":"CREATED","requestId":"550e8400-...","departmentName":"Электроника",...}
550e8400-e29b-41d4-a716-446655440001 → {"type":"ASSIGNED","assignedUserName":"Иван Петров",...}
550e8400-e29b-41d4-a716-446655440001 → {"type":"COMPLETED",...}
```
