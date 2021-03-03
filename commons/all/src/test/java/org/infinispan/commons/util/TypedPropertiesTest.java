package org.infinispan.commons.util;

import static org.junit.Assert.assertEquals;

import java.util.Properties;

import org.junit.Test;

/**
 * Test for {@link TypedProperties}.
 *
 * @author Diego Lovison
 * @since 12.0
 **/
public class TypedPropertiesTest {

   @Test
   public void testIntProperty() {
      TypedProperties p = createProperties();
      assertEquals(1, p.getIntProperty("int", 999));
      assertEquals(10, p.getIntProperty("int_put_str", 999));
      assertEquals(100, p.getIntProperty("int_property_str", 999));
   }

   @Test
   public void testLongProperty() {
      TypedProperties p = createProperties();
      assertEquals(2L, p.getLongProperty("long", 999L));
      assertEquals(20L, p.getLongProperty("long_put_str", 999L));
      assertEquals(200L, p.getLongProperty("long_property_str", 999L));
   }

   @Test
   public void testBooleanProperty() {
      TypedProperties p = createProperties();
      assertEquals(true, p.getBooleanProperty("boolean", false));
      assertEquals(true, p.getBooleanProperty("boolean_put_str", false));
      assertEquals(true, p.getBooleanProperty("boolean_property_str", false));
   }

   @Test
   public void testEnumProperty() {
      TypedProperties p = createProperties();
      assertEquals(COLOR.RED, p.getEnumProperty("enum", COLOR.class, COLOR.BLUE));
      assertEquals(COLOR.RED, p.getEnumProperty("enum_put_str", COLOR.class, COLOR.BLUE));
      assertEquals(COLOR.RED, p.getEnumProperty("enum_property_str", COLOR.class, COLOR.BLUE));
   }

   private enum COLOR {
      RED, BLUE
   }

   private TypedProperties createProperties() {
      Properties p = new Properties();
      p.put("int", 1);
      p.put("int_put_str", Integer.toString(10));
      p.setProperty("int_property_str", Integer.toString(100));

      p.put("long", 2L);
      p.put("long_put_str", Long.toString(20L));
      p.setProperty("long_property_str", Long.toString(200L));

      p.put("boolean", true);
      p.put("boolean_put_str", Boolean.toString(true));
      p.setProperty("boolean_property_str", Boolean.toString(true));

      p.put("enum", COLOR.RED);
      p.put("enum_put_str", COLOR.RED.toString());
      p.setProperty("enum_property_str", COLOR.RED.toString());

      return new TypedProperties(p);
   }
}
