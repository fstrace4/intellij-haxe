package com.intellij.plugins.haxe.model;

import com.intellij.plugins.haxe.lang.psi.HaxeEnumValueDeclaration;
import com.intellij.plugins.haxe.model.type.HaxeGenericResolver;
import com.intellij.plugins.haxe.model.type.ResultHolder;
import org.jetbrains.annotations.Nullable;

public interface HaxeEnumValueModel extends HaxeModel {

  ResultHolder getResultType(@Nullable HaxeGenericResolver resolver);

  HaxeClassModel getDeclaringEnum();

  @Nullable
  HaxeEnumValueDeclaration getEnumValuePsi();
}
