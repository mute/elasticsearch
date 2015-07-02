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

import org.apache.lucene.search.Query;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.lucene.search.Queries;
import org.elasticsearch.common.regex.Regex;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.support.XContentStructure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 */
public class IndicesQueryParser extends BaseQueryParserTemp {

    private static final ParseField QUERY_FIELD = new ParseField("query", "filter");
    private static final ParseField NO_MATCH_QUERY = new ParseField("no_match_query", "no_match_filter");

    @Nullable
    private final ClusterService clusterService;

    @Inject
    public IndicesQueryParser(@Nullable ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public String[] names() {
        return new String[]{IndicesQueryBuilder.NAME};
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        Query noMatchQuery = null;
        boolean queryFound = false;
        boolean indicesFound = false;
        boolean currentIndexMatchesIndices = false;
        String queryName = null;
        float boost = AbstractQueryBuilder.DEFAULT_BOOST;

        String currentFieldName = null;
        XContentParser.Token token;
        XContentStructure.InnerQuery innerQuery = null;
        XContentStructure.InnerQuery innerNoMatchQuery = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_OBJECT) {
                if (parseContext.parseFieldMatcher().match(currentFieldName, QUERY_FIELD)) {
                    innerQuery = new XContentStructure.InnerQuery(parseContext, null);
                    queryFound = true;
                } else if (parseContext.parseFieldMatcher().match(currentFieldName, NO_MATCH_QUERY)) {
                    innerNoMatchQuery = new XContentStructure.InnerQuery(parseContext, null);
                } else {
                    throw new QueryParsingException(parseContext, "[indices] query does not support [" + currentFieldName + "]");
                }
            } else if (token == XContentParser.Token.START_ARRAY) {
                if ("indices".equals(currentFieldName)) {
                    if (indicesFound) {
                        throw new QueryParsingException(parseContext, "[indices] indices or index already specified");
                    }
                    indicesFound = true;
                    Collection<String> indices = new ArrayList<>();
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        String value = parser.textOrNull();
                        if (value == null) {
                            throw new QueryParsingException(parseContext, "[indices] no value specified for 'indices' entry");
                        }
                        indices.add(value);
                    }
                    currentIndexMatchesIndices = matchesIndices(parseContext.index().name(), indices.toArray(new String[indices.size()]));
                } else {
                    throw new QueryParsingException(parseContext, "[indices] query does not support [" + currentFieldName + "]");
                }
            } else if (token.isValue()) {
                if ("index".equals(currentFieldName)) {
                    if (indicesFound) {
                        throw new QueryParsingException(parseContext, "[indices] indices or index already specified");
                    }
                    indicesFound = true;
                    currentIndexMatchesIndices = matchesIndices(parseContext.index().name(), parser.text());
                } else if (parseContext.parseFieldMatcher().match(currentFieldName, NO_MATCH_QUERY)) {
                    String type = parser.text();
                    if ("all".equals(type)) {
                        noMatchQuery = Queries.newMatchAllQuery();
                    } else if ("none".equals(type)) {
                        noMatchQuery = Queries.newMatchNoDocsQuery();
                    }
                } else if ("_name".equals(currentFieldName)) {
                    queryName = parser.text();
                } else if ("boost".equals(currentFieldName)) {
                    boost = parser.floatValue();
                } else {
                    throw new QueryParsingException(parseContext, "[indices] query does not support [" + currentFieldName + "]");
                }
            }
        }
        if (!queryFound) {
            throw new QueryParsingException(parseContext, "[indices] requires 'query' element");
        }
        if (!indicesFound) {
            throw new QueryParsingException(parseContext, "[indices] requires 'indices' or 'index' element");
        }

        Query chosenQuery;
        if (currentIndexMatchesIndices) {
            chosenQuery = innerQuery.asQuery();
        } else {
            // If noMatchQuery is set, it means "no_match_query" was "all" or "none"
            if (noMatchQuery != null) {
                chosenQuery = noMatchQuery;
            } else {
                // There might be no "no_match_query" set, so default to the match_all if not set
                if (innerNoMatchQuery == null) {
                    chosenQuery = Queries.newMatchAllQuery();
                } else {
                    chosenQuery = innerNoMatchQuery.asQuery();
                }
            }
        }
        if (queryName != null) {
            parseContext.addNamedQuery(queryName, chosenQuery);
        }
        chosenQuery.setBoost(boost);
        return chosenQuery;
    }

    protected boolean matchesIndices(String currentIndex, String... indices) {
        final String[] concreteIndices = clusterService.state().metaData().concreteIndices(IndicesOptions.lenientExpandOpen(), indices);
        for (String index : concreteIndices) {
            if (Regex.simpleMatch(index, currentIndex)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public IndicesQueryBuilder getBuilderPrototype() {
        return IndicesQueryBuilder.PROTOTYPE;
    }
}
