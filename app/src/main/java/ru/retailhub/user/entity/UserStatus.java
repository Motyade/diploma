package ru.retailhub.user.entity;

/**
 * Оперативный статус консультанта.
 *   OFFLINE — не на смене, не получает заявки
 *   ACTIVE  — на смене, свободен, может принимать заявки
 *   BUSY    — обслуживает клиента (заявка в статусе ASSIGNED)
 */
public enum UserStatus {
    OFFLINE,
    ACTIVE,
    BUSY
}
