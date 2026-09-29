/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.cli;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The packaged CLI must stay on the documented Java 21 floor. */
class RuntimeFloorTest {

    @Test
    void mainCompilesToJava21() throws IOException {
        assertThat(majorVersion(Main.class)).as("CLI must be Java 21 bytecode").isEqualTo(65);
    }

    private static int majorVersion(Class<?> type) throws IOException {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing class file: " + resource);
            }
            byte[] header = in.readNBytes(8);
            if (header.length < 8 || header[0] != (byte) 0xCA || header[1] != (byte) 0xFE || header[2] != (byte) 0xBA || header[3] != (byte) 0xBE) {
                throw new IOException("not a class file: " + resource);
            }
            return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
        }
    }

}
