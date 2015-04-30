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

package org.elasticsearch.cluster;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.google.common.collect.ImmutableSet;
import org.elasticsearch.cluster.DiffableUtils.KeyedReader;
import org.elasticsearch.cluster.block.ClusterBlock;
import org.elasticsearch.cluster.block.ClusterBlocks;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.IndexTemplateMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.routing.*;
import org.elasticsearch.cluster.routing.allocation.RoutingAllocation;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.compress.CompressedString;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 *
 */
public class ClusterState implements ToXContent, Diffable<ClusterState> {

    public static final ClusterState PROTO = builder(ClusterName.DEFAULT).build();

    public static enum ClusterStateStatus {
        UNKNOWN((byte) 0),
        RECEIVED((byte) 1),
        BEING_APPLIED((byte) 2),
        APPLIED((byte) 3);

        private final byte id;

        ClusterStateStatus(byte id) {
            this.id = id;
        }

        public byte id() {
            return this.id;
        }
    }

    public interface Custom extends Diffable<Custom>, ToXContent {

        String type();
    }

    private final static Map<String, Custom> customPrototypes = new HashMap<>();

    /**
     * Register a custom index meta data factory. Make sure to call it from a static block.
     */
    public static void registerPrototype(String type, Custom proto) {
        customPrototypes.put(type, proto);
    }

    @Nullable
    public static <T extends Custom> T lookupPrototype(String type) {
        //noinspection unchecked
        return (T) customPrototypes.get(type);
    }

    public static <T extends Custom> T lookupPrototypeSafe(String type) {
        @SuppressWarnings("unchecked")
        T proto = (T)customPrototypes.get(type);
        if (proto == null) {
            throw new IllegalArgumentException("No custom state prototype registered for type [" + type + "]");
        }
        return proto;
    }

    public static final String UNKNOWN_UUID = "_na_";

    public static final long UNKNOWN_VERSION = -1;

    private final long version;

    private final String uuid;

    private final RoutingTable routingTable;

    private final DiscoveryNodes nodes;

    private final MetaData metaData;

    private final ClusterBlocks blocks;

    private final ImmutableOpenMap<String, Custom> customs;

    private final ClusterName clusterName;

    private final boolean wasReadFromDiff;

    // built on demand
    private volatile RoutingNodes routingNodes;

    private volatile ClusterStateStatus status;

    public ClusterState(long version, String uuid, ClusterState state) {
        this(state.clusterName, version, uuid, state.metaData(), state.routingTable(), state.nodes(), state.blocks(), state.customs(), false);
    }

    public ClusterState(ClusterName clusterName, long version, String uuid, MetaData metaData, RoutingTable routingTable, DiscoveryNodes nodes, ClusterBlocks blocks, ImmutableOpenMap<String, Custom> customs, boolean wasReadFromDiff) {
        this.version = version;
        this.uuid = uuid;
        this.clusterName = clusterName;
        this.metaData = metaData;
        this.routingTable = routingTable;
        this.nodes = nodes;
        this.blocks = blocks;
        this.customs = customs;
        this.status = ClusterStateStatus.UNKNOWN;
        this.wasReadFromDiff = wasReadFromDiff;
    }

    public ClusterStateStatus status() {
        return status;
    }

    public ClusterState status(ClusterStateStatus newStatus) {
        this.status = newStatus;
        return this;
    }

    public long version() {
        return this.version;
    }

    public long getVersion() {
        return version();
    }

    /**
     * This uuid is automatically generated for for each version of cluster state. It is used to make sure that
     * we are applying diffs to the right previous state.
     */
    public String uuid() {
        return this.uuid;
    }

    public DiscoveryNodes nodes() {
        return this.nodes;
    }

    public DiscoveryNodes getNodes() {
        return nodes();
    }

    public MetaData metaData() {
        return this.metaData;
    }

    public MetaData getMetaData() {
        return metaData();
    }

    public RoutingTable routingTable() {
        return routingTable;
    }

    public RoutingTable getRoutingTable() {
        return routingTable();
    }

    public RoutingNodes routingNodes() {
        return routingTable.routingNodes(this);
    }

    public RoutingNodes getRoutingNodes() {
        return readOnlyRoutingNodes();
    }

    public ClusterBlocks blocks() {
        return this.blocks;
    }

    public ClusterBlocks getBlocks() {
        return blocks;
    }

    public ImmutableOpenMap<String, Custom> customs() {
        return this.customs;
    }

    public ImmutableOpenMap<String, Custom> getCustoms() {
        return this.customs;
    }

    public ClusterName getClusterName() {
        return this.clusterName;
    }

    // Used for testing and logging to determine how this cluster state was send over the wire
    boolean wasReadFromDiff() {
        return wasReadFromDiff;
    }

    /**
     * Returns a built (on demand) routing nodes view of the routing table. <b>NOTE, the routing nodes
     * are mutable, use them just for read operations</b>
     */
    public RoutingNodes readOnlyRoutingNodes() {
        if (routingNodes != null) {
            return routingNodes;
        }
        routingNodes = routingTable.routingNodes(this);
        return routingNodes;
    }

    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("version: ").append(version).append("\n");
        sb.append("uuid: ").append(uuid).append("\n");
        sb.append("from_diff: ").append(wasReadFromDiff).append("\n");
        sb.append("meta data version: ").append(metaData.version()).append("\n");
        sb.append(nodes().prettyPrint());
        sb.append(routingTable().prettyPrint());
        sb.append(readOnlyRoutingNodes().prettyPrint());
        return sb.toString();
    }

    @Override
    public String toString() {
        try {
            XContentBuilder builder = XContentFactory.jsonBuilder().prettyPrint();
            builder.startObject();
            toXContent(builder, EMPTY_PARAMS);
            builder.endObject();
            return builder.string();
        } catch (IOException e) {
            return "{ \"error\" : \"" + e.getMessage() + "\"}";
        }
    }

    public enum Metric {
        VERSION("version"),
        MASTER_NODE("master_node"),
        BLOCKS("blocks"),
        NODES("nodes"),
        METADATA("metadata"),
        ROUTING_TABLE("routing_table"),
        ROUTING_NODES("routing_nodes"),
        CUSTOMS("customs");

        private static Map<String, Metric> valueToEnum;

        static {
            valueToEnum = new HashMap<>();
            for (Metric metric : Metric.values()) {
                valueToEnum.put(metric.value, metric);
            }
        }

        private final String value;

        private Metric(String value) {
            this.value = value;
        }

        public static EnumSet<Metric> parseString(String param, boolean ignoreUnknown) {
            String[] metrics = Strings.splitStringByCommaToArray(param);
            EnumSet<Metric> result = EnumSet.noneOf(Metric.class);
            for (String metric : metrics) {
                if ("_all".equals(metric)) {
                    result = EnumSet.allOf(Metric.class);
                    break;
                }
                Metric m = valueToEnum.get(metric);
                if (m == null) {
                    if (!ignoreUnknown) {
                        throw new IllegalArgumentException("Unknown metric [" + metric + "]");
                    }
                } else {
                    result.add(m);
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        EnumSet<Metric> metrics = Metric.parseString(params.param("metric", "_all"), true);

        if (metrics.contains(Metric.VERSION)) {
            builder.field("version", version);
            builder.field("uuid", uuid);
        }

        if (metrics.contains(Metric.MASTER_NODE)) {
            builder.field("master_node", nodes().masterNodeId());
        }

        if (metrics.contains(Metric.BLOCKS)) {
            builder.startObject("blocks");

            if (!blocks().global().isEmpty()) {
                builder.startObject("global");
                for (ClusterBlock block : blocks().global()) {
                    block.toXContent(builder, params);
                }
                builder.endObject();
            }

            if (!blocks().indices().isEmpty()) {
                builder.startObject("indices");
                for (Map.Entry<String, ImmutableSet<ClusterBlock>> entry : blocks().indices().entrySet()) {
                    builder.startObject(entry.getKey());
                    for (ClusterBlock block : entry.getValue()) {
                        block.toXContent(builder, params);
                    }
                    builder.endObject();
                }
                builder.endObject();
            }

            builder.endObject();
        }

        // nodes
        if (metrics.contains(Metric.NODES)) {
            builder.startObject("nodes");
            for (DiscoveryNode node : nodes()) {
                builder.startObject(node.id(), XContentBuilder.FieldCaseConversion.NONE);
                builder.field("name", node.name());
                builder.field("transport_address", node.address().toString());

                builder.startObject("attributes");
                for (Map.Entry<String, String> attr : node.attributes().entrySet()) {
                    builder.field(attr.getKey(), attr.getValue());
                }
                builder.endObject();

                builder.endObject();
            }
            builder.endObject();
        }

        // meta data
        if (metrics.contains(Metric.METADATA)) {
            builder.startObject("metadata");

            builder.startObject("templates");
            for (ObjectCursor<IndexTemplateMetaData> cursor : metaData().templates().values()) {
                IndexTemplateMetaData templateMetaData = cursor.value;
                builder.startObject(templateMetaData.name(), XContentBuilder.FieldCaseConversion.NONE);

                builder.field("template", templateMetaData.template());
                builder.field("order", templateMetaData.order());

                builder.startObject("settings");
                Settings settings = templateMetaData.settings();
                settings.toXContent(builder, params);
                builder.endObject();

                builder.startObject("mappings");
                for (ObjectObjectCursor<String, CompressedString> cursor1 : templateMetaData.mappings()) {
                    byte[] mappingSource = cursor1.value.uncompressed();
                    XContentParser parser = XContentFactory.xContent(mappingSource).createParser(mappingSource);
                    Map<String, Object> mapping = parser.map();
                    if (mapping.size() == 1 && mapping.containsKey(cursor1.key)) {
                        // the type name is the root value, reduce it
                        mapping = (Map<String, Object>) mapping.get(cursor1.key);
                    }
                    builder.field(cursor1.key);
                    builder.map(mapping);
                }
                builder.endObject();


                builder.endObject();
            }
            builder.endObject();

            builder.startObject("indices");
            for (IndexMetaData indexMetaData : metaData()) {
                builder.startObject(indexMetaData.index(), XContentBuilder.FieldCaseConversion.NONE);

                builder.field("state", indexMetaData.state().toString().toLowerCase(Locale.ENGLISH));

                builder.startObject("settings");
                Settings settings = indexMetaData.settings();
                settings.toXContent(builder, params);
                builder.endObject();

                builder.startObject("mappings");
                for (ObjectObjectCursor<String, MappingMetaData> cursor : indexMetaData.mappings()) {
                    byte[] mappingSource = cursor.value.source().uncompressed();
                    XContentParser parser = XContentFactory.xContent(mappingSource).createParser(mappingSource);
                    Map<String, Object> mapping = parser.map();
                    if (mapping.size() == 1 && mapping.containsKey(cursor.key)) {
                        // the type name is the root value, reduce it
                        mapping = (Map<String, Object>) mapping.get(cursor.key);
                    }
                    builder.field(cursor.key);
                    builder.map(mapping);
                }
                builder.endObject();

                builder.startArray("aliases");
                for (ObjectCursor<String> cursor : indexMetaData.aliases().keys()) {
                    builder.value(cursor.value);
                }
                builder.endArray();

                builder.endObject();
            }
            builder.endObject();

            for (ObjectObjectCursor<String, MetaData.Custom> cursor : metaData.customs()) {
                builder.startObject(cursor.key);
                cursor.value.toXContent(builder, params);
                builder.endObject();
            }

            builder.endObject();
        }

        // routing table
        if (metrics.contains(Metric.ROUTING_TABLE)) {
            builder.startObject("routing_table");
            builder.startObject("indices");
            for (IndexRoutingTable indexRoutingTable : routingTable()) {
                builder.startObject(indexRoutingTable.index(), XContentBuilder.FieldCaseConversion.NONE);
                builder.startObject("shards");
                for (IndexShardRoutingTable indexShardRoutingTable : indexRoutingTable) {
                    builder.startArray(Integer.toString(indexShardRoutingTable.shardId().id()));
                    for (ShardRouting shardRouting : indexShardRoutingTable) {
                        shardRouting.toXContent(builder, params);
                    }
                    builder.endArray();
                }
                builder.endObject();
                builder.endObject();
            }
            builder.endObject();
            builder.endObject();
        }

        // routing nodes
        if (metrics.contains(Metric.ROUTING_NODES)) {
            builder.startObject("routing_nodes");
            builder.startArray("unassigned");
            for (ShardRouting shardRouting : readOnlyRoutingNodes().unassigned()) {
                shardRouting.toXContent(builder, params);
            }
            builder.endArray();

            builder.startObject("nodes");
            for (RoutingNode routingNode : readOnlyRoutingNodes()) {
                builder.startArray(routingNode.nodeId() == null ? "null" : routingNode.nodeId(), XContentBuilder.FieldCaseConversion.NONE);
                for (ShardRouting shardRouting : routingNode) {
                    shardRouting.toXContent(builder, params);
                }
                builder.endArray();
            }
            builder.endObject();

            builder.endObject();
        }
        if (metrics.contains(Metric.CUSTOMS)) {
            for (ObjectObjectCursor<String, Custom> cursor : customs) {
                builder.startObject(cursor.key);
                cursor.value.toXContent(builder, params);
                builder.endObject();
            }
        }

        return builder;
    }

    public static Builder builder(ClusterName clusterName) {
        return new Builder(clusterName);
    }

    public static Builder builder(ClusterState state) {
        return new Builder(state);
    }

    public static class Builder {

        private final ClusterName clusterName;
        private long version = 0;
        private String uuid = UNKNOWN_UUID;
        private MetaData metaData = MetaData.EMPTY_META_DATA;
        private RoutingTable routingTable = RoutingTable.EMPTY_ROUTING_TABLE;
        private DiscoveryNodes nodes = DiscoveryNodes.EMPTY_NODES;
        private ClusterBlocks blocks = ClusterBlocks.EMPTY_CLUSTER_BLOCK;
        private final ImmutableOpenMap.Builder<String, Custom> customs;
        private boolean fromDiff;


        public Builder(ClusterState state) {
            this.clusterName = state.clusterName;
            this.version = state.version();
            this.uuid = state.uuid();
            this.nodes = state.nodes();
            this.routingTable = state.routingTable();
            this.metaData = state.metaData();
            this.blocks = state.blocks();
            this.customs = ImmutableOpenMap.builder(state.customs());
            this.fromDiff = false;
        }

        public Builder(ClusterName clusterName) {
            customs = ImmutableOpenMap.builder();
            this.clusterName = clusterName;
        }

        public Builder nodes(DiscoveryNodes.Builder nodesBuilder) {
            return nodes(nodesBuilder.build());
        }

        public Builder nodes(DiscoveryNodes nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder routingTable(RoutingTable.Builder routingTable) {
            return routingTable(routingTable.build());
        }

        public Builder routingResult(RoutingAllocation.Result routingResult) {
            this.routingTable = routingResult.routingTable();
            return this;
        }

        public Builder routingTable(RoutingTable routingTable) {
            this.routingTable = routingTable;
            return this;
        }

        public Builder metaData(MetaData.Builder metaDataBuilder) {
            return metaData(metaDataBuilder.build());
        }

        public Builder metaData(MetaData metaData) {
            this.metaData = metaData;
            return this;
        }

        public Builder blocks(ClusterBlocks.Builder blocksBuilder) {
            return blocks(blocksBuilder.build());
        }

        public Builder blocks(ClusterBlocks blocks) {
            this.blocks = blocks;
            return this;
        }

        public Builder version(long version) {
            this.version = version;
            return this;
        }

        public Builder incrementVersion() {
            this.version = version + 1;
            this.uuid = UNKNOWN_UUID;
            return this;
        }

        public Builder uuid(String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Custom getCustom(String type) {
            return customs.get(type);
        }

        public Builder putCustom(String type, Custom custom) {
            customs.put(type, custom);
            return this;
        }

        public Builder removeCustom(String type) {
            customs.remove(type);
            return this;
        }

        public Builder customs(ImmutableOpenMap<String, Custom> customs) {
            this.customs.putAll(customs);
            return this;
        }

        public Builder fromDiff(boolean fromDiff) {
            this.fromDiff = fromDiff;
            return this;
        }

        public ClusterState build() {
            if (UNKNOWN_UUID.equals(uuid)) {
                uuid = Strings.randomBase64UUID();
            }
            return new ClusterState(clusterName, version, uuid, metaData, routingTable, nodes, blocks, customs.build(), fromDiff);
        }

        public static byte[] toBytes(ClusterState state) throws IOException {
            BytesStreamOutput os = new BytesStreamOutput();
            state.writeTo(os);
            return os.bytes().toBytes();
        }

        /**
         * @param data               input bytes
         * @param localNode          used to set the local node in the cluster state.
         */
        public static ClusterState fromBytes(byte[] data, DiscoveryNode localNode) throws IOException {
            return readFrom(new BytesStreamInput(data), localNode);
        }

        /**
         * @param in                 input stream
         * @param localNode          used to set the local node in the cluster state. can be null.
         */
        public static ClusterState readFrom(StreamInput in, @Nullable DiscoveryNode localNode) throws IOException {
            return PROTO.readFrom(in, localNode);
        }

    }

    @Override
    public Diff diff(ClusterState previousState) {
        return new ClusterStateDiff(previousState, this);
    }

    @Override
    public Diff<ClusterState> readDiffFrom(StreamInput in) throws IOException {
        return new ClusterStateDiff(in, this);
    }

    public ClusterState readFrom(StreamInput in, DiscoveryNode localNode) throws IOException {
        ClusterName clusterName = ClusterName.readClusterName(in);
        Builder builder = new Builder(clusterName);
        builder.version = in.readLong();
        builder.uuid = in.readString();
        builder.metaData = MetaData.Builder.readFrom(in);
        builder.routingTable = RoutingTable.Builder.readFrom(in);
        builder.nodes = DiscoveryNodes.Builder.readFrom(in, localNode);
        builder.blocks = ClusterBlocks.Builder.readClusterBlocks(in);
        int customSize = in.readVInt();
        for (int i = 0; i < customSize; i++) {
            String type = in.readString();
            Custom customIndexMetaData = lookupPrototypeSafe(type).readFrom(in);
            builder.putCustom(type, customIndexMetaData);
        }
        return builder.build();
    }

    @Override
    public ClusterState readFrom(StreamInput in) throws IOException {
        return readFrom(in, nodes.localNode());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        clusterName.writeTo(out);
        out.writeLong(version);
        out.writeString(uuid);
        metaData.writeTo(out);
        routingTable.writeTo(out);
        nodes.writeTo(out);
        blocks.writeTo(out);
        out.writeVInt(customs.size());
        for (ObjectObjectCursor<String, Custom> cursor : customs) {
            out.writeString(cursor.key);
            cursor.value.writeTo(out);
        }
    }

    private static class ClusterStateDiff implements Diff<ClusterState> {

        private final long toVersion;

        private final String fromUuid;

        private final String toUuid;

        private final ClusterName clusterName;

        private final Diff<RoutingTable> routingTable;

        private final Diff<DiscoveryNodes> nodes;

        private final Diff<MetaData> metaData;

        private final Diff<ClusterBlocks> blocks;

        private final Diff<ImmutableOpenMap<String, Custom>> customs;

        public ClusterStateDiff(ClusterState before, ClusterState after) {
            fromUuid = before.uuid;
            toUuid = after.uuid;
            toVersion = after.version;
            clusterName = after.clusterName;
            routingTable = after.routingTable.diff(before.routingTable);
            nodes = after.nodes.diff(before.nodes);
            metaData = after.metaData.diff(before.metaData);
            blocks = after.blocks.diff(before.blocks);
            customs = DiffableUtils.diff(before.customs, after.customs);
        }

        public ClusterStateDiff(StreamInput in, ClusterState proto) throws IOException {
            clusterName = ClusterName.readClusterName(in);
            fromUuid = in.readString();
            toUuid = in.readString();
            toVersion = in.readLong();
            routingTable = proto.routingTable.readDiffFrom(in);
            nodes = proto.nodes.readDiffFrom(in);
            metaData = proto.metaData.readDiffFrom(in);
            blocks = proto.blocks.readDiffFrom(in);
            customs = DiffableUtils.readImmutableOpenMapDiff(in, new KeyedReader<Custom>() {
                @Override
                public Custom readFrom(StreamInput in, String key) throws IOException {
                    return lookupPrototypeSafe(key).readFrom(in);
                }

                @Override
                public Diff<Custom> readDiffFrom(StreamInput in, String key) throws IOException {
                    return lookupPrototypeSafe(key).readDiffFrom(in);
                }
            });
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            clusterName.writeTo(out);
            out.writeString(fromUuid);
            out.writeString(toUuid);
            out.writeLong(toVersion);
            routingTable.writeTo(out);
            nodes.writeTo(out);
            metaData.writeTo(out);
            blocks.writeTo(out);
            customs.writeTo(out);
        }

        @Override
        public ClusterState apply(ClusterState state) {
            Builder builder = new Builder(clusterName);
            if (toUuid.equals(state.uuid)) {
                // no need to read the rest - cluster state didn't change
                return state;
            }
            if (fromUuid.equals(state.uuid) == false) {
                throw new IncompatibleClusterStateVersionException(state.version, state.uuid, toVersion, fromUuid);
            }
            builder.uuid(toUuid);
            builder.version(toVersion);
            builder.routingTable(routingTable.apply(state.routingTable));
            builder.nodes(nodes.apply(state.nodes));
            builder.metaData(metaData.apply(state.metaData));
            builder.blocks(blocks.apply(state.blocks));
            builder.customs(customs.apply(state.customs));
            builder.fromDiff(true);
            return builder.build();
        }
    }

}
