/*
 * Copyright (c) 2026 CleanroomMC contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */

package zone.rong.clearskies.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/** Expands star imports in the project's Java sources in place. */
@Mojo(name = "apply", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class ApplyMojo extends AbstractClearSkiesMojo {

    @Override
    protected boolean checkOnly() {
        return false;
    }

}
