package ru.retailhub.store.controller.dto;

public record QrScanResponse(
        String departmentName,
        String storeName,
        boolean isValid
) {}
