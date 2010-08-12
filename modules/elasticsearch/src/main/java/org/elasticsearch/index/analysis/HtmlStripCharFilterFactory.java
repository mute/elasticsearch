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

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.CharStream;
import org.elasticsearch.common.collect.ImmutableSet;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.assistedinject.Assisted;
import org.elasticsearch.common.lucene.analysis.HTMLStripCharFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.settings.IndexSettings;

/**
 * @author kimchy (shay.banon)
 */
public class HtmlStripCharFilterFactory extends AbstractCharFilterFactory {

    private final ImmutableSet<String> escapedTags;

    private final int readAheadLimit;

    @Inject public HtmlStripCharFilterFactory(Index index, @IndexSettings Settings indexSettings, @Assisted String name, @Assisted Settings settings) {
        super(index, indexSettings, name);
        this.readAheadLimit = settings.getAsInt("read_ahead", HTMLStripCharFilter.DEFAULT_READ_AHEAD);
        String[] escapedTags = settings.getAsArray("escaped_tags");
        if (escapedTags.length > 0) {
            this.escapedTags = ImmutableSet.copyOf(escapedTags);
        } else {
            this.escapedTags = null;
        }
    }

    public ImmutableSet<String> escapedTags() {
        return escapedTags;
    }

    public int readAheadLimit() {
        return readAheadLimit;
    }

    @Override public CharStream create(CharStream tokenStream) {
        return new HTMLStripCharFilter(tokenStream, escapedTags, readAheadLimit);
    }
}
