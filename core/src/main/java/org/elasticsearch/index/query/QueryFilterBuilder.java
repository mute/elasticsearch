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

package org.elasticsearch.index.query;

import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.Query;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Objects;

/**
 * A filter that simply wraps a query.
 * @deprecated Useless now that queries and filters are merged: pass the
 *             query as a filter directly.
 */
@Deprecated
public class QueryFilterBuilder extends AbstractQueryBuilder<QueryFilterBuilder> {

    public static final String NAME = "query";

    private final QueryBuilder queryBuilder;

    static final QueryFilterBuilder PROTOTYPE = new QueryFilterBuilder(null);

    /**
     * A filter that simply wraps a query.
     *
     * @param queryBuilder The query to wrap as a filter
     */
    public QueryFilterBuilder(QueryBuilder queryBuilder) {
        this.queryBuilder = queryBuilder;
    }

    /**
     * @return the query builder that is wrapped by this {@link QueryFilterBuilder}
     */
    public QueryBuilder innerQuery() {
        return this.queryBuilder;
    }

    @Override
    public QueryFilterBuilder boost(float boost) {
        //no-op: QueryFilterParser doesn't support boost, we should be consistent and ignore it here too.
        return this;
    }

    @Override
    public QueryFilterBuilder queryName(String queryName) {
        //no-op: QueryFilterParser doesn't support _name, we should be consistent and ignore it here too.
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        doXContentInnerBuilder(builder, NAME, queryBuilder, params);
    }

    @Override
    protected Query doToQuery(QueryParseContext parseContext) throws IOException {
        // inner query builder can potentially be `null`, in that case we ignore it
        if (this.queryBuilder == null) {
            return null;
        }
        Query innerQuery = this.queryBuilder.toQuery(parseContext);
        if (innerQuery == null) {
            return null;
        }
        return new ConstantScoreQuery(innerQuery);
    }

    @Override
    public QueryValidationException validate() {
        return validateInnerQuery(queryBuilder, null);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(queryBuilder);
    }

    @Override
    protected boolean doEquals(QueryFilterBuilder other) {
        return Objects.equals(queryBuilder, other.queryBuilder);
    }

    @Override
    protected QueryFilterBuilder doReadFrom(StreamInput in) throws IOException {
        QueryBuilder innerQueryBuilder = in.readNamedWriteable();
        return new QueryFilterBuilder(innerQueryBuilder);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(queryBuilder);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
