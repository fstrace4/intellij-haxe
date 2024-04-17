package com.intellij.plugins.haxe.ide.hint.types;

import com.intellij.codeInsight.hints.declarative.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.plugins.haxe.lang.psi.HaxeEnumArgumentExtractor;
import com.intellij.plugins.haxe.lang.psi.HaxeEnumExtractedValue;
import com.intellij.plugins.haxe.lang.psi.HaxeEnumValueDeclaration;
import com.intellij.plugins.haxe.lang.psi.HaxeParameterList;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HaxeInlayEnumExtractorHintsProvider implements InlayHintsProvider {

  @Nullable
  @Override
  public InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
    return new TypeCollector();
  }

  private static class TypeCollector extends HaxeSharedBypassCollector {

    @Override
    public void collectFromElement(@NotNull PsiElement element, @NotNull InlayTreeSink sink) {
      if (element instanceof HaxeEnumArgumentExtractor extractor) {
        handleEnumArgumentExtractorHints(sink, extractor);
      }
    }

    private static void handleEnumArgumentExtractorHints(@NotNull InlayTreeSink sink, HaxeEnumArgumentExtractor extractor) {
      PsiElement resolve = extractor.getEnumValueReference().getReferenceExpression().resolve();
      if (resolve instanceof HaxeEnumValueDeclaration enumValueDeclaration) {


        List<HaxeEnumExtractedValue> extractedValueList = extractor.getEnumExtractorArgumentList().getEnumExtractedValueList();

        HaxeParameterList parameterList = enumValueDeclaration.getParameterList();
        if (parameterList != null) {
          @NotNull PsiElement[] children = extractor.getEnumExtractorArgumentList().getChildren();
          for (int i = 0; i < children.length; i++) {
            if (children[i] instanceof HaxeEnumExtractedValue enumExtractedValue) {
              int offset = enumExtractedValue.getTextRange().getEndOffset();
              if (extractedValueList.size() > i) {
                InlineInlayPosition position = new InlineInlayPosition(offset, true, 0);
                ResultHolder type = HaxeExpressionEvaluator.evaluate(extractedValueList.get(i), null).result;
                sink.addPresentation(position, null, null, false, appendTypeTextToBuilder(type));
              }
            }
          }
        }
      }
    }
  }
}
