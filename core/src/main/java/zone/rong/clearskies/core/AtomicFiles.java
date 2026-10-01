/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;

/**
 * Writes a file by replacing it with a sibling temp, using an atomic move when the filesystem
 * supports it so a failed write cannot leave a truncated target.
 */
public final class AtomicFiles {

    private AtomicFiles() { }

    public static void writeString(Path file, String text, Charset charset) throws IOException {
        // Replacing a symlink would detach it from the file it points at, so write the real file.
        Path target = Files.exists(file) ? file.toRealPath() : file.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("cannot write " + file + " without a parent directory");
        }
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, target.getFileName().toString() + ".", ".tmp");
        try {
            Files.writeString(temp, text, charset);
            // The temp file is created owner-only, and the move would carry that onto the target.
            if (Files.exists(target) && Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(target));
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

}
