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
package org.elasticsearch.action.support;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.Version;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.rest.RestRequest;

import java.io.IOException;

/**
 * Controls how to deal with unavailable concrete indices (closed or missing), how wildcard expressions are expanded
 * to actual indices (all, closed or open indices) and how to deal with wildcard expressions that resolve to no indices.
 */
public class IndicesOptions {

    private static final IndicesOptions[] VALUES;

    private static final byte IGNORE_UNAVAILABLE = 1;
    private static final byte ALLOW_NO_INDICES = 2;
    private static final byte EXPAND_WILDCARDS_OPEN = 4;
    private static final byte EXPAND_WILDCARDS_CLOSED = 8;
    private static final byte FORBID_ALIASES_TO_MULTIPLE_INDICES = 16;
    private static final byte FORBID_CLOSED_INDICES = 32;

    private static final byte STRICT_EXPAND_OPEN = 6;
    private static final byte LENIENT_EXPAND_OPEN = 7;
    private static final byte STRICT_EXPAND_OPEN_CLOSED = 14;
    private static final byte STRICT_EXPAND_OPEN_FORBID_CLOSED = 38;
    private static final byte STRICT_SINGLE_INDEX_NO_EXPAND_FORBID_CLOSED = 48;

    static {
        byte max = 1 << 6;
        VALUES = new IndicesOptions[max];
        for (byte id = 0; id < max; id++) {
            VALUES[id] = new IndicesOptions(id);
        }
    }

    private final byte id;

    private IndicesOptions(byte id) {
        this.id = id;
    }

    /**
     * @return Whether specified concrete indices should be ignored when unavailable (missing or closed)
     */
    public boolean ignoreUnavailable() {
        return (id & IGNORE_UNAVAILABLE) != 0;
    }

    /**
     * @return Whether to ignore if a wildcard expression resolves to no concrete indices.
     *         The `_all` string or empty list of indices count as wildcard expressions too.
     */
    public boolean allowNoIndices() {
        return (id & ALLOW_NO_INDICES) != 0;
    }

    /**
     * @return Whether wildcard expressions should get expanded to open indices
     */
    public boolean expandWildcardsOpen() {
        return (id & EXPAND_WILDCARDS_OPEN) != 0;
    }

    /**
     * @return Whether wildcard expressions should get expanded to closed indices
     */
    public boolean expandWildcardsClosed() {
        return (id & EXPAND_WILDCARDS_CLOSED) != 0;
    }

    /**
     * @return Whether execution on closed indices is allowed.
     */
    public boolean forbidClosedIndices() {
        return (id & FORBID_CLOSED_INDICES) != 0;
    }

    /**
     * @return whether aliases pointing to multiple indices are allowed
     */
    public boolean allowAliasesToMultipleIndices() {
        //true is default here, for bw comp we keep the first 16 values
        //in the array same as before + the default value for the new flag
        return (id & FORBID_ALIASES_TO_MULTIPLE_INDICES) == 0;
    }

    public void writeIndicesOptions(StreamOutput out) throws IOException {
        if (out.getVersion().onOrAfter(Version.V_1_2_2)) {
            out.write(id);
        } else if (out.getVersion().before(Version.V_1_2_0)) {
            // Target node doesn't know about the FORBID_CLOSED_INDICES and FORBID_ALIASES_TO_MULTIPLE_INDICES flags,
            // so unset the bits starting from the 5th position.
            out.write(id & 0xf);
        } else {
            // Target node doesn't know about the FORBID_CLOSED_INDICES flag,
            // so unset the bits starting from the 6th position.
            out.write(id & 0x1f);
        }
    }

    public static IndicesOptions readIndicesOptions(StreamInput in) throws IOException {
        //if we read from a node that doesn't support the newly added flag (allowAliasesToMultipleIndices)
        //we just receive the old corresponding value with the new flag set to true (default)
        byte id = in.readByte();
        if (id >= VALUES.length) {
            throw new ElasticsearchIllegalArgumentException("No valid missing index type id: " + id);
        }
        return VALUES[id];
    }

    public static IndicesOptions fromOptions(boolean ignoreUnavailable, boolean allowNoIndices, boolean expandToOpenIndices, boolean expandToClosedIndices) {
        return fromOptions(ignoreUnavailable, allowNoIndices, expandToOpenIndices, expandToClosedIndices, true, false);
    }

    public static IndicesOptions fromOptions(boolean ignoreUnavailable, boolean allowNoIndices, boolean expandToOpenIndices, boolean expandToClosedIndices, IndicesOptions defaultOptions) {
        return fromOptions(ignoreUnavailable, allowNoIndices, expandToOpenIndices, expandToClosedIndices, defaultOptions.allowAliasesToMultipleIndices(), defaultOptions.forbidClosedIndices());
    }

    static IndicesOptions fromOptions(boolean ignoreUnavailable, boolean allowNoIndices, boolean expandToOpenIndices, boolean expandToClosedIndices, boolean allowAliasesToMultipleIndices, boolean forbidClosedIndices) {
        byte id = toByte(ignoreUnavailable, allowNoIndices, expandToOpenIndices, expandToClosedIndices, allowAliasesToMultipleIndices, forbidClosedIndices);
        return VALUES[id];
    }

    public static IndicesOptions fromRequest(RestRequest request, IndicesOptions defaultSettings) {
        String sWildcards = request.param("expand_wildcards");
        String sIgnoreUnavailable = request.param("ignore_unavailable");
        String sAllowNoIndices = request.param("allow_no_indices");
        if (sWildcards == null && sIgnoreUnavailable == null && sAllowNoIndices == null) {
            return defaultSettings;
        }

        boolean expandWildcardsOpen = false;
        boolean expandWildcardsClosed = false;
        if (sWildcards == null) {
            expandWildcardsOpen = defaultSettings.expandWildcardsOpen();
            expandWildcardsClosed = defaultSettings.expandWildcardsClosed();
        } else {
            String[] wildcards = Strings.splitStringByCommaToArray(sWildcards);
            for (String wildcard : wildcards) {
                if ("open".equals(wildcard)) {
                    expandWildcardsOpen = true;
                } else if ("closed".equals(wildcard)) {
                    expandWildcardsClosed = true;
                } else if ("none".equals(wildcard)) {
                    expandWildcardsOpen = false;
                    expandWildcardsClosed = false;
                } else if ("all".equals(wildcard)) {
                    expandWildcardsOpen = true;
                    expandWildcardsClosed = true;
                } else {
                    throw new ElasticsearchIllegalArgumentException("No valid expand wildcard value [" + wildcard + "]");
                }
            }
        }

        //note that allowAliasesToMultipleIndices is not exposed, always true (only for internal use)
        return fromOptions(
                toBool(sIgnoreUnavailable, defaultSettings.ignoreUnavailable()),
                toBool(sAllowNoIndices, defaultSettings.allowNoIndices()),
                expandWildcardsOpen,
                expandWildcardsClosed,
                defaultSettings.allowAliasesToMultipleIndices(),
                defaultSettings.forbidClosedIndices()
        );
    }

    /**
     * @return indices options that requires every specified index to exist, expands wildcards only to open indices and
     *         allows that no indices are resolved from wildcard expressions (not returning an error).
     * @deprecated in favor of {@link #strictExpandOpen()} whose name makes it clearer what the method actually does.
     */
    @Deprecated
    public static IndicesOptions strict() {
        return strictExpandOpen();
    }

    /**
     * @return indices options that requires every specified index to exist, expands wildcards only to open indices and
     *         allows that no indices are resolved from wildcard expressions (not returning an error).
     */
    public static IndicesOptions strictExpandOpen() {
        return VALUES[STRICT_EXPAND_OPEN];
    }

    /**
     * @return indices options that requires every specified index to exist, expands wildcards only to open indices,
     *         allows that no indices are resolved from wildcard expressions (not returning an error) and forbids the
     *         use of closed indices by throwing an error.
     */
    public static IndicesOptions strictExpandOpenAndForbidClosed() {
        return VALUES[STRICT_EXPAND_OPEN_FORBID_CLOSED];
    }

    /**
     * @return indices option that requires every specified index to exist, expands wildcards to both open and closed
     * indices and allows that no indices are resolved from wildcard expressions (not returning an error).
     */
    public static IndicesOptions strictExpand() {
        return VALUES[STRICT_EXPAND_OPEN_CLOSED];
    }

    /**
     * @return indices option that requires each specified index or alias to exist, doesn't expand wildcards and
     * throws error if any of the aliases resolves to multiple indices
     */
    public static IndicesOptions strictSingleIndexNoExpandForbidClosed() {
        return VALUES[STRICT_SINGLE_INDEX_NO_EXPAND_FORBID_CLOSED];
    }

    /**
     * @return indices options that ignores unavailable indices, expands wildcards only to open indices and
     *         allows that no indices are resolved from wildcard expressions (not returning an error).
     * @deprecated in favor of {@link #lenientExpandOpen()} whose name makes it clearer what the method actually does.
     */
    @Deprecated
    public static IndicesOptions lenient() {
        return lenientExpandOpen();
    }

    /**
     * @return indices options that ignores unavailable indices, expands wildcards only to open indices and
     *         allows that no indices are resolved from wildcard expressions (not returning an error).
     */
    public static IndicesOptions lenientExpandOpen() {
        return VALUES[LENIENT_EXPAND_OPEN];
    }

    private static byte toByte(boolean ignoreUnavailable, boolean allowNoIndices, boolean wildcardExpandToOpen,
                               boolean wildcardExpandToClosed, boolean allowAliasesToMultipleIndices, boolean forbidClosedIndices) {
        byte id = 0;
        if (ignoreUnavailable) {
            id |= IGNORE_UNAVAILABLE;
        }
        if (allowNoIndices) {
            id |= ALLOW_NO_INDICES;
        }
        if (wildcardExpandToOpen) {
            id |= EXPAND_WILDCARDS_OPEN;
        }
        if (wildcardExpandToClosed) {
            id |= EXPAND_WILDCARDS_CLOSED;
        }
        //true is default here, for bw comp we keep the first 16 values
        //in the array same as before + the default value for the new flag
        if (!allowAliasesToMultipleIndices) {
            id |= FORBID_ALIASES_TO_MULTIPLE_INDICES;
        }
        if (forbidClosedIndices) {
            id |= FORBID_CLOSED_INDICES;
        }
        return id;
    }

    private static boolean toBool(String sValue, boolean defaultValue) {
        if (sValue == null) {
            return defaultValue;
        }
        return !(sValue.equals("false") || sValue.equals("0") || sValue.equals("off"));
    }
}
