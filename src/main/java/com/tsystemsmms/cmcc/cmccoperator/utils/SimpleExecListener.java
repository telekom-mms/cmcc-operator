/*
 * Copyright (c) 2022. T-Systems Multimedia Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.tsystemsmms.cmcc.cmccoperator.utils;

import io.fabric8.kubernetes.client.dsl.ExecListener;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;

@Slf4j
public class SimpleExecListener  implements ExecListener {
    private final CountDownLatch latch = new CountDownLatch(1);

    @Getter
    private int code = -1;

    @Override
    public void onOpen() {
        log.debug("Starting process");
    }

    @Override
    public void onFailure(Throwable t, Response failureResponse) {
        log.debug("Process failed with {}: {}", failureResponse.code(), failureResponse.code(), t);
        latch.countDown();
    }

    @Override
    public void onClose(int code, String reason) {
        log.debug("Finished: {}, {}", code, reason);
        this.code = code;
        latch.countDown();
    }

    public void await() throws InterruptedException {
        latch.await();
    }

    public void awaitUninterruptable() {
        while (true) {
            try {
                await();
                return;
            } catch (InterruptedException e) {
                //
            }
        }
    }
}
