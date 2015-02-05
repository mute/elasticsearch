/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.engine;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.lease.Releasable;
import org.elasticsearch.index.store.Store;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * RecoveryCounter keeps tracks of the number of ongoing recoveries for a
 * particular {@link Store}
 */
public class RecoveryCounter implements Releasable {

    private final Store store;

    RecoveryCounter(Store store) {
        this.store = store;
    }

    private final AtomicInteger onGoingRecoveries = new AtomicInteger();

    void startRecovery() {
        store.incRef();
        onGoingRecoveries.incrementAndGet();
    }

    public int get() {
        return onGoingRecoveries.get();
    }

    /**
     * End the recovery counter by decrementing the store's ref and the ongoing recovery counter
     * @return number of ongoing recoveries remaining
     */
    int endRecovery() throws ElasticsearchException {
        store.decRef();
        int left = onGoingRecoveries.decrementAndGet();
        assert onGoingRecoveries.get() >= 0 : "ongoingRecoveries must be >= 0 but was: " + onGoingRecoveries.get();
        return left;
    }

    @Override
    public void close() throws ElasticsearchException {
        endRecovery();
    }
}
