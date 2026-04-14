package ru.retailhub.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseEvent {
    private UUID eventId;
    private String eventType;
    private String source;
    private long timestamp;
}
