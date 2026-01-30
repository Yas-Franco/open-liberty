/*******************************************************************************
 * Copyright (c) 2026 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package test.jakarta.concurrency31.web;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

/**
 * Task that takes an optional continue latch to control when the task finishes.
 * Returns a boolean representing if the task was run on a virtual thread.
 */
public class VirtualCheckTask implements Callable<Boolean> {

    Optional<CountDownLatch> continueLatch;

    public VirtualCheckTask() {
        this.continueLatch = Optional.ofNullable(null);
    }

    public VirtualCheckTask(CountDownLatch continueLatch) {
        this.continueLatch = Optional.ofNullable(continueLatch);
    }

    @Override
    public Boolean call() throws Exception {
        if (continueLatch.isPresent())
            continueLatch.get().await();
        return (boolean) Thread.class.getMethod("isVirtual").invoke(Thread.currentThread());
    }

}
