package ru.retailhub.request.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.model.ClientRequestView;
import ru.retailhub.model.ServiceRequest;
import ru.retailhub.model.ServiceRequestAssignedUser;
import ru.retailhub.request.entity.Request;
import ru.retailhub.request.entity.RequestStatus;
import ru.retailhub.user.entity.User;

@Mapper(componentModel = "spring")
public interface RequestMapper {

    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "isEscalated", expression = "java(request.getStatus() == ru.retailhub.request.entity.RequestStatus.ESCALATED || request.getStatus() == ru.retailhub.request.entity.RequestStatus.WAITING)")
    @Mapping(target = "assignedUser", source = "assignedUser")
    ServiceRequest toDto(Request request);

    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    ServiceRequestAssignedUser toUserDto(User user);

    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "consultantName", expression = "java(request.getAssignedUser() != null ? request.getAssignedUser().getFirstName() + \" \" + request.getAssignedUser().getLastName() : null)")
    @Mapping(target = "canRemind", expression = "java(calculateCanRemind(request))")
    @Mapping(target = "canReassign", expression = "java(calculateCanReassign(request))")
    @Mapping(target = "clientSessionToken", source = "clientSessionToken")
    @Mapping(target = "status", source = "status")
    ClientRequestView toClientView(Request request);

    default boolean calculateCanRemind(Request request) {
        if (request.getStatus() != RequestStatus.ASSIGNED
                || request.getAssignedAt() == null) {
            return false;
        }
        return request.getAssignedAt().plusMinutes(1).isBefore(java.time.OffsetDateTime.now());
    }

    default boolean calculateCanReassign(Request request) {
        if (request.getStatus() != RequestStatus.ASSIGNED
                || request.getAssignedAt() == null) {
            return false;
        }
        return request.getAssignedAt().plusMinutes(3).isBefore(java.time.OffsetDateTime.now());
    }
}
