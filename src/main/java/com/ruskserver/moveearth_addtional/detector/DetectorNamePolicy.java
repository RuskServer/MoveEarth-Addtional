package com.ruskserver.moveearth_addtional.detector;

/** Validation and normalization rules for player-defined detector names. */
public final class DetectorNamePolicy {
    public static final int MAX_LENGTH = 32;

    private DetectorNamePolicy() {
    }

    public static Validation validate(String input) {
        if (input == null) {
            return new Validation(false, "", "名称を読み取れませんでした。");
        }

        String normalized = input.trim();
        if (normalized.length() > MAX_LENGTH) {
            return new Validation(false, normalized, "名称は" + MAX_LENGTH + "文字以内で入力してください。");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (Character.isISOControl(character) || character == '§') {
                return new Validation(false, normalized, "名称に改行・制御文字・装飾コードは使用できません。");
            }
        }
        return new Validation(true, normalized, "");
    }

    public record Validation(boolean valid, String normalized, String errorMessage) {
    }
}
