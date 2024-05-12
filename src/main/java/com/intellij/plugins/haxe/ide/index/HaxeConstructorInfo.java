package com.intellij.plugins.haxe.ide.index;

import com.intellij.plugins.haxe.HaxeComponentType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


@EqualsAndHashCode
public class HaxeConstructorInfo {
  @Getter @NotNull private final String className;
  @Getter @NotNull private final String packageName;
  @Getter @NotNull private final HaxeComponentType type;
  private final boolean hasParameters;


  public HaxeConstructorInfo(@NotNull String className, @NotNull String packageName, boolean parameters, HaxeComponentType type) {
    this.className = className;
    this.packageName = packageName;
    this.hasParameters = parameters;
    this.type = type != null ? type : HaxeComponentType.METHOD;
  }


  @Nullable
  public Icon getIcon() {
    return type.getIcon();
  }

  public boolean hasParameters() {
    return hasParameters;
  }
}
