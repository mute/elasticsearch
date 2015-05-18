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

package org.elasticsearch.http.netty.pipelining;

import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;

/**
 *
 */
public class HttpPipelinedResponse implements Comparable<HttpPipelinedResponse> {

    private final DefaultFullHttpResponse response;
    private final ChannelPromise promise;
    private final int sequenceId;

    public HttpPipelinedResponse(DefaultFullHttpResponse response, ChannelPromise promise, int sequenceId) {
        this.response = response;
        this.promise = promise;
        this.sequenceId = sequenceId;
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public DefaultFullHttpResponse getResponse() {
        return response;
    }

    public ChannelPromise getPromise() {
        return promise;
    }

    @Override
    public int compareTo(HttpPipelinedResponse other) {
        return sequenceId - other.getSequenceId();
    }
}
