package org.infinispan.test.integration.embedded;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.junit.After;
import org.junit.Test;

/**
 * Test the Infinispan AS module integration
 *
 * @author Tristan Tarrant
 * @since 5.2
 */
public abstract class AbstractInfinispanCoreIT {

   private EmbeddedCacheManager cm;

   @After
   public void cleanUp() {
      if (cm != null)
         cm.stop();
   }

   @Test
   public void testCacheManager() {
      assertTrue("security.manager", Boolean.valueOf(System.getProperty("security.manager")));
      assertNotNull("manager", System.getSecurityManager());

      GlobalConfigurationBuilder gcb = new GlobalConfigurationBuilder();
      gcb.defaultCacheName("default");

      ConfigurationBuilder b = new ConfigurationBuilder();
      b.persistence()
            .addSingleFileStore()
            .maxEntries(5000);

      cm = new DefaultCacheManager(gcb.build(), b.build());
      Cache<String, String> cache = cm.getCache();
      cache.put("a", "a");
      assertEquals("a", cache.get("a"));
   }
}
