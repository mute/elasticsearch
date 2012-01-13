/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
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

package org.elasticsearch.client.transport.support;

import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.TransportActions;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.count.CountRequest;
import org.elasticsearch.action.count.CountResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.deletebyquery.DeleteByQueryRequest;
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.mlt.MoreLikeThisRequest;
import org.elasticsearch.action.percolate.PercolateRequest;
import org.elasticsearch.action.percolate.PercolateResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.AdminClient;
import org.elasticsearch.client.internal.InternalClient;
import org.elasticsearch.client.support.AbstractClient;
import org.elasticsearch.client.transport.TransportClientNodesService;
import org.elasticsearch.client.transport.action.bulk.ClientTransportBulkAction;
import org.elasticsearch.client.transport.action.count.ClientTransportCountAction;
import org.elasticsearch.client.transport.action.delete.ClientTransportDeleteAction;
import org.elasticsearch.client.transport.action.deletebyquery.ClientTransportDeleteByQueryAction;
import org.elasticsearch.client.transport.action.get.ClientTransportGetAction;
import org.elasticsearch.client.transport.action.get.ClientTransportMultiGetAction;
import org.elasticsearch.client.transport.action.index.ClientTransportIndexAction;
import org.elasticsearch.client.transport.action.mlt.ClientTransportMoreLikeThisAction;
import org.elasticsearch.client.transport.action.percolate.ClientTransportPercolateAction;
import org.elasticsearch.client.transport.action.search.ClientTransportSearchAction;
import org.elasticsearch.client.transport.action.search.ClientTransportSearchScrollAction;
import org.elasticsearch.client.transport.action.support.BaseClientTransportAction;
import org.elasticsearch.client.transport.action.update.ClientTransportUpdateAction;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;

import java.util.Map;

/**
 *
 */
public class InternalTransportClient extends AbstractClient implements InternalClient {

    private final ThreadPool threadPool;

    private final TransportClientNodesService nodesService;

    private final InternalTransportAdminClient adminClient;

    private final ClientTransportIndexAction indexAction;

    private final ClientTransportUpdateAction updateAction;

    private final ClientTransportDeleteAction deleteAction;

    private final ClientTransportBulkAction bulkAction;

    private final ClientTransportGetAction getAction;

    private final ClientTransportMultiGetAction multiGetAction;

    private final ClientTransportDeleteByQueryAction deleteByQueryAction;

    private final ClientTransportCountAction countAction;

    private final ClientTransportSearchAction searchAction;

    private final ClientTransportSearchScrollAction searchScrollAction;

    private final ClientTransportMoreLikeThisAction moreLikeThisAction;

    private final ClientTransportPercolateAction percolateAction;

    @Inject
    public InternalTransportClient(Settings settings, ThreadPool threadPool,
                                   TransportClientNodesService nodesService, InternalTransportAdminClient adminClient,
                                   Map<String, BaseClientTransportAction> actions) {
        this.threadPool = threadPool;
        this.nodesService = nodesService;
        this.adminClient = adminClient;

        this.indexAction = (ClientTransportIndexAction) actions.get(TransportActions.INDEX);
        this.updateAction = (ClientTransportUpdateAction) actions.get(TransportActions.UPDATE);
        this.deleteAction = (ClientTransportDeleteAction) actions.get(TransportActions.DELETE);
        this.bulkAction = (ClientTransportBulkAction) actions.get(TransportActions.BULK);
        this.getAction = (ClientTransportGetAction) actions.get(TransportActions.GET);
        this.multiGetAction = (ClientTransportMultiGetAction) actions.get(TransportActions.MULTI_GET);
        this.deleteByQueryAction = (ClientTransportDeleteByQueryAction) actions.get(TransportActions.DELETE_BY_QUERY);
        this.countAction = (ClientTransportCountAction) actions.get(TransportActions.COUNT);
        this.searchAction = (ClientTransportSearchAction) actions.get(TransportActions.SEARCH);
        this.searchScrollAction = (ClientTransportSearchScrollAction) actions.get(TransportActions.SEARCH_SCROLL);
        this.moreLikeThisAction = (ClientTransportMoreLikeThisAction) actions.get(TransportActions.MORE_LIKE_THIS);
        this.percolateAction = (ClientTransportPercolateAction) actions.get(TransportActions.PERCOLATE);
    }

    @Override
    public void close() {
        // nothing to do here
    }

    @Override
    public ThreadPool threadPool() {
        return this.threadPool;
    }

    @Override
    public AdminClient admin() {
        return adminClient;
    }

    @Override
    public ActionFuture<IndexResponse> index(final IndexRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<IndexResponse>>() {
            @Override
            public ActionFuture<IndexResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return indexAction.execute(node, request);
            }
        });
    }

    @Override
    public void index(final IndexRequest request, final ActionListener<IndexResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<IndexResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<IndexResponse> listener) throws ElasticSearchException {
                indexAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<UpdateResponse> update(final UpdateRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<UpdateResponse>>() {
            @Override
            public ActionFuture<UpdateResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return updateAction.execute(node, request);
            }
        });
    }

    @Override
    public void update(final UpdateRequest request, final ActionListener<UpdateResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<UpdateResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<UpdateResponse> listener) throws ElasticSearchException {
                updateAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<DeleteResponse> delete(final DeleteRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<DeleteResponse>>() {
            @Override
            public ActionFuture<DeleteResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return deleteAction.execute(node, request);
            }
        });
    }

    @Override
    public void delete(final DeleteRequest request, final ActionListener<DeleteResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<DeleteResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<DeleteResponse> listener) throws ElasticSearchException {
                deleteAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<BulkResponse> bulk(final BulkRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<BulkResponse>>() {
            @Override
            public ActionFuture<BulkResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return bulkAction.execute(node, request);
            }
        });
    }

    @Override
    public void bulk(final BulkRequest request, final ActionListener<BulkResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<BulkResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<BulkResponse> listener) throws ElasticSearchException {
                bulkAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<DeleteByQueryResponse> deleteByQuery(final DeleteByQueryRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<DeleteByQueryResponse>>() {
            @Override
            public ActionFuture<DeleteByQueryResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return deleteByQueryAction.execute(node, request);
            }
        });
    }

    @Override
    public void deleteByQuery(final DeleteByQueryRequest request, final ActionListener<DeleteByQueryResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<DeleteByQueryResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<DeleteByQueryResponse> listener) throws ElasticSearchException {
                deleteByQueryAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<GetResponse> get(final GetRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<GetResponse>>() {
            @Override
            public ActionFuture<GetResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return getAction.execute(node, request);
            }
        });
    }

    @Override
    public void get(final GetRequest request, final ActionListener<GetResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<GetResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<GetResponse> listener) throws ElasticSearchException {
                getAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<MultiGetResponse> multiGet(final MultiGetRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<MultiGetResponse>>() {
            @Override
            public ActionFuture<MultiGetResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return multiGetAction.execute(node, request);
            }
        });
    }

    @Override
    public void multiGet(final MultiGetRequest request, final ActionListener<MultiGetResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<MultiGetResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<MultiGetResponse> listener) throws ElasticSearchException {
                multiGetAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<CountResponse> count(final CountRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<CountResponse>>() {
            @Override
            public ActionFuture<CountResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return countAction.execute(node, request);
            }
        });
    }

    @Override
    public void count(final CountRequest request, final ActionListener<CountResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<CountResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<CountResponse> listener) throws ElasticSearchException {
                countAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<SearchResponse> search(final SearchRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<SearchResponse>>() {
            @Override
            public ActionFuture<SearchResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return searchAction.execute(node, request);
            }
        });
    }

    @Override
    public void search(final SearchRequest request, final ActionListener<SearchResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<SearchResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<SearchResponse> listener) throws ElasticSearchException {
                searchAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<SearchResponse> searchScroll(final SearchScrollRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<SearchResponse>>() {
            @Override
            public ActionFuture<SearchResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return searchScrollAction.execute(node, request);
            }
        });
    }

    @Override
    public void searchScroll(final SearchScrollRequest request, final ActionListener<SearchResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<SearchResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<SearchResponse> listener) throws ElasticSearchException {
                searchScrollAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<SearchResponse> moreLikeThis(final MoreLikeThisRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<SearchResponse>>() {
            @Override
            public ActionFuture<SearchResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return moreLikeThisAction.execute(node, request);
            }
        });
    }

    @Override
    public void moreLikeThis(final MoreLikeThisRequest request, final ActionListener<SearchResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<SearchResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<SearchResponse> listener) throws ElasticSearchException {
                moreLikeThisAction.execute(node, request, listener);
            }
        }, listener);
    }

    @Override
    public ActionFuture<PercolateResponse> percolate(final PercolateRequest request) {
        return nodesService.execute(new TransportClientNodesService.NodeCallback<ActionFuture<PercolateResponse>>() {
            @Override
            public ActionFuture<PercolateResponse> doWithNode(DiscoveryNode node) throws ElasticSearchException {
                return percolateAction.execute(node, request);
            }
        });
    }

    @Override
    public void percolate(final PercolateRequest request, final ActionListener<PercolateResponse> listener) {
        nodesService.execute(new TransportClientNodesService.NodeListenerCallback<PercolateResponse>() {
            @Override
            public void doWithNode(DiscoveryNode node, ActionListener<PercolateResponse> listener) throws ElasticSearchException {
                percolateAction.execute(node, request, listener);
            }
        }, listener);
    }
}
