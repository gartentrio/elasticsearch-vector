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

package com.github.gartentrio.elasticsearch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.search.lookup.SearchLookup;

public final class VectorScoreScript extends ScoreScript {

	private final BinaryDocValues values;
	private final float[] vector;
	private int doc;

	@SuppressWarnings("unchecked")
	public VectorScoreScript(Map<String, Object> params, SearchLookup lookup, LeafReaderContext leafContext) throws IOException {
		super(params, lookup, leafContext);

		String field = (String) params.get("field");
		if (field == null) {
			throw new IllegalArgumentException("Missing or empty parameter [field]");
		}
		List<Double> vectorList = (List<Double>) params.get("vector");
		if (vectorList != null) {
			vector = new float[vectorList.size()];
			for (int i = 0; i < vector.length; i++) {
				vector[i] = vectorList.get(i).floatValue();
			}
		} else {
			throw new IllegalArgumentException("Missing or empty parameter [vector]");
		}
		values = leafContext.reader().getBinaryDocValues(field);
	}

	@Override
	public void setDocument(int doc) {
		this.doc = doc;
	}

	@Override
	public double execute() {
		try {
			if (values != null && values.advanceExact(doc)) {
				BytesRef data = values.binaryValue();
				return dot(vector, data.bytes, data.offset);
			} else {
				return 0.0;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static final float dot(final float[] vector, final byte[] data, final int offset) {
		float sum = 0;
		for (int i=0, pos=offset; i < vector.length;) {
			sum += vector[i++] * Float.intBitsToFloat(
					((data[pos++] & 0xFF) << 24) | 
					((data[pos++] & 0xFF) << 16) | 
					((data[pos++] & 0xFF) <<  8) | 
					(data[pos++] & 0xFF));
		}
		return Math.max(0, sum);
	}	
}
