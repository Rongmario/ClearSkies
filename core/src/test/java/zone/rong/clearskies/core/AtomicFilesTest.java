/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class AtomicFilesTest {

    @Test
    void keepsThePermissionsOfTheReplacedFile(@TempDir Path temp) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path file = temp.resolve("A.java");
        Files.writeString(file, "old\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-rw-r--"));

        AtomicFiles.writeString(file, "new\n", StandardCharsets.UTF_8);

        assertThat(Files.readString(file)).isEqualTo("new\n");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-rw-r--");
    }

    @Test
    void writesThroughASymlinkAndKeepsTheLink(@TempDir Path temp) throws Exception {
        Path real = temp.resolve("real/A.java");
        Files.createDirectories(real.getParent());
        Files.writeString(real, "old\n");
        Path link = temp.resolve("A.java");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "symlinks unavailable: " + e);
        }

        AtomicFiles.writeString(link, "new\n", StandardCharsets.UTF_8);

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(real)).isEqualTo("new\n");
    }

}
