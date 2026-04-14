package ru.retailhub.user.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.retailhub.user.entity.Shift;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShiftMapper {

    private final UserRepository userRepository;

    public Map<String, Object> toMap(Shift shift) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", shift.getId());
        map.put("user_id", shift.getUserId());
        map.put("started_at", shift.getStartedAt());
        map.put("ended_at", shift.getEndedAt());

        userRepository.findById(shift.getUserId()).ifPresent(user ->
                map.put("user_name", user.getFirstName() + " " + user.getLastName()));

        return map;
    }
}
