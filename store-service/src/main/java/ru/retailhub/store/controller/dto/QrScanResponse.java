package ru.retailhub.store.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QrScanResponse(
        @JsonProperty("department_name") String departmentName,
        @JsonProperty("store_name") String storeName,
        @JsonProperty("is_valid") boolean isValid
) {}
