package com.intellij.plugins.haxe.ide.annotator.semantics;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.plugins.haxe.HaxeLanguage;
import com.intellij.plugins.haxe.lang.psi.HaxeLocalVarDeclaration;
import com.intellij.plugins.haxe.model.HaxeLocalVarModel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.plugins.haxe.ide.annotator.HaxeSemanticAnnotatorInspections.INCOMPATIBLE_INITIALIZATION;

public class HaxeLocalVarAnnotator implements Annotator {

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element.getLanguage() == HaxeLanguage.INSTANCE) {
      if (element instanceof HaxeLocalVarDeclaration varDeclaration) {
        check(varDeclaration, holder);
      }
    }
  }

  public static void check(final HaxeLocalVarDeclaration var, final AnnotationHolder holder) {
    if (!INCOMPATIBLE_INITIALIZATION.isEnabled(var)) return;

    HaxeLocalVarModel local = new HaxeLocalVarModel(var);
    if (local.hasInitializer() && local.hasTypeTag()) {
      HaxeSemanticsUtil.TypeTagChecker.check(local.getBasePsi(), local.getTypeTagPsi(), local.getInitializerPsi(), false, holder);
    }
  }
}
