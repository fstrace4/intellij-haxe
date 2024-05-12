package com.intellij.plugins.haxe.ide.index;

import com.intellij.plugins.haxe.HaxeComponentType;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class HaxeConstructorInfoExternalizer implements DataExternalizer<HaxeConstructorInfo> {

  private final ThreadLocal<byte[]> buffer = ThreadLocal.withInitial(IOUtil::allocReadWriteUTFBuffer);

  @Override
  public void save(@NotNull DataOutput out, HaxeConstructorInfo constructorInfo) throws IOException {
    IOUtil.writeUTFFast(buffer.get(), out, constructorInfo.getClassName());
    IOUtil.writeUTFFast(buffer.get(), out, constructorInfo.getPackageName());
    out.writeBoolean(constructorInfo.hasParameters());
    out.writeInt(constructorInfo.getType().getKey());
  }

  @Override
  public HaxeConstructorInfo read(@NotNull DataInput in) throws IOException {
    final String className = IOUtil.readUTFFast(buffer.get(), in);
    final String packageName = IOUtil.readUTFFast(buffer.get(), in);
    final boolean hasParameters = in.readBoolean();
    HaxeComponentType type = HaxeComponentType.valueOf(in.readInt());
    return new HaxeConstructorInfo(className, packageName, hasParameters, type );
  }
}
