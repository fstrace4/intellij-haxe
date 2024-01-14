package com.intellij.plugins.haxe.haxelib.definitions.tags;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class ProjectXmlHaxedefValue extends ProjectXmlConditionalValue {
  String name;

  public ProjectXmlHaxedefValue(@NotNull String name, @Nullable String ifCondition, @Nullable String unlessCondition) {
    super(ifCondition, unlessCondition);
    this.name = name;
  }
}
