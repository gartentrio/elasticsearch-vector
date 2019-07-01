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
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.script.ScoreScript.LeafFactory;
import org.elasticsearch.search.lookup.SearchLookup;

public class VectorScoreScriptFactory implements LeafFactory {
	private Map<String, Object> params;
    private SearchLookup lookup;
    
    public VectorScoreScriptFactory(Map<String, Object> params, SearchLookup lookup) {
        this.params = params;
        this.lookup = lookup;
    }
	
    public boolean needs_score() {
        return false;
    }

	@Override
	public ScoreScript newInstance(LeafReaderContext ctx) throws IOException {
		return new VectorScoreScript(params, lookup, ctx);
	}
}