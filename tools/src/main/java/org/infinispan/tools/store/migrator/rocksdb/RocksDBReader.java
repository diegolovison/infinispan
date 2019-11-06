package org.infinispan.tools.store.migrator.rocksdb;

import java.io.File;
import java.util.Iterator;

import org.infinispan.commons.CacheException;
import org.infinispan.commons.marshall.Marshaller;
import org.infinispan.persistence.spi.MarshallableEntry;
import org.infinispan.persistence.spi.PersistenceException;
import org.infinispan.tools.store.migrator.StoreIterator;
import org.infinispan.tools.store.migrator.StoreProperties;
import org.infinispan.tools.store.migrator.marshaller.SerializationConfigUtil;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.Cache;
import org.rocksdb.CompressionType;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import static org.infinispan.tools.store.migrator.Element.*;

public class RocksDBReader implements StoreIterator {

   private final RocksDB db;
   private final Marshaller marshaller;

   public RocksDBReader(StoreProperties props) {
      props.required(LOCATION);
      String location = props.get(LOCATION) + props.get(CACHE_NAME).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
      File f = new File(location);
      if (!f.exists() || !f.isDirectory())
         throw new CacheException(String.format("Unable to read db directory '%s'", location));

      long capacity = 1000;
      Cache cache = new LRUCache(capacity);
      BlockBasedTableConfig tableOptions = new BlockBasedTableConfig();
      tableOptions.setBlockCache(cache);
      Options options = new Options().setCreateIfMissing(false);
      options.setTableFormatConfig(tableOptions);
      String compressionType = props.get(COMPRESSION);
      if (compressionType != null) {
         options.setCompressionType(CompressionType.getCompressionType(compressionType));
      }

      try {
         this.db = RocksDB.openReadOnly(options, location);
      } catch (RocksDBException e) {
         throw new CacheException(e);
      }
      this.marshaller = SerializationConfigUtil.getMarshaller(props);
   }

   @Override
   public void close() {
      db.close();
   }

   @Override
   public Iterator<MarshallableEntry> iterator() {
      return new RocksDBIterator();
   }

   class RocksDBIterator implements Iterator<MarshallableEntry>, AutoCloseable {

      final RocksIterator it;

      private RocksDBIterator() {
         this.it = db.newIterator(new ReadOptions().setFillCache(false));
         it.seekToFirst();
      }

      @Override
      public void close() {
         it.close();
      }

      @Override
      public boolean hasNext() {
         return it.isValid();
      }

      @Override
      public MarshallableEntry next() {
         MarshallableEntry entry = unmarshall(it.value());
         it.next();
         return entry;
      }

      @SuppressWarnings(value = "unchecked")
      private <T> T unmarshall(byte[] bytes) {
         try {
            return (T) marshaller.objectFromByteBuffer(bytes);
         } catch (Exception e) {
            throw new PersistenceException(e);
         }
      }
   }
}
