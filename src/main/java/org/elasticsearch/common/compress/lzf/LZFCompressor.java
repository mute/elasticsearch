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

package org.elasticsearch.common.compress.lzf;

import com.ning.compress.lzf.ChunkDecoder;
import com.ning.compress.lzf.LZFChunk;
import com.ning.compress.lzf.LZFEncoder;
import com.ning.compress.lzf.util.ChunkDecoderFactory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.elasticsearch.common.compress.*;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.jboss.netty.buffer.ChannelBuffer;

import java.io.IOException;

/**
 */
public class LZFCompressor implements Compressor {

    static final byte[] LUCENE_HEADER = {'L', 'Z', 'F', 0};

    public static final String TYPE = "lzf";

    private ChunkDecoder decoder;

    public LZFCompressor() {
        this.decoder = ChunkDecoderFactory.optimalInstance();
        Loggers.getLogger(LZFCompressor.class).debug("using [{}] decoder", this.decoder.getClass().getSimpleName());
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void configure(Settings settings) {
    }

    @Override
    public boolean isCompressed(byte[] data, int offset, int length) {
        return length >= 3 &&
                data[offset] == LZFChunk.BYTE_Z &&
                data[offset + 1] == LZFChunk.BYTE_V &&
                (data[offset + 2] == LZFChunk.BLOCK_TYPE_COMPRESSED || data[offset + 2] == LZFChunk.BLOCK_TYPE_NON_COMPRESSED);
    }

    @Override
    public boolean isCompressed(ChannelBuffer buffer) {
        int offset = buffer.readerIndex();
        return buffer.readableBytes() >= 3 &&
                buffer.getByte(offset) == LZFChunk.BYTE_Z &&
                buffer.getByte(offset + 1) == LZFChunk.BYTE_V &&
                (buffer.getByte(offset + 2) == LZFChunk.BLOCK_TYPE_COMPRESSED || buffer.getByte(offset + 2) == LZFChunk.BLOCK_TYPE_NON_COMPRESSED);
    }

    @Override
    public boolean isCompressed(IndexInput in) throws IOException {
        long currentPointer = in.getFilePointer();
        // since we have some metdata before the first compressed header, we check on our specific header
        if (in.length() - currentPointer < (LUCENE_HEADER.length)) {
            return false;
        }
        for (int i = 0; i < LUCENE_HEADER.length; i++) {
            if (in.readByte() != LUCENE_HEADER[i]) {
                in.seek(currentPointer);
                return false;
            }
        }
        in.seek(currentPointer);
        return true;
    }

    @Override
    public byte[] uncompress(byte[] data, int offset, int length) throws IOException {
        return decoder.decode(data, offset, length);
    }

    @Override
    public byte[] compress(byte[] data, int offset, int length) throws IOException {
        return LZFEncoder.encode(data, offset, length);
    }

    @Override
    public CompressedStreamInput streamInput(StreamInput in) throws IOException {
        return new LZFCompressedStreamInput(in, decoder);
    }

    @Override
    public CompressedStreamOutput streamOutput(StreamOutput out) throws IOException {
        return new LZFCompressedStreamOutput(out);
    }

    @Override
    public CompressedIndexInput indexInput(IndexInput in) throws IOException {
        return new LZFCompressedIndexInput(in, decoder);
    }

    @Override
    public CompressedIndexOutput indexOutput(IndexOutput out) throws IOException {
        return new LZFCompressedIndexOutput(out);
    }
}
