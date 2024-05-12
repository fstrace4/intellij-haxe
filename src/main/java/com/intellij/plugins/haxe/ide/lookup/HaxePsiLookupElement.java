package com.intellij.plugins.haxe.ide.lookup;

import com.intellij.plugins.haxe.HaxeComponentType;

public interface HaxePsiLookupElement extends HaxeLookupElement{
  HaxeComponentType getType();
}
