package org.infinispan.server.xsite;

import org.infinispan.server.test.InfinispanServerTestConfiguration;
import org.infinispan.server.test.XSiteInfinispanServerRule;
import org.infinispan.server.test.category.XSite;
import org.junit.ClassRule;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({XSiteCacheOperations.class})
@Category(XSite.class)
public class XSiteIT {

   @ClassRule
   public static final XSiteInfinispanServerRule SERVERS = new XSiteInfinispanServerRule(
         new InfinispanServerTestConfiguration("configuration/XSiteServerTest.xml").numServers(2)
   );

}
