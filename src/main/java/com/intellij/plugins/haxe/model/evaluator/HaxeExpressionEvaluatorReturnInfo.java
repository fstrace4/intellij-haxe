package com.intellij.plugins.haxe.model.evaluator;

import com.intellij.plugins.haxe.model.type.ResultHolder;
import com.intellij.psi.PsiElement;

public record HaxeExpressionEvaluatorReturnInfo(PsiElement element, ResultHolder type) {
}