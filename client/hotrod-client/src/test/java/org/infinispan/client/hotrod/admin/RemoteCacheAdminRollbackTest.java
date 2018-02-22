package org.infinispan.client.hotrod.admin;

import java.lang.reflect.Method;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.server.hotrod.HotRodSingleNodeTest;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "client.hotrod.admin.RemoteCacheAdminRollbackTest")
public class RemoteCacheAdminRollbackTest extends HotRodSingleNodeTest {

   @Test
   public void testCacheCreationRollback(Method m) {

      String cacheName = m.getName();

      try {
         cacheManager.administration().createCache(cacheName,
               new org.infinispan.configuration.cache.ConfigurationBuilder()
                     .clustering().cacheMode(CacheMode.DIST_SYNC)
                     .build()
         );
      } catch (Exception e) {

         // it should fail because we are using a wrong configuration
      }

      cacheManager.administration().createCache(cacheName,
            new org.infinispan.configuration.cache.ConfigurationBuilder()
                  .build()
      );
   }
}
