```bash
mvn clean install -pl server/testdriver/core -DskipTests
mvn clean install -s maven-settings.xml -pl server/tests -DskipTests
```

```bash
mvn verify -s maven-settings.xml -pl server/tests -Dit.test="AuthorizationLDAPIT,AuthenticationImplicitIT,AuthenticationLDAPIT,AuthenticationMultiEndpointIT"
```
```
[INFO] Results:
[INFO] 
[INFO] Tests run: 202, Failures: 0, Errors: 0, Skipped: 0
```

```bash
mvn verify -s maven-settings.xml -pl server/tests -Dit.test="AuthorizationLDAPIT,AuthenticationImplicitIT,AuthenticationLDAPIT,AuthenticationMultiEndpointIT" -Dorg.infinispan.test.ldapServer=empty -fae
```
```
[ERROR] Tests run: 202, Failures: 14, Errors: 29, Skipped: 0
```