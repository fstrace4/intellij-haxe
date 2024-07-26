package com.intellij.plugins.haxe.lang.psi;

import com.intellij.lang.ASTNode;
import com.intellij.plugins.haxe.lang.psi.impl.HaxePsiCompositeElementImpl;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class HaxeEnumBodyMixinImpl extends HaxePsiCompositeElementImpl {
  public HaxeEnumBodyMixinImpl(@NotNull ASTNode node) {
    super(node);
  }

  @NotNull
  public List<HaxeEnumValueDeclaration> getEnumValueDeclarationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HaxeEnumValueDeclaration.class);
  }
}
