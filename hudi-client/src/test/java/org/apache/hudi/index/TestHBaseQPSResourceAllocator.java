/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hudi.index;

import org.apache.hudi.common.HoodieClientTestHarness;
import org.apache.hudi.common.HoodieTestDataGenerator;
import org.apache.hudi.common.util.Option;
import org.apache.hudi.config.HoodieCompactionConfig;
import org.apache.hudi.config.HoodieHBaseIndexConfig;
import org.apache.hudi.config.HoodieIndexConfig;
import org.apache.hudi.config.HoodieStorageConfig;
import org.apache.hudi.config.HoodieWriteConfig;
import org.apache.hudi.index.hbase.DefaultHBaseQPSResourceAllocator;
import org.apache.hudi.index.hbase.HBaseIndex;
import org.apache.hudi.index.hbase.HBaseIndexQPSResourceAllocator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestHBaseQPSResourceAllocator extends HoodieClientTestHarness {

  private static final String TABLE_NAME = "test_table";
  private static final String QPS_TEST_SUFFIX_PATH = "qps_test_suffix";
  private HBaseTestingUtility utility;
  private Configuration hbaseConfig;

  @BeforeEach
  public void setUp() throws Exception {
    utility = new HBaseTestingUtility();
    utility.startMiniCluster();
    hbaseConfig = utility.getConnection().getConfiguration();
    initSparkContexts("TestQPSResourceAllocator");

    initPath();
    basePath = tempDir.resolve(QPS_TEST_SUFFIX_PATH).toAbsolutePath().toString();
    // Initialize table
    initMetaClient();
  }

  @AfterEach
  public void tearDown() throws Exception {
    cleanupSparkContexts();
    cleanupMetaClient();
    if (utility != null) {
      utility.shutdownMiniCluster();
    }
  }

  @Test
  public void testsDefaultQPSResourceAllocator() {
    HoodieWriteConfig config = getConfig(Option.empty());
    HBaseIndex index = new HBaseIndex(config);
    HBaseIndexQPSResourceAllocator hBaseIndexQPSResourceAllocator = index.createQPSResourceAllocator(config);
    assertEquals(hBaseIndexQPSResourceAllocator.getClass().getName(),
        DefaultHBaseQPSResourceAllocator.class.getName());
    assertEquals(config.getHbaseIndexQPSFraction(),
        hBaseIndexQPSResourceAllocator.acquireQPSResources(config.getHbaseIndexQPSFraction(), 100), 0.0f);
  }

  @Test
  public void testsExplicitDefaultQPSResourceAllocator() {
    HoodieWriteConfig config = getConfig(Option.of(HoodieHBaseIndexConfig.DEFAULT_HBASE_INDEX_QPS_ALLOCATOR_CLASS));
    HBaseIndex index = new HBaseIndex(config);
    HBaseIndexQPSResourceAllocator hBaseIndexQPSResourceAllocator = index.createQPSResourceAllocator(config);
    assertEquals(hBaseIndexQPSResourceAllocator.getClass().getName(),
        DefaultHBaseQPSResourceAllocator.class.getName());
    assertEquals(config.getHbaseIndexQPSFraction(),
        hBaseIndexQPSResourceAllocator.acquireQPSResources(config.getHbaseIndexQPSFraction(), 100), 0.0f);
  }

  @Test
  public void testsInvalidQPSResourceAllocator() {
    HoodieWriteConfig config = getConfig(Option.of("InvalidResourceAllocatorClassName"));
    HBaseIndex index = new HBaseIndex(config);
    HBaseIndexQPSResourceAllocator hBaseIndexQPSResourceAllocator = index.createQPSResourceAllocator(config);
    assertEquals(hBaseIndexQPSResourceAllocator.getClass().getName(),
        DefaultHBaseQPSResourceAllocator.class.getName());
    assertEquals(config.getHbaseIndexQPSFraction(),
        hBaseIndexQPSResourceAllocator.acquireQPSResources(config.getHbaseIndexQPSFraction(), 100), 0.0f);
  }

  private HoodieWriteConfig getConfig(Option<String> resourceAllocatorClass) {
    HoodieHBaseIndexConfig hoodieHBaseIndexConfig = getConfigWithResourceAllocator(resourceAllocatorClass);
    return getConfigBuilder(hoodieHBaseIndexConfig).build();
  }

  private HoodieWriteConfig.Builder getConfigBuilder(HoodieHBaseIndexConfig hoodieHBaseIndexConfig) {
    return HoodieWriteConfig.newBuilder().withPath(basePath).withSchema(HoodieTestDataGenerator.TRIP_EXAMPLE_SCHEMA)
        .withParallelism(1, 1)
        .withCompactionConfig(HoodieCompactionConfig.newBuilder().compactionSmallFileSize(1024 * 1024)
            .withInlineCompaction(false).build())
        .withAutoCommit(false).withStorageConfig(HoodieStorageConfig.newBuilder().limitFileSize(1024 * 1024).build())
        .forTable("test-trip-table").withIndexConfig(HoodieIndexConfig.newBuilder()
            .withIndexType(HoodieIndex.IndexType.HBASE).withHBaseIndexConfig(hoodieHBaseIndexConfig).build());
  }

  private HoodieHBaseIndexConfig getConfigWithResourceAllocator(Option<String> resourceAllocatorClass) {
    HoodieHBaseIndexConfig.Builder builder = new HoodieHBaseIndexConfig.Builder()
        .hbaseZkPort(Integer.parseInt(hbaseConfig.get("hbase.zookeeper.property.clientPort")))
        .hbaseZkQuorum(hbaseConfig.get("hbase.zookeeper.quorum")).hbaseTableName(TABLE_NAME).hbaseIndexGetBatchSize(100);
    if (resourceAllocatorClass.isPresent()) {
      builder.withQPSResourceAllocatorType(resourceAllocatorClass.get());
    }
    return builder.build();
  }
}
