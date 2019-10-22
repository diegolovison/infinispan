package org.infinispan.marshall.protostream.impl;

import static org.infinispan.marshall.protostream.impl.SerializationContextRegistry.MarshallerType.GLOBAL;
import static org.infinispan.marshall.protostream.impl.SerializationContextRegistry.MarshallerType.PERSISTENCE;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.scopes.Scope;
import org.infinispan.factories.scopes.Scopes;
import org.infinispan.marshall.persistence.impl.PersistenceContextInitializerImpl;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.FileDescriptorSource;
import org.infinispan.protostream.ProtobufUtil;
import org.infinispan.protostream.SerializationContext;
import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.config.Configuration;

@Scope(Scopes.GLOBAL)
public class SerializationContextRegistryImpl implements SerializationContextRegistry {

   @Inject GlobalConfiguration globalConfig;

   private final MarshallerContext global = new MarshallerContext();
   private final MarshallerContext persistence = new MarshallerContext();

   @Start
   public void start() {
      // Add user configured SCIs to both the global and persistence context
      List<SerializationContextInitializer> scis = globalConfig.serialization().contextInitializers();
      update(GLOBAL, ctx -> {
         if (scis != null)
            ctx.addContextIntializers(scis);

         ctx.addContextIntializer(new PersistenceContextInitializerImpl())
               // Register Commons util so that KeyValueWithPrevious can be used with JCache remote
               .addContextIntializer(new org.infinispan.commons.GlobalContextInitializerImpl())
               .update();
      });

      update(PERSISTENCE, ctx -> {
         if (scis != null)
            ctx.addContextIntializers(scis);

         ctx.addContextIntializer(new PersistenceContextInitializerImpl())
               .update();
      });
   }

   @Override
   public SerializationContext getGlobalCtx() {
      synchronized (global) {
         return global.ctx;
      }
   }

   @Override
   public SerializationContext getPersistenceCtx() {
      synchronized (persistence) {
         return persistence.ctx;
      }
   }

   @Override
   public void addContextInitializer(MarshallerType type, SerializationContextInitializer sci) {
      update(type, ctx -> ctx.addContextIntializer(sci).update());
   }

   @Override
   public void addProtoFile(MarshallerType type, FileDescriptorSource fileDescriptorSource) {
      update(type, ctx -> ctx.addProtoFile(fileDescriptorSource).update());
   }

   @Override
   public void addMarshaller(MarshallerType type, BaseMarshaller marshaller) {
      update(type, ctx -> ctx.addMarshaller(marshaller).update());
   }

   @Override
   public void addConfiguration(MarshallerType type, Configuration config) {
      update(type, ctx -> ctx.addConfiguration(config).update());
   }

   private void update(MarshallerType type, Consumer<MarshallerContext> consumer) {
      if (type == GLOBAL) {
         synchronized (global) {
            consumer.accept(global);
         }
      } else {
         synchronized (persistence) {
            consumer.accept(persistence);
         }
      }
   }

   static class MarshallerContext {
      private final List<SerializationContextInitializer> initializers = new ArrayList<>();
      private final List<FileDescriptorSource> schemas = new ArrayList<>();
      private final List<BaseMarshaller<?>> marshallers = new ArrayList<>();
      private SerializationContext ctx = ProtobufUtil.newSerializationContext();

      MarshallerContext addContextIntializers(List<SerializationContextInitializer> scis) {
         initializers.addAll(scis);
         return this;
      }

      MarshallerContext addContextIntializer(SerializationContextInitializer sci) {
         initializers.add(sci);
         return this;
      }

      MarshallerContext addProtoFile(FileDescriptorSource fileDescriptorSource) {
         schemas.add(fileDescriptorSource);
         return this;
      }

      MarshallerContext addMarshaller(BaseMarshaller marshaller) {
         marshallers.add(marshaller);
         return this;
      }

      MarshallerContext addConfiguration(Configuration config) {
         ctx = ProtobufUtil.newSerializationContext(config);
         return this;
      }

      void update() {
         initializers.forEach(sci -> {
            sci.registerSchema(ctx);
            sci.registerMarshallers(ctx);
         });
         schemas.forEach(ctx::registerProtoFiles);
         marshallers.forEach(ctx::registerMarshaller);
      }
   }
}