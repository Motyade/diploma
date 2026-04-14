package ru.retailhub.store.controller.dto;

public record UpdateStoreRequest(
        String name,
        String address,
        String timezone
) {}
