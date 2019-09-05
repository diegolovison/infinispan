package org.infinispan.server.xsite;

import static org.junit.Assert.assertEquals;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.server.test.InfinispanServerTestMethodRule;
import org.infinispan.server.test.XSiteInfinispanServerRule;
import org.infinispan.server.test.category.XSite;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(XSite.class)
public class XSiteCacheOperations {

   @ClassRule
   public static XSiteInfinispanServerRule SERVERS = XSiteIT.SERVERS;

   @Rule
   public InfinispanServerTestMethodRule SERVER_TEST = new InfinispanServerTestMethodRule(SERVERS);

   @Test
   public void testHotRodOperations() {
      RemoteCache<String, String> cache = SERVER_TEST.getRemoteCacheManager().getCache("users");
      cache.put("k1", "v1");
      assertEquals(1, cache.size());
      assertEquals("v1", cache.get("k1"));
      cache.remove("k1");
      assertEquals(0, cache.size());
   }
}
