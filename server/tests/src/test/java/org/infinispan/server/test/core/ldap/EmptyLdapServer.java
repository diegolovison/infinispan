package org.infinispan.server.test.core.ldap;

import java.io.IOException;

import org.apache.directory.api.ldap.model.exception.LdapInvalidDnException;

public class EmptyLdapServer extends AbstractLdapServer {

   @Override
   public void start(String keystoreFile, String initLDIF) throws Exception {

   }

   @Override
   public void stop() throws Exception {

   }

   @Override
   public void startKdc() throws IOException, LdapInvalidDnException {

   }
}
