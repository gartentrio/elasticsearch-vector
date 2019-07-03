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

import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Created by Lior Knaany on 4/7/18.
 */
public class PluginTest {

    private static TestServer esServer;
    private static RestClient esClient;

    @BeforeClass
    public static void init() throws Exception {
        esServer = new TestServer();
        esClient = RestClient.builder(new HttpHost("localhost", esServer.getPort(), "http")).build();

        // delete test index if exists
        try {
        	Request deleteRequest = new Request("DELETE", "/test");
            esClient.performRequest(deleteRequest);
        } catch (Exception e) {}

        // create test index
        String mappingJson = "{\n" +
                "  \"mappings\": {\n" +
        		"    \"_source\": { \"enabled\": false }, \n" +
                "    \"properties\": {\n" +
                "      \"image_vector\": {\n" +
                "        \"type\": \"vector\",\n" +
                "        \"store\": true\n" +
                "      },\n" +
                "      \"image_id\": {\n" +
                "        \"type\": \"long\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        Request putRequest = new Request("PUT", "/test");
        putRequest.setJsonEntity(mappingJson);
        esClient.performRequest(putRequest);
    }

    @Test
    public void test() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TestImage[] imgs = {
    		new TestImage(0, new float[] {0.26726124f, 0.53452248f, 0.80178373f}),
            new TestImage(1, new float[] {0.80178373f, 0.53452248f, 0.26726124f})};

        for (int i = 0; i < imgs.length; i++) {
        	TestImage t = imgs[i];
            String json = mapper.writeValueAsString(t);
            System.out.println(json);
            Request indexRequest = new Request("POST", "/test/_doc/" + t.imageId);
            indexRequest.addParameter("refresh", "true");
            indexRequest.setJsonEntity(json);
            Response put = esClient.performRequest(indexRequest);
            System.out.println(put);
            System.out.println(EntityUtils.toString(put.getEntity()));
            int statusCode = put.getStatusLine().getStatusCode();
            Assert.assertTrue(statusCode == 200 || statusCode == 201);
        }

        // Test dot-product score function
        String body = "{" +
                "  \"query\": {" +
                "    \"function_score\": {" +
                "      \"boost_mode\": \"replace\"," +
                "      \"script_score\": {" +
                "        \"script\": {" +
                "          \"lang\": \"vector_score\"," +
                "          \"source\": \"vector_score\"," +
                "          \"params\": {" +
                "            \"field\": \"image_vector\"," +
                "            \"vector\": [" +
                "               0.26726124, 0.53452248, 0.80178373" +
                "             ]" +
                "          }" +
                "        }" +
                "      }" +
                "    }" +
                "  }," +
                "  \"size\": 100" +
                "}";
        Request searchRequest = new Request("POST", "/test/_search");
        searchRequest.addParameter("stored_fields", "image_vector");
        searchRequest.setJsonEntity(body);
        Response res = esClient.performRequest(searchRequest);
        System.out.println(res);
        String resBody = EntityUtils.toString(res.getEntity());
        System.out.println(resBody);
        Assert.assertEquals("search should return status code 200", 200, res.getStatusLine().getStatusCode());
        Assert.assertTrue(String.format("There should be %d documents in the search response", imgs.length), resBody.contains("\"hits\":{\"total\":{\"value\":" + imgs.length));
        // Testing Scores
        ArrayNode hitsJson = (ArrayNode)mapper.readTree(resBody).get("hits").get("hits");
        Assert.assertEquals(1.0, hitsJson.get(0).get("_score").asDouble(), 0);
        Assert.assertEquals(0.71428573, hitsJson.get(1).get("_score").asDouble(), 0);
  }

    @AfterClass
    public static void shutdown() {
        try {
            esClient.close();
            esServer.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
