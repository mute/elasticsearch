/*
 * Licensed to Elasticsearch under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elasticsearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.elasticsearch.cluster;

import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.unit.TimeValue;

/**
 * An extension interface to {@link ClusterStateUpdateTask} that allows to be notified when
 * all the nodes have acknowledged a cluster state update request
 */
public interface AckedClusterStateUpdateTask extends TimeoutClusterStateUpdateTask {

    /**
     * Called to determine which nodes the acknowledgement is expected from
     * @param discoveryNode a node
     * @return true if the node is expected to send ack back, false otherwise
     */
    boolean mustAck(DiscoveryNode discoveryNode);

    /**
     * Called once all the nodes have acknowledged the cluster state update request. Must be
     * very lightweight execution, since it gets executed on the cluster service thread.
     * @param t optional error that might have been thrown
     */
    void onAllNodesAcked(@Nullable Throwable t);

    /**
     * Called once the acknowledgement timeout defined by
     * {@link AckedClusterStateUpdateTask#ackTimeout()} has expired
     */
    void onAckTimeout();

    /**
     * Acknowledgement timeout, maximum time interval to wait for acknowledgements
     */
    TimeValue ackTimeout();
}
