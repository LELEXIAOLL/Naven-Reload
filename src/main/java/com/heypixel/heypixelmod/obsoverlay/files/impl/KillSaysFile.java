package com.heypixel.heypixelmod.obsoverlay.files.impl;

import com.heypixel.heypixelmod.obsoverlay.Naven;
import com.heypixel.heypixelmod.obsoverlay.files.ClientFile;
import com.heypixel.heypixelmod.obsoverlay.modules.impl.misc.KillSay;
import com.heypixel.heypixelmod.obsoverlay.values.ValueBuilder;
import com.heypixel.heypixelmod.obsoverlay.values.impl.BooleanValue;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;

public class KillSaysFile extends ClientFile {
   private static final String[] styles = new String[]{
      "%s Love",
      "%s 兄弟你好香",
      "卧龙出山, %s 你已被 Naven-Reload 客户端击毙",
      "雷霆万钧, %s 你已被 Naven-Reload 客户端击毙",
      "蛟龙出海, %s 你已被 Naven-Reload 客户端击毙",
      "利刃出鞘, %s 你已被 Naven-Reload 客户端击毙",
      "锐箭穿云, %s 你已被 Naven-Reload 客户端击毙",
      "潜龙破渊, %s 你已被 Naven-Reload 客户端击毙"
   };

   public KillSaysFile() {
      super("killsay.cfg");
   }

   @Override
   public void read(BufferedReader reader) throws IOException {
      KillSay module = (KillSay)Naven.getInstance().getModuleManager().getModule(KillSay.class);
      List<BooleanValue> values = module.getValues();

      String line;
      while ((line = reader.readLine()) != null) {
         values.add(ValueBuilder.create(module, line).setDefaultBooleanValue(false).build().getBooleanValue());
      }

      if (values.isEmpty()) {
         for (String style : styles) {
            values.add(ValueBuilder.create(module, style).setDefaultBooleanValue(false).build().getBooleanValue());
         }
      }
   }

   @Override
   public void save(BufferedWriter writer) throws IOException {
      KillSay module = (KillSay)Naven.getInstance().getModuleManager().getModule(KillSay.class);

      for (BooleanValue value : module.getValues()) {
         writer.write(value.getName() + "\n");
      }
   }
}
