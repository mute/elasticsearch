/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test.unit.index.analysis;

import org.elasticsearch.common.inject.ProvisionException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.analysis.AnalysisService;
import org.elasticsearch.index.analysis.HunspellTokenFilterFactory;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.test.integration.ElasticsearchTestCase;
import org.junit.Test;

import java.io.IOException;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

public class HunspellTokenFilterFactoryTests extends ElasticsearchTestCase {

    @Test
    public void testDedup() throws IOException {
        Settings settings = settingsBuilder()
                .put("path.conf", getResource("/indices/analyze/conf_dir"))
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();

        AnalysisService analysisService = AnalysisTestsHelper.createAnalysisServiceFromSettings(settings);
        TokenFilterFactory tokenFilter = analysisService.tokenFilter("en_US");
        assertThat(tokenFilter, instanceOf(HunspellTokenFilterFactory.class));
        HunspellTokenFilterFactory hunspellTokenFilter = (HunspellTokenFilterFactory) tokenFilter;
        assertThat(hunspellTokenFilter.dedup(), is(true));

        settings = settingsBuilder()
                .put("path.conf", getResource("/indices/analyze/conf_dir"))
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.dedup", false)
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();

        analysisService = AnalysisTestsHelper.createAnalysisServiceFromSettings(settings);
        tokenFilter = analysisService.tokenFilter("en_US");
        assertThat(tokenFilter, instanceOf(HunspellTokenFilterFactory.class));
        hunspellTokenFilter = (HunspellTokenFilterFactory) tokenFilter;
        assertThat(hunspellTokenFilter.dedup(), is(false));
    }

    @Test
    public void testDefaultRecursionLevel() throws IOException {
        Settings settings = settingsBuilder()
                .put("path.conf", getResource("/indices/analyze/conf_dir"))
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();

        AnalysisService analysisService = AnalysisTestsHelper.createAnalysisServiceFromSettings(settings);
        TokenFilterFactory tokenFilter = analysisService.tokenFilter("en_US");
        assertThat(tokenFilter, instanceOf(HunspellTokenFilterFactory.class));
        HunspellTokenFilterFactory hunspellTokenFilter = (HunspellTokenFilterFactory) tokenFilter;
        assertThat(hunspellTokenFilter.recursionLevel(), is(2));
    }

    @Test
    public void testCustomRecursionLevel() throws IOException {
        Settings settings = settingsBuilder()
                .put("path.conf", getResource("/indices/analyze/conf_dir"))
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.recursion_level", 0)
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();

        AnalysisService analysisService = AnalysisTestsHelper.createAnalysisServiceFromSettings(settings);
        TokenFilterFactory tokenFilter = analysisService.tokenFilter("en_US");
        assertThat(tokenFilter, instanceOf(HunspellTokenFilterFactory.class));
        HunspellTokenFilterFactory hunspellTokenFilter = (HunspellTokenFilterFactory) tokenFilter;
        assertThat(hunspellTokenFilter.recursionLevel(), is(0));
    }

    @Test(expected = ProvisionException.class)
    public void negativeRecursionLevelShouldFail() throws IOException {
        Settings settings = settingsBuilder()
                .put("path.conf", getResource("/indices/analyze/conf_dir"))
                .put("index.analysis.filter.en_US.type", "hunspell")
                .put("index.analysis.filter.en_US.recursion_level", -1)
                .put("index.analysis.filter.en_US.locale", "en_US")
                .build();
        AnalysisTestsHelper.createAnalysisServiceFromSettings(settings);
    }

}
