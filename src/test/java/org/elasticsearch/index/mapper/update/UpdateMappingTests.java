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

package org.elasticsearch.index.mapper.update;

import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.common.compress.CompressedString;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.mapper.DocumentMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.service.IndexService;
import org.elasticsearch.test.ElasticsearchSingleNodeTest;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;

import static org.elasticsearch.common.io.Streams.copyToStringFromClasspath;
import static org.hamcrest.CoreMatchers.equalTo;


public class UpdateMappingTests extends ElasticsearchSingleNodeTest {

    @Test
    public void testIndexFieldParsing() throws IOException {
        IndexService indexService = createIndex("test", ImmutableSettings.settingsBuilder().build());
        XContentBuilder indexMapping = XContentFactory.jsonBuilder();
        boolean enabled = randomBoolean();
        indexMapping.startObject()
                .startObject("type")
                .startObject("_index")
                .field("enabled", enabled)
                .field("store", true)
                .startObject("fielddata")
                .field("format", "fst")
                .endObject()
                .endObject()
                .endObject()
                .endObject();
        DocumentMapper documentMapper = indexService.mapperService().parse("type", new CompressedString(indexMapping.string()), true);
        assertThat(documentMapper.indexMapper().enabled(), equalTo(enabled));
        assertTrue(documentMapper.indexMapper().fieldType().stored());
        assertThat(documentMapper.indexMapper().fieldDataType().getFormat(null), equalTo("fst"));
        documentMapper.refreshSource();
        documentMapper = indexService.mapperService().parse("type", new CompressedString(documentMapper.mappingSource().string()), true);
        assertThat(documentMapper.indexMapper().enabled(), equalTo(enabled));
        assertTrue(documentMapper.indexMapper().fieldType().stored());
        assertThat(documentMapper.indexMapper().fieldDataType().getFormat(null), equalTo("fst"));
    }

    @Test
    public void testTimestampParsing() throws IOException {
        IndexService indexService = createIndex("test", ImmutableSettings.settingsBuilder().build());
        XContentBuilder indexMapping = XContentFactory.jsonBuilder();
        boolean enabled = randomBoolean();
        indexMapping.startObject()
                .startObject("type")
                .startObject("_timestamp")
                .field("enabled", enabled)
                .field("store", true)
                .startObject("fielddata")
                .field("format", "doc_values")
                .endObject()
                .endObject()
                .endObject()
                .endObject();
        DocumentMapper documentMapper = indexService.mapperService().parse("type", new CompressedString(indexMapping.string()), true);
        assertThat(documentMapper.timestampFieldMapper().enabled(), equalTo(enabled));
        assertTrue(documentMapper.timestampFieldMapper().fieldType().stored());
        assertTrue(documentMapper.timestampFieldMapper().hasDocValues());
        documentMapper.refreshSource();
        documentMapper = indexService.mapperService().parse("type", new CompressedString(documentMapper.mappingSource().string()), true);
        assertThat(documentMapper.timestampFieldMapper().enabled(), equalTo(enabled));
        assertTrue(documentMapper.timestampFieldMapper().hasDocValues());
        assertTrue(documentMapper.timestampFieldMapper().fieldType().stored());
    }

    @Test
    public void testSizeParsing() throws IOException {
        IndexService indexService = createIndex("test", ImmutableSettings.settingsBuilder().build());
        XContentBuilder indexMapping = XContentFactory.jsonBuilder();
        boolean enabled = randomBoolean();
        indexMapping.startObject()
                .startObject("type")
                .startObject("_size")
                .field("enabled", enabled)
                .field("store", true)
                .endObject()
                .endObject()
                .endObject();
        DocumentMapper documentMapper = indexService.mapperService().parse("type", new CompressedString(indexMapping.string()), true);
        assertThat(documentMapper.sizeFieldMapper().enabled(), equalTo(enabled));
        assertTrue(documentMapper.sizeFieldMapper().fieldType().stored());
        documentMapper.refreshSource();
        documentMapper = indexService.mapperService().parse("type", new CompressedString(documentMapper.mappingSource().string()), true);
        assertThat(documentMapper.sizeFieldMapper().enabled(), equalTo(enabled));
    }

    @Test
    public void testSizeTimestampIndexParsing() throws IOException {
        IndexService indexService = createIndex("test", ImmutableSettings.settingsBuilder().build());
        String mapping = copyToStringFromClasspath("/org/elasticsearch/index/mapper/update/default_mapping_with_disabled_root_types.json");
        DocumentMapper documentMapper = indexService.mapperService().parse("type", new CompressedString(mapping), true);
        assertThat(documentMapper.mappingSource().string(), equalTo(mapping));
        documentMapper.refreshSource();
        documentMapper = indexService.mapperService().parse("type", new CompressedString(documentMapper.mappingSource().string()), true);
        assertThat(documentMapper.mappingSource().string(), equalTo(mapping));
    }

    @Test
    public void testDefaultApplied() throws IOException {
        createIndex("test1", ImmutableSettings.settingsBuilder().build());
        createIndex("test2", ImmutableSettings.settingsBuilder().build());
        XContentBuilder defaultMapping = XContentFactory.jsonBuilder().startObject()
                .startObject(MapperService.DEFAULT_MAPPING).startObject("_size").field("enabled", true).endObject().endObject()
                .endObject();
        client().admin().indices().preparePutMapping().setType(MapperService.DEFAULT_MAPPING).setSource(defaultMapping).get();
        XContentBuilder typeMapping = XContentFactory.jsonBuilder().startObject()
                .startObject("type").startObject("_all").field("enabled", false).endObject().endObject()
                .endObject();
        client().admin().indices().preparePutMapping("test1").setType("type").setSource(typeMapping).get();
        client().admin().indices().preparePutMapping("test1", "test2").setType("type").setSource(typeMapping).get();

        GetMappingsResponse response = client().admin().indices().prepareGetMappings("test2").get();
        assertNotNull(response.getMappings().get("test2").get("type").getSourceAsMap().get("_all"));
        assertFalse((Boolean) ((LinkedHashMap) response.getMappings().get("test2").get("type").getSourceAsMap().get("_all")).get("enabled"));
        assertNotNull(response.getMappings().get("test2").get("type").getSourceAsMap().get("_size"));
        assertTrue((Boolean)((LinkedHashMap)response.getMappings().get("test2").get("type").getSourceAsMap().get("_size")).get("enabled"));
    }
}
