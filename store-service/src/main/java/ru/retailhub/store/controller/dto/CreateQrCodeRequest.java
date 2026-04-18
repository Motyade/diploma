package ru.retailhub.store.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateQrCodeRequest(
        @NotNull @JsonProperty("department_id") UUID departmentId,
        String label
) {}
