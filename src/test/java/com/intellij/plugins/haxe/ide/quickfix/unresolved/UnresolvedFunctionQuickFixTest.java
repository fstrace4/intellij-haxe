package com.intellij.plugins.haxe.ide.quickfix.unresolved;

import com.intellij.plugins.haxe.ide.quickfix.HaxeQuickFixTestBase;
import org.jetbrains.annotations.NotNull;

public class UnresolvedFunctionQuickFixTest extends HaxeQuickFixTestBase {



  @Override
  protected @NotNull String getBasePath() {
    return "/unresolved/function";
  }


  public void testCreateFunctionAssign() {
    doSingleTest("_create_function_assign.hx");
  }



}
