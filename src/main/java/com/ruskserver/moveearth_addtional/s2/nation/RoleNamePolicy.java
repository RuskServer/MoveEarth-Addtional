package com.ruskserver.moveearth_addtional.s2.nation;

public final class RoleNamePolicy {
    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 24;

    private RoleNamePolicy() {
    }

    public static Validation validate(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.length() < MIN_LENGTH || name.length() > MAX_LENGTH) {
            return new Validation(false, name, "length");
        }
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (!Character.isLetterOrDigit(value) && value != ' ' && value != '_' && value != '-') {
                return new Validation(false, name, "characters");
            }
        }
        return new Validation(true, name, "ok");
    }

    public record Validation(boolean valid, String name, String reason) {
    }
}
