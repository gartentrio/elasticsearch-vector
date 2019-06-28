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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.apache.commons.io.FileUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.http.BindHttpException;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.transport.Netty4Plugin;

import com.google.common.annotations.VisibleForTesting;


public class TestServer {

	private static final int MAX_PORT_RETRIES = 20;

	private Node node;
	private int port;
	private String dataDirectory;
	private String homeDirectory;

	public TestServer() throws NodeValidationException {
		this.dataDirectory = "target/elasticsearch-data";
		this.homeDirectory = "target/elasticsearch-home";
		Settings.Builder settings = Settings.builder()
				.put("http.type", "netty4")
				.put("path.data", dataDirectory)
				.put("path.home", homeDirectory)
				.put("node.max_local_storage_nodes", 10000)
				.put("node.name", "test");

		for (int numRetries = MAX_PORT_RETRIES; numRetries >= 0; numRetries--) {
			try {
				this.port = new Random().nextInt(500) + 4200;
				settings.put("http.port", String.valueOf(this.port));                
				node = new Node(
						InternalSettingsPreparer.prepareEnvironment(settings.build(), Collections.emptyMap(), null, null), 
						Arrays.asList(Netty4Plugin.class, VectorPlugin.class), false) {			
				};
				node.start();
				System.out.println(TestServer.class.getName() + ": Using port: " + this.port);
				break;
			} catch (BindHttpException ex) {
				if (numRetries == 0) {
					throw new ElasticsearchException("Could not find any free port in range: [" + 
							(this.port - MAX_PORT_RETRIES) + " - " + this.port+"]", ex);
				}
				System.out.println("Port already in use (" + this.port + "). Trying another port...");
			}
		}
	}

	public Client getClient() {
		return node.client();
	}

	public void shutdown() {
		if (node != null) {
			try {
				node.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		deleteDataDirectory();
	}

	private void deleteDataDirectory() {
		try {
			FileUtils.deleteDirectory(new File(dataDirectory));
		} catch (IOException e) {
			throw new RuntimeException("Could not delete data directory of embedded elasticsearch server", e);
		}
	}

	@VisibleForTesting
	public int getPort() {
		return port;
	}
}
