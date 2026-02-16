package ru.retailhub.user.entity;

/**
 * Роль пользователя в системе.
 *   MANAGER    — управляет магазином, создаёт консультантов, видит аналитику
 *   CONSULTANT — принимает и обрабатывает заявки клиентов
 */
public enum Role {
    MANAGER,
    CONSULTANT
}
