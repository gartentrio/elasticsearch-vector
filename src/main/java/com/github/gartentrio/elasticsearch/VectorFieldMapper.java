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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.DocValuesFieldExistsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.fielddata.IndexFieldData;
import org.elasticsearch.index.fielddata.plain.BytesBinaryDVIndexFieldData;
import org.elasticsearch.index.mapper.BinaryFieldMapper;
import org.elasticsearch.index.mapper.CustomDocValuesField;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MappedFieldType;
import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.index.mapper.MapperParsingException;
import org.elasticsearch.index.mapper.ParseContext;
import org.elasticsearch.index.mapper.TypeParsers;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;
import org.elasticsearch.search.DocValueFormat;

public class VectorFieldMapper extends BinaryFieldMapper {

	public static final String CONTENT_TYPE = "vector";

	public static class Defaults {
		public static final MappedFieldType FIELD_TYPE = new VectorFieldType();

		static {
			FIELD_TYPE.setIndexOptions(IndexOptions.NONE);
			FIELD_TYPE.freeze();
		}
	}

	public static class Builder extends FieldMapper.Builder<Builder, VectorFieldMapper> {

		public Builder(String name) {
			super(name, Defaults.FIELD_TYPE, Defaults.FIELD_TYPE);
			builder = this;
		}

		@Override
		public VectorFieldMapper build(BuilderContext context) {
			setupFieldType(context);
			return new VectorFieldMapper(name, fieldType, defaultFieldType,
					context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
		}
	}

	public static class TypeParser implements Mapper.TypeParser {
		@Override
		public VectorFieldMapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext)
				throws MapperParsingException {
			Builder builder = new Builder(name);

			if (node.get("doc_values") != null) {
				throw new MapperParsingException("Setting [doc_values] cannot be modified for field [" + name + "]");
			}
			if (node.get("index") != null) {
				throw new MapperParsingException("Setting [index] cannot be modified for field [" + name + "]");
			}

			TypeParsers.parseField(builder, name, node, parserContext);

			return builder;
		}
	}

	static final class VectorFieldType extends MappedFieldType {

		VectorFieldType() {}

		public VectorFieldType(VectorFieldType ref) {
			super(ref);
		}

		@Override
		public MappedFieldType clone() {
			return new VectorFieldType(this);
		}


		@Override
		public String typeName() {
			return CONTENT_TYPE;
		}

		@Override
		public DocValueFormat docValueFormat(String format, ZoneId timeZone) {
			return DocValueFormat.BINARY;
		}

		@Override
		public IndexFieldData.Builder fielddataBuilder(String fullyQualifiedIndexName) {
			failIfNoDocValues();
			return new BytesBinaryDVIndexFieldData.Builder();
		}

		@Override
		public Query existsQuery(QueryShardContext context) {
			return new DocValuesFieldExistsQuery(name());
		}

		@Override
		public Query termQuery(Object value, QueryShardContext context) {
			throw new QueryShardException(context, "Vector fields do not support searching");
		}
	}

	protected VectorFieldMapper(String simpleName, MappedFieldType fieldType, MappedFieldType defaultFieldType,
			Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
		super(simpleName, fieldType, defaultFieldType, indexSettings, multiFields, copyTo);
	}

	@Override
	protected void parseCreateField(ParseContext context, List<IndexableField> fields) throws IOException {
		Float value;
		if (context.externalValueSet()) {
			value = (Float) context.externalValue();
		} else {
            value = context.parser().floatValue();
        }
		if (value != null) {
			String name = fieldType().name();
			VectorField field = (VectorField) context.doc().getByKey(name);
			if (field == null) {
				field = new VectorField(name);
				context.doc().addWithKey(name, field);
			}
			field.addFloat(value);
			if (fieldType().stored()) {
				context.doc().add(new StoredField(name, value));
			}
		}
	}

	@Override
	protected String contentType() {
		return CONTENT_TYPE;
	}

	static class VectorField extends CustomDocValuesField {

		List<Float> value = new ArrayList<>();

		protected VectorField(String name) {
			super(name);
		}

		public void addFloat(Float v) {
			value.add(v);
		}

		@Override
		public BytesRef binaryValue() {
			try {
				byte[] data = new byte[Float.BYTES * value.size()];
				ByteArrayDataOutput buf = new ByteArrayDataOutput(data);
				for (Float v : value) {
					buf.writeInt(Float.floatToIntBits(v));
				}
				return new BytesRef(data);
			} catch (IOException e) {
				throw new ElasticsearchException("Failed to get binary value from vector", e);
			}
		}

	}
}
