package org.infinispan.tx;

import static org.infinispan.context.Flag.FORCE_WRITE_LOCK;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.transaction.TransactionManager;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.EmbeddedTransactionManagerLookup;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "tx.MultipleThreadTest")
public class MultipleThreadTest {

   // ISPN-10029
   public void testMultipleThread() throws InterruptedException, ExecutionException {

      ExecutorService executor = Executors.newFixedThreadPool(5);

      String key = "k";
      String value = "1";
      String newValue = "2";
      String cacheName = "myCache";

      EmbeddedCacheManager cacheManagerX = createCacheManager(cacheName);
      Cache<String, String> cacheX = cacheManagerX.getCache(cacheName);
      cacheX.put(key, value);
      assertEquals(cacheX.getCacheConfiguration().transaction().transactionMode(), TransactionMode.TRANSACTIONAL);
      TransactionManager txX = cacheX.getAdvancedCache().getTransactionManager();

      EmbeddedCacheManager cacheManagerY = createCacheManager(cacheName);
      Cache<String, String> cacheY = cacheManagerY.getCache(cacheName);
      assertEquals(cacheY.get(key), value);
      TransactionManager txY = cacheY.getAdvancedCache().getTransactionManager();

      assertEquals(2, cacheX.getCacheManager().getMembers().size());
      assertEquals(2, cacheY.getCacheManager().getMembers().size());
      assertEquals(value, cacheX.get(key));
      assertEquals(value, cacheY.get(key));

      AtomicInteger i = new AtomicInteger();

      // task1
      Future<Void> task1 = executor.submit(() -> {
         txX.begin();
         String vX = cacheX.getAdvancedCache().withFlags(FORCE_WRITE_LOCK).get(key);
         assertEquals(value, vX);
         cacheX.put(key, newValue);

         // task2
         Future<Void> task2 = executor.submit(() -> {
            txY.begin();
            assertEquals(0, i.getAndIncrement());
            String vY = cacheY.getAdvancedCache().withFlags(FORCE_WRITE_LOCK).get(key);
            assertEquals(2, i.getAndIncrement());
            assertEquals(newValue, vY);
            txY.commit();
            return null;
         });

         long now = System.currentTimeMillis();
         while (System.currentTimeMillis() - now < 1000) {}

         txX.commit();
         assertEquals(1, i.getAndIncrement());

         task2.get();

         return null;
      });

      task1.get();

      cacheManagerX.stop();
      cacheManagerY.stop();

      executor.shutdown();
   }

   private EmbeddedCacheManager createCacheManager(String cacheName) {
      GlobalConfiguration globalConfig = new GlobalConfigurationBuilder().transport()
            .defaultTransport()
            .clusterName("qa-cluster")
            .build();
      EmbeddedCacheManager cacheManager = new DefaultCacheManager(globalConfig);
      Configuration cacheConfig = new ConfigurationBuilder()
            .clustering().cacheMode(CacheMode.DIST_SYNC)
            .transaction().transactionMode(TransactionMode.TRANSACTIONAL).transactionManagerLookup(new EmbeddedTransactionManagerLookup())
            .lockingMode(LockingMode.PESSIMISTIC)
            .build();
      cacheManager.createCache(cacheName, cacheConfig);
      return cacheManager;
   }
}
