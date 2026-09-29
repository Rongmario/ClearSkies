/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.core;

import zone.rong.clearskies.api.ExpandRequest;
import zone.rong.clearskies.api.ExpandResult;
import zone.rong.clearskies.api.StarExpander;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Finite seeded generator over review-sensitive shapes. Not an open-ended campaign.
 */
class StarExpanderFuzzTest {

    private static final long SEED = 0xC1EA15L;
    private static final int ITERATIONS = 64;

    @Test
    void seededShapesNeverCorruptFailedInput() {
        Random random = new Random(SEED);
        StarExpander expander = ClearSkies.defaultExpander();
        int seenFailed = 0;
        int seenExpanded = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            String name = name(random);
            String source = source(random);
            ExpandResult result = expander.expand(ExpandRequest.of(source).withName(name));
            switch (result.outcome()) {
                case FAILED -> {
                    seenFailed++;
                    assertThat(result.text()).as("FAILED must return the original source").isEqualTo(source);
                    assertThat(result.hasErrors()).isTrue();
                }
                case EXPANDED -> {
                    seenExpanded++;
                    assertThat(result.text().contains("class ") || result.text().contains("package ")).as(result.text()).isTrue();
                }
                default -> assertThat(result.text() != null).isTrue();
            }
        }
        assertThat(seenFailed > 0).as("seeded corpus should include malformed files").isTrue();
        assertThat(seenExpanded > 0).as("seeded corpus should include clean expansions").isTrue();
    }

    private static String name(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> "My File.java";
            case 1 -> "package-info.java";
            case 2 -> "Sample.java";
            default -> "A" + random.nextInt(99) + ".java";
        };
    }

    private static String source(Random random) {
        return switch (random.nextInt(8)) {
            case 0 -> "package sample;\n\nimport java.util.*;\n\nclass Sample { List x; }\n";
            case 1 -> "package sample;\n\nimport java./* KEEP */util.*;\n\nclass Sample { List x; }\n";
            case 2 -> "package sample;\n\nimport java.util.*;\n\nclass Sample { List x;\n";
            case 3 -> "package sample;\n\n\\u0069mport java.util.*;\n\nclass Sample { List x; }\n";
            case 4 -> "package sample;\n\nimport java.util.*;\nimport java.util.*;\n\nclass Sample { List x; }\n";
            case 5 -> "package sample;\n\nimport java.util.*;\n\n/** {@link List} */\nclass Sample {}\n";
            case 6 -> "@Deprecated\npackage sample;\n\nimport java.util.*;\n";
            default -> "import java.util.*; // keep\nclass Sample { int x; }\n";
        };
    }

}
