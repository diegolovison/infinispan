package org.infinispan.client.hotrod;

import static org.testng.Assert.assertTrue;

import org.infinispan.client.hotrod.impl.ClientStatistics;
import org.infinispan.commons.time.DefaultTimeService;
import org.infinispan.commons.time.TimeService;
import org.testng.annotations.Test;

/**
 * @since 9.4
 * @author Diego Lovison
 */
@Test(groups = "functional", testName = "client.hotrod.ClientStatisticsTest")
public class ClientStatisticsTest {

   public void testAverageRemoteStoreTime() throws InterruptedException {

      TimeService timeService = DefaultTimeService.INSTANCE;
      ClientStatistics clientStatistics = new ClientStatistics(true, timeService);
      // given: a put operation
      long now = timeService.time();
      // when: network is slow
      Thread.sleep(1200);
      clientStatistics.dataStore(now, 1);
      // then:
      assertTrue(clientStatistics.getAverageRemoteStoreTime() > 0);
   }

   public void testAverageRemoteReadTime() throws InterruptedException {

      TimeService timeService = DefaultTimeService.INSTANCE;
      ClientStatistics clientStatistics = new ClientStatistics(true, timeService);
      // given: a get operation
      long now = timeService.time();
      // when: network is slow
      Thread.sleep(1200);
      clientStatistics.dataRead(true, now, 1);
      // then:
      assertTrue(clientStatistics.getAverageRemoteReadTime() > 0);
   }

   public void testAverageRemovesTime() throws InterruptedException {

      TimeService timeService = DefaultTimeService.INSTANCE;
      ClientStatistics clientStatistics = new ClientStatistics(true, timeService);
      // given: a remove operation
      long now = timeService.time();
      // when: network is slow
      Thread.sleep(1200);
      clientStatistics.dataRemove(now, 1);
      // then:
      assertTrue(clientStatistics.getAverageRemoteRemovesTime() > 0);
   }
}
