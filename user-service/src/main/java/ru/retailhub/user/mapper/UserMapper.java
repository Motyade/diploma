package ru.retailhub.user.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.retailhub.user.entity.DepartmentEmployee;
import ru.retailhub.user.entity.ReplicaDepartment;
import ru.retailhub.user.entity.User;
import ru.retailhub.user.repository.DepartmentEmployeeRepository;
import ru.retailhub.user.repository.ReplicaDepartmentRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final DepartmentEmployeeRepository departmentEmployeeRepository;
    private final ReplicaDepartmentRepository replicaDepartmentRepository;

    public Map<String, Object> toMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("phone_number", user.getPhoneNumber());
        map.put("first_name", user.getFirstName());
        map.put("last_name", user.getLastName());
        map.put("role", user.getRole());
        map.put("current_status", user.getCurrentStatus());
        map.put("store_id", user.getStoreId());
        map.put("created_at", user.getCreatedAt());

        List<DepartmentEmployee> assignments = departmentEmployeeRepository.findAllByUserId(user.getId());
        List<Map<String, Object>> departments = assignments.stream().map(de -> {
            Map<String, Object> dept = new LinkedHashMap<>();
            dept.put("id", de.getDepartmentId());
            replicaDepartmentRepository.findById(de.getDepartmentId())
                    .ifPresent(rd -> dept.put("name", rd.getName()));
            return dept;
        }).toList();
        map.put("departments", departments);

        return map;
    }
}
