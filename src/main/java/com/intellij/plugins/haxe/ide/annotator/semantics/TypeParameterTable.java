package com.intellij.plugins.haxe.ide.annotator.semantics;

import com.intellij.plugins.haxe.model.type.ResultHolder;
import com.intellij.plugins.haxe.model.type.resolver.ResolveSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class TypeParameterTable {

  private record typeAndSource(ResultHolder type, ResolveSource resolveSource) {}

  Map<String, typeAndSource> data = new HashMap<>();

  public void put(@NotNull String name, ResultHolder holder, ResolveSource scope) {
    data.put(name, new typeAndSource(holder, scope));
  }

  public boolean contains(String name) {
    return data.containsKey(name);
  }
  public boolean contains(@NotNull String name, @NotNull ResolveSource scope) {
    return data.entrySet().stream().anyMatch(entry -> entry.getKey().equals(name) && entry.getValue().resolveSource().equals(scope));
  }

  @Nullable
  public ResultHolder get(@NotNull String name) {
    if(!contains(name)) return null;
    return data.get(name).type();
  }
  @Nullable
  public ResultHolder get(@NotNull String name, ResolveSource scope) {
    return data.entrySet().stream().filter(entry -> entry.getKey().equals(name) && entry.getValue().resolveSource().equals(scope))
      .findFirst()
      .map(entry -> entry.getValue().type)
      .orElse(null);
  }
}
