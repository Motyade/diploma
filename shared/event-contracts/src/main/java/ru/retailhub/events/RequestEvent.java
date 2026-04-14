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
public class RequestEvent extends BaseEvent {

    public static final String TYPE_CREATED = "REQUEST_CREATED";
    public static final String TYPE_ASSIGNED = "REQUEST_ASSIGNED";
    public static final String TYPE_COMPLETED = "REQUEST_COMPLETED";
    public static final String TYPE_CANCELED = "REQUEST_CANCELED";
    public static final String TYPE_REASSIGNED = "REQUEST_REASSIGNED";
    public static final String TYPE_ESCALATED = "REQUEST_ESCALATED";
    public static final String TYPE_WAITING = "REQUEST_WAITING";
    public static final String TYPE_REMINDED = "REQUEST_REMINDED";

    private UUID requestId;
    private UUID storeId;
    private UUID departmentId;
    private String departmentName;
    private UUID assignedUserId;
    private String assignedUserName;
    private UUID previousAssignedUserId;
    private String status;
    private String reason;
    private UUID clientSessionToken;
}
