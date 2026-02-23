# Руководство по тестированию жизненного цикла заявок

В этом документе описаны шаги для полной проверки реализованной функциональности модуля заявок (Requests).

## Предварительные условия
1. Запущен Docker Compose (PostgreSQL, Kafka).
2. Приложение запущено (Backend на порту 8080).
3. Используются данные из миграции `V2__initial_data.sql` (уже в БД).

---

## 🚀 Основной флоу (Счастливый путь)

### 1. Создание заявки (Клиент)
Имитация сканирования QR-кода в отделе "Обувь".
- **URL:** `POST http://localhost:8080/api/v1/requests`
- **Body:**
```json
{
  "qr_token": "770e8400-e29b-41d4-a716-446655440002"
}
```
- **Ожидаемый результат:** `201 Created`.
- **Важно:** Сохраните `id` (requestId) и `client_session_token` из ответа.
- **Kafka:** В топике `request-events` появится событие `TYPE: CREATED`.

### 2. Проверка статуса (Клиент - Polling)
- **URL:** `GET http://localhost:8080/api/v1/requests/{requestId}?session={client_session_token}`
- **Ожидаемый результат:** `200 OK`, статус `CREATED`.

### 3. Взятие заявки в работу (Консультант)
- **URL:** `POST http://localhost:8080/api/v1/requests/{requestId}/assign`
- **Ожидаемый результат:** `200 OK`, статус изменится на `ASSIGNED`.
- **Kafka:** Событие `TYPE: ASSIGNED`.

### 4. Завершение обслуживания (Консультант)
- **URL:** `POST http://localhost:8080/api/v1/requests/{requestId}/complete`
- **Ожидаемый результат:** `200 OK`, статус изменится на `COMPLETED`.
- **Kafka:** Событие `TYPE: COMPLETED`.

---

## 🔄 Альтернативные сценарии

### Сценарий А: Отмена заявки клиентом
1. Создайте новую заявку (шаг 1).
2. Отмените её:
   - **URL:** `POST http://localhost:8080/api/v1/requests/{requestId}/cancel?session={client_session_token}`
- **Ожидаемый результат:** `200 OK`, статус `CANCELED`.

### Сценарий Б: Смена консультанта (Reassign)
1. Создайте заявку и назначьте её (шаги 1 и 3).
2. Клиент нажимает "Сменить консультанта":
   - **URL:** `POST http://localhost:8080/api/v1/requests/{requestId}/reassign?session={client_session_token}&reason=LATE`
- **Ожидаемый результат:** `200 OK`. Статус возвращается в `CREATED`.
- **Kafka:** Событие `TYPE: REASSIGNED`. Поле `reason` заполнено.

---

## 🛡️ Проверка безопасности и ошибок

### 1. Проверка сессии (403 Forbidden)
Попробуйте получить статус или отменить заявку с неверным `session` токеном.
- **Ожидаемый результат:** `403 Forbidden`. Доступ к заявке разрешен только владельцу сессии.

### 2. Оптимистичная блокировка (409 Conflict)
1. Откройте в Swagger/Postman две вкладки.
2. В обеих введите `assign` для одной и той же заявки.
3. Нажмите "Send" почти одновременно (или по очереди).
- **Ожидаемый результат:** Первый запрос пройдет успешно (`200`), второй вернет ошибку (в зависимости от реализации — либо `409 Conflict`, либо `500` с сообщением об `ObjectOptimisticLockingFailureException`).

---

## 📊 Мониторинг Kafka

### Способ 1: Через IntelliJ IDEA (Рекомендуемый)
Если у тебя **Ultimate** версия или установлен плагин **Kafka**:
1. Открой вкладку **Streaming Data** или **Kafka** (обычно справа или снизу).
2. Нажми **+** (New Connection).
3. Bootstrap servers: `localhost:9092`.
4. В дереве появятся топики. Кликни правой кнопкой на `request-events` -> **Browse Messages**.
5. Новые события будут появляться там в реальном времени с подсветкой JSON.

### Способ 2: В контейнере (Через терминал)
Если хочешь по старинке через лог:

**1. Посмотреть список всех топиков:**
```bash
docker exec -it retailhub-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

**2. Читать события из топика в реальном времени:**
```bash
docker exec -it retailhub-kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic request-events --from-beginning
```
*(Чтобы выйти из режима чтения, нажми `Ctrl+C`)*
