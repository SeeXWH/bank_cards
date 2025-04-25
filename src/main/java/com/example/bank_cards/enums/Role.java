package com.example.bank_cards.enums;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public enum Role {
    USER("User", 1, "Standard user with limited privileges, e.g., "
            + "placing orders, viewing profile."),
    ADMIN("Administrator", 3, "Full access to all system functions "
            + "including administration panels and critical operations.");

    private final String displayName;
    private final int accessLevel;
    private final String description;

    Role(String displayName, int accessLevel, String description) {
        this.displayName = displayName;
        this.accessLevel = accessLevel;
        this.description = description;
    }

}
