package ru.retailhub.notification.websocket;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.security.Principal;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class StompPrincipal implements Principal {

    private final UUID userId;
    private final String role;
    private final UUID storeId;

    @Override
    public String getName() {
        return userId.toString();
    }
}
