package org.infinispan.server.test.core.persistence;

import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.commons.util.StringPropertyReplacer;
import org.infinispan.server.test.core.InfinispanGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/
public class ContainerDatabase extends Database {
   private final static Log log = LogFactory.getLog(ContainerDatabase.class);
   private final static String ENV_PREFIX = "database.container.env.";
   private final InfinispanGenericContainer container;
   private final int port;

   ContainerDatabase(String type, Properties properties) {
      super(type, properties);
      Map<String, String> env = properties.entrySet().stream().filter(e -> e.getKey().toString().startsWith(ENV_PREFIX))
            .collect(Collectors.toMap(e -> e.getKey().toString().substring(ENV_PREFIX.length()), e -> e.getValue().toString()));
      port = Integer.parseInt(properties.getProperty("database.container.port"));
      ImageFromDockerfile image = new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> {
               builder
                     .from(properties.getProperty("database.container.name") + ":" + properties.getProperty("database.container.tag"))
                     .expose(port)
                     .env(env)
                     .build();
            });
      container = new InfinispanGenericContainer(new GenericContainer(image).withExposedPorts(port).waitingFor(Wait.forListeningPort()));
   }

   @Override
   public void start() {
      container.start();
   }

   @Override
   public void stop() {
      container.stop();
   }

   public int getPort() {
      return container.getMappedPort(port);
   }

   @Override
   public String jdbcUrl() {
      String address = container.getNetworkIpAddress();
      Properties props = new Properties();
      props.setProperty("container.address", address);
      return StringPropertyReplacer.replaceProperties(super.jdbcUrl(), props);
   }

   @Override
   public String username() {
      String username = System.getProperty("org.infinispan.server.test.database.mysql.username", "test");
      Properties props = new Properties();
      props.setProperty("org.infinispan.server.test.database.mysql.username", username);
      return StringPropertyReplacer.replaceProperties(super.username(), props);
   }

   @Override
   public String password() {
      String password = System.getProperty("org.infinispan.server.test.database.mysql.password", "test");
      Properties props = new Properties();
      props.setProperty("org.infinispan.server.test.database.mysql.password", password);
      return StringPropertyReplacer.replaceProperties(super.password(), props);
   }

   // some information will be available only after the start
   @Override
   public String getDatabaseInformation() {
      String imageName = container.getDockerImageName();
      String ipAddress = container.getNetworkIpAddress();
      return "ContainerDatabase{" +
            "port=" + port +
            ", type=" + getType() +
            ", imageName=" + imageName +
            ", ip=" + ipAddress +
            '}';
   }

   @Override
   public boolean isRunning() {
      return container.isRunning();
   }
}
