package ru.retailhub.request.entity;

public enum RequestStatus {
    CREATED, // Заявка создана, ищем консультанта
    ASSIGNED, // Консультант взял заявку
    COMPLETED, // Заявка выполнена успешно
    ESCALATED // Заявка просрочена, менеджер уведомлен
}
