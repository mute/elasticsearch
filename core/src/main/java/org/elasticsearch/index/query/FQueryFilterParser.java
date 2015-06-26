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

import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;

/**
 * The "fquery" filter is the same as the {@link QueryFilterParser} except that it allows also to
 * associate a name with the query filter.
 */
@Deprecated
public class FQueryFilterParser extends BaseQueryParser {

    @Inject
    public FQueryFilterParser() {
    }

    @Override
    public String[] names() {
        return new String[]{FQueryFilterBuilder.NAME};
    }

    @Override
    public QueryBuilder fromXContent(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        QueryBuilder wrappedQuery = null;
        boolean queryFound = false;

        float boost = AbstractQueryBuilder.DEFAULT_BOOST;
        String queryName = null;
        String currentFieldName = null;
        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (parseContext.isDeprecatedSetting(currentFieldName)) {
                // skip
            } else if (token == XContentParser.Token.START_OBJECT) {
                if ("query".equals(currentFieldName)) {
                    queryFound = true;
                    wrappedQuery = parseContext.parseInnerQueryBuilder();
                } else {
                    throw new QueryParsingException(parseContext, "[fquery] query does not support [" + currentFieldName + "]");
                }
            } else if (token.isValue()) {
                if ("_name".equals(currentFieldName)) {
                    queryName = parser.text();
                } else if ("boost".equals(currentFieldName)) {
                    boost = parser.floatValue();
                } else {
                    throw new QueryParsingException(parseContext, "[fquery] query does not support [" + currentFieldName + "]");
                }
            }
        }
        if (!queryFound) {
            throw new QueryParsingException(parseContext, "[fquery] requires 'query' element");
        }
        FQueryFilterBuilder queryBuilder = new FQueryFilterBuilder(wrappedQuery);
        queryBuilder.queryName(queryName);
        queryBuilder.boost(boost);
        return queryBuilder;
    }

    @Override
    public FQueryFilterBuilder getBuilderPrototype() {
        return FQueryFilterBuilder.PROTOTYPE;
    }
}
