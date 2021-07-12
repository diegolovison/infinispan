package org.infinispan.test.integration.thirdparty.embedded;

import org.infinispan.test.integration.embedded.AbstractInfinispanCoreIT;
import org.infinispan.test.integration.thirdparty.DeploymentHelper;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;

/**
 * Test the Infinispan AS module integration
 *
 * @author Tristan Tarrant
 * @since 5.2
 */
@RunWith(Arquillian.class)
public class InfinispanCoreIT extends AbstractInfinispanCoreIT {

   @Deployment
   public static Archive<?> deployment() {
      WebArchive war = DeploymentHelper.createDeployment();
      war.addClass(AbstractInfinispanCoreIT.class);
      war.add(permission(), "META-INF/permissions.xml");
      DeploymentHelper.addLibrary(war, "org.infinispan:infinispan-core");
      return war;
   }

   private static Asset permission() {
      return new StringAsset("<permissions version=\"7\">\n" +
            "  <permission>\n" +
            "  <class-name>java.lang.reflect.ReflectPermission</class-name>\n" +
            "  <name>suppressAccessChecks</name>\n" +
            "  <actions>read,write</actions>\n" +
            "  </permission>\n" +
            "  <permission>\n" +
            "  <class-name>java.lang.RuntimePermission</class-name>\n" +
            "  <name>*</name>\n" +
            "  <actions>read,write</actions>\n" +
            "  </permission>\n" +
            "  <permission>\n" +
            "  <class-name>java.util.PropertyPermission</class-name>\n" +
            "  <name>*</name>\n" +
            "  <actions>read,write</actions>\n" +
            "  </permission>\n" +
            "  <permission>\n" +
            "  <class-name>java.io.FilePermission</class-name>\n" +
            "  <name>META-INF/infinispan-features.properties</name>\n" +
            "  <actions>read,write</actions>\n" +
            "  </permission>\n" +
            "</permissions>");
   }
}
