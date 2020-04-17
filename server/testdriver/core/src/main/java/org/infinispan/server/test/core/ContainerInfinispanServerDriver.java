package org.infinispan.server.test.core;

import static org.infinispan.server.test.core.ContainerUtil.getIpAddressFromContainer;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.commons.logging.Log;
import org.infinispan.commons.test.CommonsTestingUtil;
import org.infinispan.commons.test.Exceptions;
import org.infinispan.commons.test.ThreadLeakChecker;
import org.infinispan.commons.util.StringPropertyReplacer;
import org.infinispan.commons.util.Version;
import org.infinispan.server.Server;
import org.infinispan.util.logging.LogFactory;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.Base58;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.Network;

/**
 * @author Tristan Tarrant &lt;tristan@infinispan.org&gt;
 * @since 10.0
 **/
public class ContainerInfinispanServerDriver extends AbstractInfinispanServerDriver {
   private static final Log log = org.infinispan.commons.logging.LogFactory.getLog(ContainerInfinispanServerDriver.class);
   private static final String STARTUP_MESSAGE_REGEX = ".*ISPN080001.*";
   private static final String SHUTDOWN_MESSAGE_REGEX = ".*ISPN080003.*";
   private static final int TIMEOUT_SECONDS = Integer.getInteger(TestSystemPropertyNames.INFINISPAN_TEST_SERVER_CONTAINER_TIMEOUT_SECONDS, 45);
   public static final String INFINISPAN_SERVER_HOME = "/opt/infinispan";
   public static final int JMX_PORT = 9999;
   public static final String JDK_BASE_IMAGE_NAME = "jboss/base-jdk:11";
   private final List<GenericContainer> containers;
   private final boolean preferContainerExposedPorts;
   private String name;
   CountdownLatchLoggingConsumer latch;
   ImageFromDockerfile image;

   protected ContainerInfinispanServerDriver(InfinispanServerTestConfiguration configuration) {
      super(
            configuration,
            getDockerBridgeAddress()
      );
      this.containers = new ArrayList<>(configuration.numServers());
      this.preferContainerExposedPorts = Boolean.getBoolean(TestSystemPropertyNames.INFINISPAN_TEST_SERVER_CONTAINER_PREFER_CONTAINER_EXPOSED_PORTS);
   }

   static InetAddress getDockerBridgeAddress() {
      DockerClient dockerClient = DockerClientFactory.instance().client();
      Network bridge = dockerClient.inspectNetworkCmd().withNetworkId("bridge").exec();
      String gateway = bridge.getIpam().getConfig().get(0).getGateway();
      return Exceptions.unchecked(() -> InetAddress.getByName(gateway));
   }

   @Override
   protected void start(String name, File rootDir, String configurationFile) {
      this.name = name;
      // Build a skeleton server layout
      createServerHierarchy(rootDir);
      // Build the command-line that launches the server
      List<String> args = new ArrayList<>();
      args.add("bin/server.sh");
      args.add("-c");
      args.add(configurationFile);
      args.add("-b");
      args.add("SITE_LOCAL");
      args.add("-Djgroups.bind.address=SITE_LOCAL");
      args.add("-Dinfinispan.cluster.name=" + name);
      args.add("-D" + TEST_HOST_ADDRESS + "=" + testHostAddress.getHostAddress());
      if (configuration.isJMXEnabled()) {
         args.add("-Dcom.sun.management.jmxremote.port=" + JMX_PORT);
         args.add("-Dcom.sun.management.jmxremote.authenticate=false");
         args.add("-Dcom.sun.management.jmxremote.ssl=false");
      }
      Properties properties = new Properties();
      properties.setProperty(Server.INFINISPAN_SERVER_CONFIG_PATH, Paths.get(INFINISPAN_SERVER_HOME, Server.DEFAULT_SERVER_CONFIG).toString());
      properties.setProperty(Server.INFINISPAN_CLUSTER_NAME, name);
      properties.setProperty(TEST_HOST_ADDRESS, testHostAddress.getHostName());
      configuration.properties().forEach((k, v) -> args.add("-D" + k + "=" + StringPropertyReplacer.replaceProperties((String) v, properties)));
      boolean preserveImageAfterTest = Boolean.parseBoolean(configuration.properties().getProperty(TestSystemPropertyNames.INFINISPAN_TEST_SERVER_PRESERVE_IMAGE, "false"));
      Path tmp = Paths.get(CommonsTestingUtil.tmpDirectory(this.getClass()));

      File libDir = new File(rootDir, "lib");
      libDir.mkdirs();
      copyArtifactsToUserLibDir(libDir);

      image = new ImageFromDockerfile("testcontainers/" + Base58.randomString(16).toLowerCase(), !preserveImageAfterTest)
            .withFileFromPath("test", rootDir.toPath())
            .withFileFromPath("tmp", tmp)
            .withFileFromPath("lib", libDir.toPath());
      final boolean prebuiltImage;
      final String imageName;
      String baseImageName = configuration.properties().getProperty(TestSystemPropertyNames.INFINISPAN_TEST_SERVER_BASE_IMAGE_NAME);
      if (baseImageName == null) {
         String serverOutputDir = configuration.properties().getProperty(TestSystemPropertyNames.INFINISPAN_TEST_SERVER_DIR);
         if (serverOutputDir == null) {
            // We try to use the latest public image for this major.minor version
            imageName = "infinispan/server:" + Version.getMajorMinor();
            prebuiltImage = true;
            log.infof("Using prebuilt image '%s'", imageName);
         } else {
            // We build our local image based on the supplied server
            Path serverOutputPath = Paths.get(serverOutputDir).normalize();
            imageName = JDK_BASE_IMAGE_NAME;
            image
                  .withFileFromPath("target", serverOutputPath.getParent())
                  .withFileFromPath("src", serverOutputPath.getParent().getParent().resolve("src"))
                  .withFileFromPath("build", serverOutputPath);
            prebuiltImage = false;
            log.infof("Using local image from server built at '%s'", serverOutputPath);
         }
      } else {
         imageName = baseImageName;
         prebuiltImage = true;
         log.infof("Using prebuilt image '%s'", imageName);
      }
      image.withDockerfileFromBuilder(builder -> {
         builder
               .from(imageName)
               .env("INFINISPAN_SERVER_HOME", INFINISPAN_SERVER_HOME)
               .env("INFINISPAN_VERSION", Version.getVersion())
               .env("LAUNCH_ISPN_IN_BACKGROUND", Boolean.TRUE.toString())
               .label("name", "Infinispan Server")
               .label("version", Version.getVersion())
               .label("release", Version.getVersion())
               .label("architecture", "x86_64");

         if (!prebuiltImage) {
            builder.copy("build", INFINISPAN_SERVER_HOME);
         }

         builder.copy("test", INFINISPAN_SERVER_HOME + "/server")
               .copy("tmp", INFINISPAN_SERVER_HOME)
               .workDir(INFINISPAN_SERVER_HOME)
               .entryPoint(args.toArray(new String[]{}))
               .expose(
                     11222, // Protocol endpoint
                     11221, // Memcached endpoint
                     7800,  // JGroups TCP
                     46655, // JGroups UDP
                     9999   // JMX Remoting
               );

         builder.copy("lib", serverPathFrom("lib"));
         builder
               .user("root")
               .run("chown", "-R", "jboss:0", INFINISPAN_SERVER_HOME)
               .run("chmod", "-R", "g+rw", INFINISPAN_SERVER_HOME)
               .user("jboss");
      });

      if (configuration.isParallelStartup()) {
         latch = new CountdownLatchLoggingConsumer(configuration.numServers(), STARTUP_MESSAGE_REGEX);
         IntStream.range(0, configuration.numServers()).forEach(i -> createContainer(i, name, rootDir));
         Exceptions.unchecked(() -> latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
      } else {
         for (int i = 0; i < configuration.numServers(); i++) {
            latch = new CountdownLatchLoggingConsumer(1, STARTUP_MESSAGE_REGEX);
            createContainer(i, name, rootDir);
            Exceptions.unchecked(() -> latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
         }
      }
   }

   private void createContainer(int i, String name, File rootDir) {
      GenericContainer container = createContainer(i, rootDir);
      containers.add(i, container);
      log.infof("Starting container %s-%d", name, i);
      container.start();
   }

   private GenericContainer createContainer(int i, File rootDir) {

      String volumeName = UUID.randomUUID().toString();
      DockerClientFactory.instance().client().createVolumeCmd().withName(volumeName).exec();

      GenericContainer container = new GenericContainer<>(image)
         .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withMounts(
            Arrays.asList(new Mount().withSource(volumeName).withTarget(serverPathFrom("data")).withType(MountType.VOLUME))
         ));
      // Process any enhancers
      withLogConsumer(container, i);
      return container;
   }

   private void copyArtifactsToUserLibDir(File libDir) {
      // Maven artifacts
      String propertyArtifacts = configuration.properties().getProperty(TestSystemPropertyNames.INFINISPAN_TEST_SERVER_EXTRA_LIBS);
      String[] artifacts = propertyArtifacts != null ? propertyArtifacts.replaceAll("\\s+", "").split(",") : configuration.mavenArtifacts();
      if (artifacts != null && artifacts.length > 0) {
         MavenResolvedArtifact[] archives = Maven.resolver().resolve(artifacts).withoutTransitivity().asResolvedArtifact();
         for (MavenResolvedArtifact archive : archives) {
            Exceptions.unchecked(() -> {
               Path source = archive.asFile().toPath();
               Files.copy(source, libDir.toPath().resolve(source.getFileName()));
            });
         }
      }
      // Supplied artifacts
      if (configuration.archives() != null) {
         for (JavaArchive artifact : configuration.archives()) {
            File jar = libDir.toPath().resolve(artifact.getName()).toFile();
            jar.setWritable(true, false);
            artifact.as(ZipExporter.class).exportTo(jar, true);
         }
      }
   }

   @Override
   protected void stop() {
      for (int i = 0; i < containers.size(); i++) {
         log.infof("Stopping container %s-%d", name, i);
         containers.get(i).stop();
         log.infof("Stopped container %s-%d", name, i);
      }
      containers.clear();

      // See https://github.com/testcontainers/testcontainers-java/issues/2276
      ThreadLeakChecker.ignoreThreadsContaining("tc-okhttp-stream-");
   }

   @Override
   public boolean isRunning(int server) {
      return containers.get(server).isRunning();
   }

   @Override
   public InetSocketAddress getServerSocket(int server, int port) {
      return new InetSocketAddress(getServerAddress(server), port);
   }

   @Override
   public InetAddress getServerAddress(int server) {
      GenericContainer container = containers.get(server);
      // We talk directly to the container, and not through forwarded addresses on localhost because of
      // https://github.com/testcontainers/testcontainers-java/issues/452
      return Exceptions.unchecked(() -> InetAddress.getByName(getIpAddressFromContainer(container)));
   }

   @Override
   public void pause(int server) {
      DockerClientFactory.instance().client().pauseContainerCmd(containers.get(server).getContainerId()).exec();
      System.out.printf("[%d] PAUSE \n", server);
   }

   @Override
   public void resume(int server) {
      DockerClientFactory.instance().client().unpauseContainerCmd(containers.get(server).getContainerId()).exec();
      System.out.printf("[%d] RESUME \n", server);
   }

   @Override
   public void stop(int server) {
      containers.get(server).stop();
      System.out.printf("[%d] STOP \n", server);
   }

   @Override
   public void kill(int server) {
      DockerClientFactory.instance().client().killContainerCmd(containers.get(server).getContainerId()).exec();
      System.out.printf("[%d] KILL \n", server);
   }

   @Override
   public void restart(int server) {
      if (isRunning(server)) {
         throw new IllegalStateException("Server " + server + " is still running");
      }
      latch = new CountdownLatchLoggingConsumer(1, STARTUP_MESSAGE_REGEX);
      restartContainer(containers.get(server), server);
      Exceptions.unchecked(() -> latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
   }

   @Override
   public void restartCluster() {
      latch = new CountdownLatchLoggingConsumer(configuration.numServers(), STARTUP_MESSAGE_REGEX);
      for (int i = 0; i < configuration.numServers(); i++) {
         restartContainer(containers.get(i), i);
      }
      Exceptions.unchecked(() -> latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
   }

   @Override
   public MBeanServerConnection getJmxConnection(int server) {
      return Exceptions.unchecked(() -> {
         GenericContainer container = containers.get(server);
         ContainerNetwork network = container.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next();
         JMXServiceURL url = new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi", network.getIpAddress(), JMX_PORT));
         JMXConnector jmxConnector = JMXConnectorFactory.connect(url);
         return jmxConnector.getMBeanServerConnection();
      });
   }

   @Override
   public String getLog(int server) {
      GenericContainer container = containers.get(server);
      return container.getLogs();
   }

   @Override
   public RemoteCacheManager createRemoteCacheManager(ConfigurationBuilder builder) {
      if (preferContainerExposedPorts) {
         return new ContainerRemoteCacheManager(containers).wrap(builder);
      } else {
         return new RemoteCacheManager(builder.build());
      }
   }

   @Override
   public int getTimeout() {
      return TIMEOUT_SECONDS;
   }

   private void restartContainer(GenericContainer container, int server) {
      log.infof("Restarting container %s-%d", name, server);
      // We can stop the server by doing a rest call. TestContainers doesn't support start a container with a running state
      container.stop();
      // append the log consumer again
      withLogConsumer(container, server);
      // start it back
      container.start();
   }

   private String serverPathFrom(String path) {
      return String.format("%s/server/%s", INFINISPAN_SERVER_HOME, path);
   }

   private void withLogConsumer(GenericContainer container, int i) {
      container
            .withLogConsumer(new JBossLoggingConsumer(LogFactory.getLogger(name)).withPrefix(Integer.toString(i)))
            .withLogConsumer(latch)
            .waitingFor(Wait.forListeningPort());
   }
}
