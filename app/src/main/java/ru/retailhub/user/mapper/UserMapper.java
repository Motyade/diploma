package ru.retailhub.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.model.UserProfile;
import ru.retailhub.model.UserProfileDepartmentsInner;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "storeId", source = "store.id")
    @Mapping(target = "departments", source = "departmentAssignments")
    UserProfile toUserProfile(User user);

    @Mapping(target = "id", source = "department.id")
    @Mapping(target = "name", source = "department.name")
    UserProfileDepartmentsInner toDepartmentDto(DepartmentEmployee departmentEmployee);
}
