package org.infinispan.server.test.jmx.management;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.infinispan.arquillian.core.InfinispanResource;
import org.infinispan.arquillian.core.RemoteInfinispanServer;
import org.infinispan.arquillian.core.RunningServer;
import org.infinispan.arquillian.core.WithRunningServer;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
@WithRunningServer({@RunningServer(name = "jmx-jolokia")})
public class JmxManagementJolokiaIT {

   @InfinispanResource("jmx-jolokia")
   RemoteInfinispanServer server1;

   @Test
   public void testJolokiaVersion() throws IOException {

      HttpClient client = HttpClientBuilder.create().build();

      HttpGet get = new HttpGet("http://localhost:8778/jolokia/version");

      HttpResponse response = client.execute(get);

      assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());
   }

}
