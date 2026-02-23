package ru.retailhub.request.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestEvent {
    private String type;
    private UUID requestId;
    private UUID storeId;
    private UUID departmentId;
    private String departmentName;
    private UUID assignedUserId;
    private String assignedUserName;
    private String reason;
    private Long timestamp;

    public static final String TYPE_CREATED = "CREATED";
    public static final String TYPE_ASSIGNED = "ASSIGNED";
    public static final String TYPE_COMPLETED = "COMPLETED";
    public static final String TYPE_CANCELED = "CANCELED";
    public static final String TYPE_REASSIGNED = "REASSIGNED";
    public static final String TYPE_ESCALATED = "ESCALATED";
    public static final String TYPE_WAITING = "WAITING";
    public static final String TYPE_REMINDED = "REMINDED";
}
