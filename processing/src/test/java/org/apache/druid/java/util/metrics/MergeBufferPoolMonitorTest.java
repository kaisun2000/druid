/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.java.util.metrics;

import org.apache.druid.collections.BlockingPool;
import org.apache.druid.collections.DefaultBlockingPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MergeBufferPoolMonitorTest
{
  private ExecutorService executorService;

  @Before
  public void setUp()
  {
    executorService = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown()
  {
    executorService.shutdown();
  }

  @Test
  public void testBlockingQueriesCount()
  {
    BlockingPool<ByteBuffer> pool = new DefaultBlockingPool(() -> ByteBuffer.allocate(1024), 1);
    MergeBufferPoolMonitor monitor = new MergeBufferPoolMonitor(pool);

    CountDownLatch latch = new CountDownLatch(1);
    executorService.submit(() -> {
      latch.countDown();
      pool.takeBatch(10);
    });

    try {
      // the latch returns from await() guarantees the above lamda to take buffer from the pool starting to run in the
      // executorService thread
      latch.await();

      // give 1 sec for pool.takeBatch to run and blocking at the pool
      Thread.sleep(1000);

      StubServiceEmitter emitter = new StubServiceEmitter("DummyService", "DummyHost");
      boolean ret = monitor.doMonitor(emitter);
      Assert.assertTrue(ret);

      List<Number> numbers = emitter.getMetricValues("mergebuffer/pendingQueries", Collections.emptyMap());
      Assert.assertEquals(numbers.size(), 1);
      Assert.assertEquals(numbers.get(0).intValue(), 1);
    }
    catch (InterruptedException e) {
      // do nothing
    }

  }
}
