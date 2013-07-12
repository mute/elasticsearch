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

package org.elasticsearch.search.facet.terms.strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import gnu.trove.iterator.TObjectIntIterator;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.cache.recycler.CacheRecycler;
import org.elasticsearch.common.collect.BoundedTreeSet;
import org.elasticsearch.script.SearchScript;
import org.elasticsearch.search.facet.FacetExecutor;
import org.elasticsearch.search.facet.InternalFacet;
import org.elasticsearch.search.facet.terms.support.EntryPriorityQueue;
import org.elasticsearch.search.internal.SearchContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class ScriptTermsStringFieldFacetExecutor extends FacetExecutor {

    private final CacheRecycler cacheRecycler;
    private final InternalStringTermsFacet.ComparatorType comparatorType;
    private final int size;
    private final SearchScript script;
    private final Matcher matcher;
    private final ImmutableSet<BytesRef> excluded;
    private final int numberOfShards;

    final TObjectIntHashMap<BytesRef> facets;
    long missing;
    long total;

    public ScriptTermsStringFieldFacetExecutor(int size, InternalStringTermsFacet.ComparatorType comparatorType, SearchContext context,
                                               ImmutableSet<BytesRef> excluded, Pattern pattern, String scriptLang, String script, Map<String, Object> params,
                                               CacheRecycler cacheRecycler) {
        this.size = size;
        this.comparatorType = comparatorType;
        this.numberOfShards = context.numberOfShards();
        this.script = context.scriptService().search(context.lookup(), scriptLang, script, params);
        this.cacheRecycler = cacheRecycler;

        this.excluded = excluded;
        this.matcher = pattern != null ? pattern.matcher("") : null;

        this.facets = cacheRecycler.popObjectIntMap();
    }

    @Override
    public Collector collector() {
        return new Collector(matcher, excluded, script, facets);
    }

    @Override
    public InternalFacet buildFacet(String facetName) {
        if (facets.isEmpty()) {
            cacheRecycler.pushObjectIntMap(facets);
            return new InternalStringTermsFacet(facetName, comparatorType, size, ImmutableList.<InternalStringTermsFacet.TermEntry>of(), missing, total);
        } else {
            if (size < EntryPriorityQueue.LIMIT) {
                EntryPriorityQueue ordered = new EntryPriorityQueue(size, comparatorType.comparator());
                for (TObjectIntIterator<BytesRef> it = facets.iterator(); it.hasNext(); ) {
                    it.advance();
                    ordered.insertWithOverflow(new InternalStringTermsFacet.TermEntry(it.key(), it.value()));
                }
                InternalStringTermsFacet.TermEntry[] list = new InternalStringTermsFacet.TermEntry[ordered.size()];
                for (int i = ordered.size() - 1; i >= 0; i--) {
                    list[i] = ((InternalStringTermsFacet.TermEntry) ordered.pop());
                }
                cacheRecycler.pushObjectIntMap(facets);
                return new InternalStringTermsFacet(facetName, comparatorType, size, Arrays.asList(list), missing, total);
            } else {
                BoundedTreeSet<InternalStringTermsFacet.TermEntry> ordered = new BoundedTreeSet<InternalStringTermsFacet.TermEntry>(comparatorType.comparator(), size);
                for (TObjectIntIterator<BytesRef> it = facets.iterator(); it.hasNext(); ) {
                    it.advance();
                    ordered.add(new InternalStringTermsFacet.TermEntry(it.key(), it.value()));
                }
                cacheRecycler.pushObjectIntMap(facets);
                return new InternalStringTermsFacet(facetName, comparatorType, size, ordered, missing, total);
            }
        }
    }

    class Collector extends FacetExecutor.Collector {

        private final Matcher matcher;
        private final ImmutableSet<BytesRef> excluded;
        private final SearchScript script;
        private final TObjectIntHashMap<BytesRef> facets;

        long missing;
        long total;

        Collector(Matcher matcher, ImmutableSet<BytesRef> excluded, SearchScript script, TObjectIntHashMap<BytesRef> facets) {
            this.matcher = matcher;
            this.excluded = excluded;
            this.script = script;
            this.facets = facets;
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
            script.setScorer(scorer);
        }

        @Override
        public void setNextReader(AtomicReaderContext context) throws IOException {
            script.setNextReader(context);
        }

        @Override
        public void collect(int doc) throws IOException {
            script.setNextDocId(doc);
            Object o = script.run();
            if (o == null) {
                missing++;
                return;
            }
            if (o instanceof Iterable) {
                boolean found = false;
                for (Object o1 : ((Iterable) o)) {
                    String value = o1.toString();
                    if (match(value)) {
                        found = true;
                        // LUCENE 4 UPGRADE: should be possible to convert directly to BR
                        facets.adjustOrPutValue(new BytesRef(value), 1, 1);
                        total++;
                    }
                }
                if (!found) {
                    missing++;
                }
            } else if (o instanceof Object[]) {
                boolean found = false;
                for (Object o1 : ((Object[]) o)) {
                    String value = o1.toString();
                    if (match(value)) {
                        found = true;
                        // LUCENE 4 UPGRADE: should be possible to convert directly to BR
                        facets.adjustOrPutValue(new BytesRef(value), 1, 1);
                        total++;
                    }
                }
                if (!found) {
                    missing++;
                }
            } else {
                String value = o.toString();
                if (match(value)) {
                    // LUCENE 4 UPGRADE: should be possible to convert directly to BR
                    facets.adjustOrPutValue(new BytesRef(value), 1, 1);
                    total++;
                } else {
                    missing++;
                }
            }
        }

        @Override
        public void postCollection() {
            ScriptTermsStringFieldFacetExecutor.this.missing = missing;
            ScriptTermsStringFieldFacetExecutor.this.total = total;
        }

        private boolean match(String value) {
            if (excluded != null && excluded.contains(new BytesRef(value))) {
                return false;
            }
            if (matcher != null && !matcher.reset(value).matches()) {
                return false;
            }
            return true;
        }
    }
}
