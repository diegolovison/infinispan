package org.infinispan.test.integration.as.client;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolverSystem;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the Infinispan AS remote client module integration
 *
 * @author Pedro Ruivo
 * @since 7.0
 */
@RunWith(Arquillian.class)
public class HotRodClientIT {

   private RemoteCacheManager rcm;

   @Deployment
   public static Archive<?> deployment() {
      MavenResolverSystem resolver = Maven.resolver();

      WebArchive war = ShrinkWrap.create(WebArchive.class, "infinispan-server-integration.war");
      String jbossDeploymentStructure = "<jboss-deployment-structure xmlns=\"urn:jboss:deployment-structure:1.1\">\n" +
            "    <deployment>\n" +
            "        <dependencies>\n" +
            "            <system export=\"true\">\n" +
            "                <paths>\n" +
            "                    <path name=\"sun/reflect\"/>\n" +
            "                </paths>\n" +
            "            </system>\n" +
            "        </dependencies>\n" +
            "    </deployment>\n" +
            "</jboss-deployment-structure>";
      war.add(new StringAsset(jbossDeploymentStructure), "WEB-INF/jboss-deployment-structure.xml");
      addLibrary(war, resolver
            .loadPomFromFile("pom.xml")
            .resolve("org.infinispan:infinispan-client-hotrod")
            .withTransitivity().as(File.class));
      return war;
   }

   @After
   public void cleanUp() {
      if (rcm != null)
         rcm.stop();
   }

   @Test
   public void testCacheManager() {
      rcm = createCacheManager();
      RemoteCache<String, String> cache = rcm.getCache();
      cache.clear();
      cache.put("a", "a");
      assertEquals("a", cache.get("a"));
   }

   private static RemoteCacheManager createCacheManager() {
      return new RemoteCacheManager(createConfiguration(), true);
   }

   private static Configuration createConfiguration() {
      ConfigurationBuilder config = new ConfigurationBuilder();
      config.addServer().host("127.0.0.1");
      return config.build();
   }

   private static void addLibrary(WebArchive war, File[]... libGroup) {
      for (File[] group : libGroup) {
         war.addAsLibraries(group);
      }
   }
}
