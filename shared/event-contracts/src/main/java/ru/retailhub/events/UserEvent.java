package ru.retailhub.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserEvent extends BaseEvent {

    public static final String TYPE_USER_CREATED = "USER_CREATED";
    public static final String TYPE_USER_UPDATED = "USER_UPDATED";
    public static final String TYPE_USER_DELETED = "USER_DELETED";
    public static final String TYPE_USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    public static final String TYPE_SHIFT_STARTED = "SHIFT_STARTED";
    public static final String TYPE_SHIFT_ENDED = "SHIFT_ENDED";
    public static final String TYPE_DEPARTMENT_ASSIGNMENT_CHANGED = "DEPARTMENT_ASSIGNMENT_CHANGED";

    private UUID userId;
    private UUID storeId;
    private String phoneNumber;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private String role;
    private String currentStatus;
    private List<UUID> departmentIds;
}
