/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
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

package org.elasticsearch.index.cache.filter.weak;

import org.apache.lucene.search.Filter;
import org.elasticsearch.common.collect.MapMaker;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.lucene.docset.DocSet;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.cache.filter.support.AbstractConcurrentMapFilterCache;
import org.elasticsearch.index.settings.IndexSettings;

import java.util.concurrent.ConcurrentMap;

/**
 * A weak reference based filter cache that has weak keys on the <tt>IndexReader</tt>.
 *
 * @author kimchy (shay.banon)
 */
public class WeakFilterCache extends AbstractConcurrentMapFilterCache {

    @Inject public WeakFilterCache(Index index, @IndexSettings Settings indexSettings) {
        super(index, indexSettings);
    }

    @Override protected ConcurrentMap<Filter, DocSet> buildFilterMap() {
        // DocSet are not really stored with strong reference only when searching on them...
        // Filter might be stored in query cache
        return new MapMaker().weakValues().makeMap();
    }

    @Override public String type() {
        return "weak";
    }
}