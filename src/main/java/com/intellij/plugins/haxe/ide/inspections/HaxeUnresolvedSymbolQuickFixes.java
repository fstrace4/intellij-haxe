package com.intellij.plugins.haxe.ide.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.plugins.haxe.ide.inspections.intentions.*;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class HaxeUnresolvedSymbolQuickFixes {


  // TODO check if references is local or in some  other class, resolve parent ( v1 +v2 vs v1 +  a.b.v2)



  public static LocalQuickFix createFunctionQuickfix(HaxeCallExpression expression) {

    return new HaxeIntroduceFunctionIntention(expression);
  }

  public static LocalQuickFix createMethodQuickfix(HaxeCallExpression expression) {
    return new HaxeIntroduceMethodIntention(expression);
  }


  public static LocalQuickFix createLocalVarQuickfix(HaxeReferenceExpression expression) {
    // do not suggest local var if reference is a chained expression
    if(expression.getParent() instanceof HaxeReferenceExpression) return null;
    if(expression.textContains('.')) return null;
    // prevent in init expressions that are not inside code blocks
    if (PsiTreeUtil.getParentOfType(expression, HaxeFieldDeclaration.class) != null) return null;
    if (PsiTreeUtil.getParentOfType(expression, HaxePropertyDeclaration.class) != null) return null;

    return new HaxeIntroduceVariableIntention(expression);
  }

  public static LocalQuickFix createFieldQuickfix(HaxeReferenceExpression expression) {
    return new HaxeIntroduceFieldIntention(expression);

  }


  public static  LocalQuickFix createMethodParameterQuickfix(HaxeReferenceExpression reference) {
    // prevent if not in Method declaration
    HaxeMethodDeclaration declaration = PsiTreeUtil.getParentOfType(reference, HaxeMethodDeclaration.class);
    if(declaration == null) return null;

    return new HaxeIntroduceMethodParameterIntention(reference, declaration);
  }


}
