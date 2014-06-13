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

package org.elasticsearch.index.fielddata.ordinals;

import org.apache.lucene.util.BytesRef;
import org.elasticsearch.index.fielddata.BytesValues;



/**
 * A thread safe ordinals abstraction. Ordinals can only be positive integers.
 */
public abstract class Ordinals {

    public static final ValuesHolder NO_VALUES = new ValuesHolder() {
        @Override
        public BytesRef getValueByOrd(long ord) {
            throw new UnsupportedOperationException();
        }
    };

    /**
     * The memory size this ordinals take.
     */
    public abstract long getMemorySizeInBytes();

    public abstract BytesValues.WithOrdinals ordinals(ValuesHolder values);

    public final BytesValues.WithOrdinals ordinals() {
        return ordinals(NO_VALUES);
    }

    public static interface ValuesHolder {

        public abstract BytesRef getValueByOrd(long ord);

    }

}
