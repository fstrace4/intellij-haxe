package com.intellij.plugins.haxe.ide.hint.types;

import com.intellij.codeInsight.hints.declarative.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class HaxeInlayLocalVariableHintsProvider implements InlayHintsProvider {

  @Nullable
  @Override
  public InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
    return new TypeCollector();
  }

  private static class TypeCollector implements SharedBypassCollector {

    @Override
    public void collectFromElement(@NotNull PsiElement element, @NotNull InlayTreeSink sink) {
      if (element instanceof HaxeLocalVarDeclaration varDeclaration) {
        handleLocalVarDeclarationHints(sink, varDeclaration);
      }
    }


    private static void handleLocalVarDeclarationHints(@NotNull InlayTreeSink sink, @NotNull HaxeLocalVarDeclaration varDeclaration) {
      if (varDeclaration instanceof  HaxeSwitchCaseCaptureVar) return; // handled by HaxeInlayCaptureVariableHintsProvider
      HaxeTypeTag tag = varDeclaration.getTypeTag();
      if (tag == null) {
        // attempts to resolve type from init or usage
        ResultHolder type = HaxeTypeResolver.getPsiElementType(varDeclaration, null);

        if (!type.isUnknown() && !type.getType().isInvalid()) {
          int offset = varDeclaration.getComponentName().getTextRange().getEndOffset();
          InlineInlayPosition position = new InlineInlayPosition(offset, false, 0);
          sink.addPresentation(position, null, null, false, appendTypeTextToBuilder(type));
        }
      }
    }

    @NotNull
    private static Function1<PresentationTreeBuilder, Unit> appendTypeTextToBuilder(ResultHolder type) {
      return builder -> {
        builder.text(":" + type.toPresentationString(), null);
        return null;
      };
    }
  }
}
