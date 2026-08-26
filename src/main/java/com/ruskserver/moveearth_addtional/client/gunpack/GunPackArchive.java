package com.ruskserver.moveearth_addtional.client.gunpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class GunPackArchive {
    static final String META_FILE = "gunpack.meta.json";
    private static final long MAX_META_BYTES = 64L * 1024L;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");

    private GunPackArchive() {
    }

    static Optional<String> readNamespace(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Path metadata = path.resolve(META_FILE);
            if (!Files.isRegularFile(metadata)) {
                return Optional.empty();
            }
            if (Files.size(metadata) > MAX_META_BYTES) {
                throw new IOException("GunPack metadata is too large: " + metadata.getFileName());
            }
            try (Reader reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8)) {
                return parseNamespace(reader);
            }
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(path) || !fileName.endsWith(".zip")) {
            return Optional.empty();
        }
        return readZipNamespace(path);
    }

    static Optional<String> readZipNamespace(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry metadata = zip.getEntry(META_FILE);
            if (metadata == null || metadata.isDirectory()) {
                return Optional.empty();
            }
            if (metadata.getSize() > MAX_META_BYTES) {
                throw new IOException("GunPack metadata is too large: " + path.getFileName());
            }
            try (InputStream stream = zip.getInputStream(metadata);
                 Reader reader = limitedMetadataReader(stream, path.getFileName().toString())) {
                return parseNamespace(reader);
            }
        }
    }

    private static Reader limitedMetadataReader(InputStream stream, String archiveName) throws IOException {
        byte[] metadata = stream.readNBytes((int) MAX_META_BYTES + 1);
        if (metadata.length > MAX_META_BYTES) {
            throw new IOException("GunPack metadata is too large: " + archiveName);
        }
        return new StringReader(new String(metadata, StandardCharsets.UTF_8));
    }

    private static Optional<String> parseNamespace(Reader reader) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement namespaceElement = root.get("namespace");
            if (namespaceElement == null || !namespaceElement.isJsonPrimitive()
                    || !namespaceElement.getAsJsonPrimitive().isString()) {
                return Optional.empty();
            }
            String namespace = namespaceElement.getAsString().trim().toLowerCase(Locale.ROOT);
            return NAMESPACE.matcher(namespace).matches() ? Optional.of(namespace) : Optional.empty();
        } catch (RuntimeException exception) {
            throw new IOException("Invalid " + META_FILE, exception);
        }
    }
}
