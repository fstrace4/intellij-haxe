package com.intellij.plugins.haxe.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.plugins.haxe.lang.psi.HaxeComponentName;
import com.intellij.plugins.haxe.lang.psi.HaxeGenericParam;
import com.intellij.plugins.haxe.lang.psi.HaxeObjectLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Fedor.Korotkov
 */
public abstract class HaxeObjectLiteralTypeImpl extends AbstractHaxePsiClass {

  public HaxeObjectLiteralTypeImpl(@NotNull HaxeObjectLiteral objectLiteral) {
    super(objectLiteral.getNode());
  }

  public HaxeObjectLiteralTypeImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable HaxeComponentName getComponentName() {
    return null;
  }

  @Override
  public @Nullable HaxeGenericParam getGenericParam() {
    return null;
  }
}
