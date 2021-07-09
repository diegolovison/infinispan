package org.infinispan.server.test.core.ldap;

import java.io.IOException;

import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;
import org.testcontainers.containers.GenericContainer;

public class RHDS10 extends AbstractLdapServer {

   GenericContainer ldapServer;

   @Override
   public void start(String keystoreFile, String initLDIF) throws Exception {
      ldapServer = new GenericContainer("registry/package/product:version");
      ldapServer.start();
   }

   @Override
   public void stop() throws Exception {
      ldapServer.stop();
   }

   @Override
   public void startKdc() throws IOException, LdapInvalidDnException {
      throw new UnsupportedOperationException();

   }
}
