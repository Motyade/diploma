package ru.retailhub.request.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.retailhub.model.ClientRequestView;
import ru.retailhub.model.ServiceRequest;
import ru.retailhub.model.ServiceRequestAssignedUser;
import ru.retailhub.request.entity.Request;
import ru.retailhub.user.entity.User;

@Mapper(componentModel = "spring")
public interface RequestMapper {

    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "isEscalated", source = "escalationLevel", qualifiedByName = "mapEscalated")
    @Mapping(target = "assignedUser", source = "assignedUser")
    ServiceRequest toDto(Request request);

    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    ServiceRequestAssignedUser toUserDto(User user);

    @Named("mapEscalated")
    default Boolean mapEscalated(int level) {
        return level > 0;
    }

    @Mapping(target = "departmentName", source = "department.name")
    @Mapping(target = "consultantName", expression = "java(request.getAssignedUser() != null ? request.getAssignedUser().getFirstName() + \" \" + request.getAssignedUser().getLastName() : null)")
    @Mapping(target = "canRemind", expression = "java(false)") 
    @Mapping(target = "canReassign", expression = "java(false)") 
    @Mapping(target = "clientSessionToken", source = "clientSessionToken")
    ClientRequestView toClientView(Request request);
}
