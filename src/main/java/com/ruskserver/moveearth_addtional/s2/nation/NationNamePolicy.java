package com.ruskserver.moveearth_addtional.s2.nation;

import java.util.Locale;

public final class NationNamePolicy {
    public static final int MIN_NAME_LENGTH = 3;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MIN_TAG_LENGTH = 2;
    public static final int MAX_TAG_LENGTH = 5;

    private NationNamePolicy() {
    }

    public static Validation validate(String rawName, String rawTag) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        String tag = rawTag == null ? "" : rawTag.trim().toUpperCase(Locale.ROOT);
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            return new Validation(false, name, tag, "name_length");
        }
        for (int index = 0; index < name.length(); index++) {
            char value = name.charAt(index);
            if (!Character.isLetterOrDigit(value) && value != ' ' && value != '_' && value != '-') {
                return new Validation(false, name, tag, "name_characters");
            }
        }
        if (tag.length() < MIN_TAG_LENGTH || tag.length() > MAX_TAG_LENGTH) {
            return new Validation(false, name, tag, "tag_length");
        }
        for (int index = 0; index < tag.length(); index++) {
            char value = tag.charAt(index);
            if (!(value >= 'A' && value <= 'Z') && !(value >= '0' && value <= '9')) {
                return new Validation(false, name, tag, "tag_characters");
            }
        }
        return new Validation(true, name, tag, "ok");
    }

    public static String normalizedName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public record Validation(boolean valid, String name, String tag, String reason) {
    }
}
