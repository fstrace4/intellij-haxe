package com.intellij.plugins.haxe.frameworks.openfl;

import com.intellij.openapi.util.TextRange;
import com.intellij.plugins.haxe.lang.psi.HaxeCompiletimeMetaArg;
import com.intellij.plugins.haxe.lang.psi.impl.HaxeStringLiteralImpl;
import com.intellij.plugins.haxe.metadata.psi.HaxeMetadataCompileTimeMeta;
import com.intellij.plugins.haxe.metadata.psi.HaxeMetadataType;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import glsl.plugin.language.GlslLanguage;
import org.jetbrains.annotations.NotNull;


public class OpenFLShaderLanguageInjector implements LanguageInjector {

  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (host instanceof HaxeStringLiteralImpl literal) {
      if (literal.getParent() instanceof HaxeCompiletimeMetaArg compiletime) {
        HaxeMetadataCompileTimeMeta type = PsiTreeUtil.getParentOfType(compiletime, HaxeMetadataCompileTimeMeta.class);
        if (type != null) {
          HaxeMetadataType metaType = type.getType();
          if (metaType != null) {
            String name = metaType.getText();
            TextRange parentRange = literal.getTextRange();
            TextRange range = new TextRange(1, parentRange.getLength() - 1);
            switch (name) {
              case "glFragmentBody":
              case "glVertexBody":
                injectionPlacesRegistrar.addPlace(GlslLanguage.Companion.getINSTANCE(), range, "void main() {", "}");
                break;
              case "glFragmentHeader":
              case "glVertexHeader":
              case "glFragmentSource":
              case "glVertexSource":
                injectionPlacesRegistrar.addPlace(GlslLanguage.Companion.getINSTANCE(), range, null, null);
                break;
            }
          }
        }
      }
    }
  }
}
