package com.intellij.plugins.haxe.ide.index;

import com.intellij.plugins.haxe.HaxeComponentType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@Getter
@EqualsAndHashCode
public class HaxeStaticMemberInfo {
  @NotNull private final String ownerPackage;
  @NotNull private final String ownerName;
  @NotNull private final String memberName;
  @NotNull private final String typeValue;

  @NotNull private final HaxeComponentType type;

  public HaxeStaticMemberInfo(@NotNull String ownerPackage,
                              @NotNull String ownerName,
                              @NotNull String memberName,
                              @NotNull HaxeComponentType type,
                              String typeValue) {

    this.ownerPackage = ownerPackage;
    this.ownerName = ownerName;
    this.memberName = memberName;
    this.type = type;
    this.typeValue = typeValue != null ? typeValue : "";
  }


  @NotNull
  public String getOwnerName() {
    return ownerName;
  }

  @NotNull
  public HaxeComponentType getType() {
    return type;
  }

  @NotNull
  public Icon getIcon() {
    return type.getIcon();
  }

}
