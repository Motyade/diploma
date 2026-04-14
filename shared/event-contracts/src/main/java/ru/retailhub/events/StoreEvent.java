package ru.retailhub.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class StoreEvent extends BaseEvent {

    public static final String TYPE_STORE_CREATED = "STORE_CREATED";
    public static final String TYPE_STORE_UPDATED = "STORE_UPDATED";
    public static final String TYPE_DEPARTMENT_CREATED = "DEPARTMENT_CREATED";
    public static final String TYPE_DEPARTMENT_UPDATED = "DEPARTMENT_UPDATED";
    public static final String TYPE_DEPARTMENT_DELETED = "DEPARTMENT_DELETED";
    public static final String TYPE_QR_CODE_CREATED = "QR_CODE_CREATED";
    public static final String TYPE_QR_CODE_DEACTIVATED = "QR_CODE_DEACTIVATED";

    private UUID storeId;
    private String storeName;
    private String storeAddress;
    private String storeTimezone;

    private UUID departmentId;
    private String departmentName;
    private String departmentDescription;

    private UUID qrCodeId;
    private UUID qrToken;
    private String qrLabel;
    private boolean qrActive;
}
