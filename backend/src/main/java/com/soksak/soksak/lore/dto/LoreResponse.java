package com.soksak.soksak.lore.dto;

import com.soksak.soksak.lore.Lore;

public record LoreResponse(
        Long id, String title, String keys, String content,
        boolean alwaysOn, boolean enabled, int priority
) {
    public static LoreResponse from(Lore lore) {
        return new LoreResponse(lore.getId(), lore.getTitle(), lore.getKeys(), lore.getContent(),
                lore.isAlwaysOn(), lore.isEnabled(), lore.getPriority());
    }
}