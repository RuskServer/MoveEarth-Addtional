package com.ruskserver.moveearth_addtional.client.gunpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunPackArchiveTest {
    @TempDir
    Path tempDirectory;

    @Test
    void readsNamespaceFromZipRootMetadata() throws IOException {
        Path archive = createZip("pack.zip", "{\"namespace\":\"CCRP\"}", "gunpack.meta.json");

        assertEquals("ccrp", GunPackArchive.readNamespace(archive).orElseThrow());
    }

    @Test
    void rejectsMetadataNestedBelowZipRoot() throws IOException {
        Path archive = createZip("pack.zip", "{\"namespace\":\"fmic\"}", "folder/gunpack.meta.json");

        assertTrue(GunPackArchive.readNamespace(archive).isEmpty());
    }

    @Test
    void validatesAnInternalTemporaryArchiveWithoutTreatingItAsInstalled() throws IOException {
        Path archive = createZip("install.part", "{\"namespace\":\"fmic\"}", "gunpack.meta.json");

        assertTrue(GunPackArchive.readNamespace(archive).isEmpty());
        assertEquals("fmic", GunPackArchive.readZipNamespace(archive).orElseThrow());
    }

    @Test
    void readsNamespaceFromUnpackedDirectory() throws IOException {
        Path packDirectory = Files.createDirectory(tempDirectory.resolve("unpacked"));
        Files.writeString(packDirectory.resolve("gunpack.meta.json"),
                "{\"namespace\":\"cib\"}", StandardCharsets.UTF_8);

        assertEquals("cib", GunPackArchive.readNamespace(packDirectory).orElseThrow());
    }

    @Test
    void rejectsInvalidNamespace() throws IOException {
        Path archive = createZip("pack.zip", "{\"namespace\":\"../unsafe\"}", "gunpack.meta.json");

        assertTrue(GunPackArchive.readNamespace(archive).isEmpty());
    }

    private Path createZip(String fileName, String metadata, String entryName) throws IOException {
        Path archive = tempDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry(entryName));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return archive;
    }
}
