package com.soksak.soksak.lore.dto;

public record UpdateLoreRequest(
        String title,
        String keys,
        String content,
        boolean alwaysOn,
        int priority
) {
}
