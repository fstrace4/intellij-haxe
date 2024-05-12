package com.intellij.plugins.haxe.ide.index;

import com.intellij.plugins.haxe.HaxeComponentType;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


public class HaxeStaticMemberInfoExternalizer implements DataExternalizer<HaxeStaticMemberInfo> {

  private final ThreadLocal<byte[]> buffer = ThreadLocal.withInitial(IOUtil::allocReadWriteUTFBuffer);

  @Override
  public void save(@NotNull DataOutput out, HaxeStaticMemberInfo memberInfo) throws IOException {
    IOUtil.writeUTFFast(buffer.get(), out, memberInfo.getOwnerPackage());
    IOUtil.writeUTFFast(buffer.get(), out, memberInfo.getOwnerName());
    IOUtil.writeUTFFast(buffer.get(), out, memberInfo.getMemberName());
    IOUtil.writeUTFFast(buffer.get(), out, memberInfo.getTypeValue());
    final HaxeComponentType haxeComponentType = memberInfo.getType();
    out.writeInt( haxeComponentType.getKey());
  }

  @Override
  public HaxeStaticMemberInfo read(@NotNull DataInput in) throws IOException {
    final String ownerPackage = IOUtil.readUTFFast(buffer.get(), in);
    final String ownerName = IOUtil.readUTFFast(buffer.get(), in);
    final String memberName = IOUtil.readUTFFast(buffer.get(), in);
    final String typeValue = IOUtil.readUTFFast(buffer.get(), in);

    HaxeComponentType type = HaxeComponentType.valueOf(in.readInt());
    if (type == null) type = HaxeComponentType.FIELD;
    return new HaxeStaticMemberInfo(ownerPackage, ownerName, memberName, type, typeValue);
  }
}
