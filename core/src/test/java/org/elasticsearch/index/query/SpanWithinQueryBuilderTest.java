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
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWithinQuery;
import org.junit.Test;

import java.io.IOException;

public class SpanWithinQueryBuilderTest extends BaseQueryTestCase<SpanWithinQueryBuilder> {

    @Override
    protected Query doCreateExpectedQuery(SpanWithinQueryBuilder testQueryBuilder, QueryParseContext context) throws IOException {
        SpanQuery big = (SpanQuery) testQueryBuilder.big().toQuery(context);
        SpanQuery little = (SpanQuery) testQueryBuilder.little().toQuery(context);
        return new SpanWithinQuery(big, little);
    }

    @Override
    protected SpanWithinQueryBuilder doCreateTestQueryBuilder() {
        SpanTermQueryBuilder[] spanTermQueries = new SpanTermQueryBuilderTest().createSpanTermQueryBuilders(2);
        return new SpanWithinQueryBuilder(spanTermQueries[0], spanTermQueries[1]);
    }

    @Test
    public void testValidate() {
        int totalExpectedErrors = 0;
        SpanQueryBuilder bigSpanQueryBuilder;
        if (randomBoolean()) {
            bigSpanQueryBuilder = new SpanTermQueryBuilder("", "test");
            totalExpectedErrors++;
        } else {
            bigSpanQueryBuilder = new SpanTermQueryBuilder("name", "value");
        }
        SpanQueryBuilder littleSpanQueryBuilder;
        if (randomBoolean()) {
            littleSpanQueryBuilder = new SpanTermQueryBuilder("", "test");
            totalExpectedErrors++;
        } else {
            littleSpanQueryBuilder = new SpanTermQueryBuilder("name", "value");
        }
        SpanWithinQueryBuilder queryBuilder = new SpanWithinQueryBuilder(bigSpanQueryBuilder, littleSpanQueryBuilder);
        assertValidate(queryBuilder, totalExpectedErrors);
    }

    @Test(expected=NullPointerException.class)
    public void testNullBig() {
        new SpanWithinQueryBuilder(null, new SpanTermQueryBuilder("name", "value"));
    }

    @Test(expected=NullPointerException.class)
    public void testNullLittle() {
        new SpanWithinQueryBuilder(new SpanTermQueryBuilder("name", "value"), null);
    }
}
