# Модель данных — RetailHub

Подробное описание каждой таблицы, каждого поля и связей между ними.

---

## Диаграмма связей (ER Summary)

```
stores 1──* departments 1──* qr_codes
  │                │
  │                └──* department_employees *──1 users
  │                                               │
  ├──* users 1──* shifts                          │
  │         1──* user_devices                     │
  │         1──* notifications                    │
  │                                               │
  └──* requests *──1 users (assigned_user_id) ────┘
```

---

## 1. `stores` — Физические магазины

Центральная сущность Multi-tenant архитектуры. Каждый магазин — изолированный tenant.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Уникальный идентификатор. |
| `name` | VARCHAR(255) | ✅ | Название: "МВидео Мега Белая Дача". |
| `address` | TEXT | ✅ | Полный адрес магазина. |
| `timezone` | VARCHAR(50) | ✅ | Часовой пояс (IANA), например `Europe/Moscow`. Нужен для корректного отображения времени смен. |
| `created_at` | TIMESTAMPTZ | auto | Дата создания записи. |
| `updated_at` | TIMESTAMPTZ | auto | Дата обновления. |

---

## 2. `departments` — Отделы магазина

Логическое деление магазина. К отделу привязываются QR-коды и компетенции консультантов.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `store_id` | UUID (FK → stores) | ✅ | Магазин. CASCADE при удалении. |
| `name` | VARCHAR(255) | ✅ | Название: "Телевизоры", "Кухни". |
| `created_at` | TIMESTAMPTZ | auto | Дата создания. |

---

## 3. `users` — Сотрудники

Менеджеры и консультанты. Менеджер создаёт аккаунты, консультант получает логин.
**При увольнении запись удаляется из БД** (hard delete, каскадно чистятся привязки).

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `store_id` | UUID (FK → stores) | ✅ | Магазин. RESTRICT — нельзя удалить магазин с сотрудниками. |
| `phone_number` | VARCHAR(20), UNIQUE | ✅ | Номер телефона для логина. Формат: "+79991234567". |
| `password_hash` | VARCHAR(255) | ✅ | BCrypt хеш. Не возвращается в API. |
| `first_name` | VARCHAR(100) | ✅ | Имя. Показывается клиенту: "Консультант **Иван** идёт к вам". |
| `last_name` | VARCHAR(100) | ✅ | Фамилия. Для менеджера. |
| `role` | VARCHAR(20) | ✅ | `MANAGER` или `CONSULTANT`. |
| `current_status` | VARCHAR(20) | ✅ | `OFFLINE` / `ACTIVE` / `BUSY` — определяет, кому можно отправить заявку. |
| `created_at` | TIMESTAMPTZ | auto | Дата создания. |
| `updated_at` | TIMESTAMPTZ | auto | Дата обновления. |

**`current_status` — зачем?** При создании заявки система ищет консультантов с `ACTIVE` в нужном отделе (через `department_employees`). `BUSY` = уже обслуживает. `OFFLINE` = не на смене.

---

## 4. `department_employees` — Матрица компетенций

Many-to-many: какой консультант в каких отделах может работать.
Один консультант → несколько отделов. Один отдел → несколько консультантов.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `user_id` | UUID (FK → users) | ✅ | Консультант. CASCADE при удалении. |
| `department_id` | UUID (FK → departments) | ✅ | Отдел. CASCADE при удалении. |
| `assigned_at` | TIMESTAMPTZ | auto | Когда назначена компетенция. |

**UNIQUE** `(user_id, department_id)`.

**Как работает диспетчеризация**: Клиент сканирует QR отдела "Телевизоры" → система ищет в `department_employees` всех `user_id` с этим `department_id` → фильтрует по `users.current_status = 'ACTIVE'` → отправляет push.

---

## 5. `shifts` — Смены консультантов

Простая модель: консультант нажимает "Начать смену" / "Закончить смену".
Каждая запись — один рабочий сеанс.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `user_id` | UUID (FK → users) | ✅ | Консультант. CASCADE. |
| `store_id` | UUID (FK → stores) | ✅ | Магазин (денормализовано для быстрой аналитики). |
| `started_at` | TIMESTAMPTZ | ✅ | Время начала. При clock-in → пользователь `ACTIVE`. |
| `ended_at` | TIMESTAMPTZ | — | Время окончания. NULL = смена активна. При clock-out → пользователь `OFFLINE`. |
| `created_at` | TIMESTAMPTZ | auto | Дата создания. |

---

## 6. `qr_codes` — QR-коды (точки входа)

Физические QR-коды в магазине. Привязаны к отделу.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `department_id` | UUID (FK → departments) | ✅ | Отдел. CASCADE. |
| `token` | UUID, UNIQUE | auto | Публичный токен в URL: `https://retailhub.ru/scan/{token}`. |
| `label` | VARCHAR(255) | — | Расположение: "Стеллаж 3, ряд B". |
| `is_active` | BOOLEAN | ✅ | Деактивированный QR не создаёт заявки. |
| `created_at` | TIMESTAMPTZ | auto | Дата создания. |

**Зачем `token` ≠ `id`**: Если QR скомпрометирован, деактивируем и создаём новый. Внутренний `id` остаётся.

---

## 7. `requests` — Заявки на обслуживание

Ядро системы. Каждая запись — один клиент, которому нужна помощь.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `store_id` | UUID (FK → stores) | ✅ | Магазин. |
| `department_id` | UUID (FK → departments) | ✅ | Отдел (из QR). |
| `qr_code_id` | UUID (FK → qr_codes) | — | QR-код источник. |
| `assigned_user_id` | UUID (FK → users) | — | Консультант. NULL пока не взял. SET NULL если удалён. |
| `status` | VARCHAR(20) | ✅ | `CREATED` → `ASSIGNED` → `COMPLETED`. |
| `client_session_token` | UUID | auto | Токен для polling клиентом (не авторизован). |
| `created_at` | TIMESTAMPTZ | auto | Момент создания = сканирование QR. |
| `assigned_at` | TIMESTAMPTZ | — | Момент принятия. **Метрика**: `assigned_at - created_at` = время реакции. |
| `completed_at` | TIMESTAMPTZ | — | Момент завершения. **Метрика**: `completed_at - assigned_at` = время обслуживания. |
| `escalation_level` | INT | DEFAULT 0 | Уровень эскалации: 0=Norm, 1=Warning(3m), 2=Manager(5m). |
| `escalated_at` | TIMESTAMPTZ | — | Время отправки уведомления менеджеру. |

### Диаграмма статусов

```
  CREATED ──(консультант "Взять")──► ASSIGNED ──(консультант "Завершить")──► COMPLETED
                 ▲                       │
                 │                       │
                 └──(клиент "Сменить")───┘  (через ≥2 мин)
```

---

## 8. `user_devices` — FCM-токены

Для push-уведомлений через Firebase Cloud Messaging.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `user_id` | UUID (FK → users) | ✅ | Владелец. CASCADE. |
| `fcm_token` | VARCHAR(500) | ✅ | Токен Firebase. Обновляется при запуске приложения. |
| `device_info` | VARCHAR(255) | — | "Samsung Galaxy S24, Android 15". |
| `created_at` | TIMESTAMPTZ | auto | Дата регистрации. |

**UNIQUE** `(user_id, fcm_token)`.

---

## 9. `notifications` — Inbox уведомлений

Все уведомления хранятся в БД для inbox в приложении.

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `id` | UUID (PK) | auto | Идентификатор. |
| `user_id` | UUID (FK → users) | ✅ | Получатель. CASCADE. |
| `title` | VARCHAR(255) | ✅ | Заголовок: "Новая заявка в отделе Телевизоры". |
| `body` | TEXT | — | Тело с деталями. |
| `type` | VARCHAR(50) | ✅ | Тип: `REQUEST_NEW`, `REQUEST_ASSIGNED`, `REQUEST_REMINDER`, `SHIFT_REMINDER`. |
| `payload` | JSONB | — | Данные для навигации: `{"request_id": "uuid"}`. |
| `is_read` | BOOLEAN | ✅ | Прочитано ли. По умолчанию `false`. |
| `created_at` | TIMESTAMPTZ | auto | Дата создания. |
