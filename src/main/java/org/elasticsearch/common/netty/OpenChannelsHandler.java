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

package org.elasticsearch.common.netty;

import io.netty.channel.*;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.metrics.CounterMetric;
import org.elasticsearch.common.util.concurrent.ConcurrentCollections;

import java.util.Set;

/**
 *
 */
@ChannelHandler.Sharable
public class OpenChannelsHandler extends ChannelInboundHandlerAdapter {

    final Set<Channel> openChannels = ConcurrentCollections.newConcurrentSet();
    final CounterMetric openChannelsMetric = new CounterMetric();
    final CounterMetric totalChannelsMetric = new CounterMetric();

    final ESLogger logger;

    public OpenChannelsHandler(ESLogger logger) {
        this.logger = logger;
    }

    final ChannelFutureListener remover = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            boolean removed = openChannels.remove(future.channel());
            if (removed) {
                openChannelsMetric.dec();
            }
            if (logger.isTraceEnabled()) {
                logger.trace("channel closed: {}", future.channel());
            }
        }
    };

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace("channel opened: {}", ctx.channel());
        }
        boolean added = openChannels.add(ctx.channel());
        if (added) {
            openChannelsMetric.inc();
            totalChannelsMetric.inc();
            ctx.channel().closeFuture().addListener(remover);
        }
        super.channelActive(ctx);
    }

    public long numberOfOpenChannels() {
        return openChannelsMetric.count();
    }

    public long totalChannels() {
        return totalChannelsMetric.count();
    }

    public void close() {
        for (Channel channel : openChannels) {
            channel.close().awaitUninterruptibly();
        }
    }

}
