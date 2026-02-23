package ru.retailhub.request.entity;

public enum RequestStatus {
    CREATED,
    WAITING, // Клиент ждёт > 3 мин, консультант не назначен (мягкое предупреждение)
    ESCALATED, // Клиент ждёт > 5 мин — SLA нарушен, уведомляется менеджер
    ASSIGNED,
    COMPLETED,
    CANCELED
}
