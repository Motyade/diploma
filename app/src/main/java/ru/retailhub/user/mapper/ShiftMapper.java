package ru.retailhub.user.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.retailhub.model.Shift;
import ru.retailhub.user.entity.User;

/**
 * MapStruct маппер: Shift-сущность → Shift OpenAPI DTO.
 *
 * userName составляем из firstName + " " + lastName.
 */
@Mapper(componentModel = "spring")
public interface ShiftMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", expression = "java(fullName(shift.getUser()))")
    Shift toDto(ru.retailhub.user.entity.Shift shift);

    default String fullName(User user) {
        if (user == null)
            return null;
        return user.getFirstName() + " " + user.getLastName();
    }
}
