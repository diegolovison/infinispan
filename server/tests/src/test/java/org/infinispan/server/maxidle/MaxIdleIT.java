package org.infinispan.server.maxidle;

import static org.junit.Assert.assertNotNull;

import java.util.UUID;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.commons.configuration.XMLStringConfiguration;
import org.infinispan.server.test.core.ServerRunMode;
import org.infinispan.server.test.junit4.InfinispanServerRule;
import org.infinispan.server.test.junit4.InfinispanServerRuleBuilder;
import org.infinispan.server.test.junit4.InfinispanServerTestMethodRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MaxIdleIT {

   @ClassRule
   public static final InfinispanServerRule SERVERS =
         InfinispanServerRuleBuilder.config("configuration/ClusteredServerTest.xml")
               .runMode(ServerRunMode.FORKED)
               .numServers(3)
               .build();

   @Rule
   public InfinispanServerTestMethodRule SERVER_TEST = new InfinispanServerTestMethodRule(SERVERS);

   @Test
   public void testMaxIdle() {
      String cacheName = UUID.randomUUID().toString();
      RemoteCacheManager remoteCacheManager = SERVER_TEST.hotrod().create().getRemoteCacheManager();
      String xml = String.format(
            "<infinispan><cache-container>" +
            "        <transport cluster=\"ISPN\" stack=\"udp\"/>" +
            "        <distributed-cache name=\"%s\" owners=\"2\" segments=\"30\" mode=\"ASYNC\" remote-timeout=\"30000\" start=\"EAGER\">\n" +
            "            <locking isolation=\"READ_COMMITTED\" striping=\"false\" acquire-timeout=\"30000\" concurrency-level=\"1000\"/>\n" +
            "            <transaction mode=\"NONE\"/>\n" +
            "            <memory>\n" +
            "                <object size=\"4000000\"/>\n" +
            "            </memory>\n" +
            "            <expiration max-idle=\"20000\" lifespan=\"300000\"/>\n" +
            "            <persistence passivation=\"true\">\n" +
            "                <file-store max-entries=\"1000000\" shared=\"false\" preload=\"false\" fetch-state=\"true\" purge=\"true\"/>\n" +
            "            </persistence>\n" +
            "        </distributed-cache>" +
            "</cache-container></infinispan>", cacheName);
      RemoteCache<String, String> cache = remoteCacheManager.administration().createCache(cacheName, new XMLStringConfiguration(xml));

      for (int i = 1; i <= 1000; i++) {
         cache.put(String.valueOf(i), "Test" + i);
      }

      long begin = System.currentTimeMillis();
      long now = System.currentTimeMillis();
      while (now - begin < 60_000) {
         for (int i = 1; i <= 1000; i++) {
            String result = cache.get(String.valueOf(i));
            if (result == null) {
               System.out.println("Null value for key: " + i + " after " + (now - begin));
            }
            assertNotNull(result);
            now = System.currentTimeMillis();
         }
      }
   }
}
