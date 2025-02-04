/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2015 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2019-2020 Eric Bishton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.plugins.haxe.model.evaluator;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.plugins.haxe.ide.highlight.HaxeSyntaxHighlighterColors;
import com.intellij.plugins.haxe.model.HaxeDocumentModel;
import com.intellij.plugins.haxe.model.fixer.HaxeFixer;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class HaxeExpressionEvaluatorContext {
  public ResultHolder result;
  private List<ResultHolder> returns = new ArrayList<ResultHolder>();
  private List<PsiElement> returnElements = new ArrayList<PsiElement>();
  private List<HaxeExpressionEvaluatorReturnInfo> returnInfos = new ArrayList<HaxeExpressionEvaluatorReturnInfo>();

  public AnnotationHolder holder;
  private HaxeScope<ResultHolder> scope = new HaxeScope<ResultHolder>();
  public final PsiElement root;

  public HaxeExpressionEvaluatorContext(@NotNull PsiElement body) {
    this.root = body;
    this.holder = null;
    beginScope();
  }
  public HaxeExpressionEvaluatorContext(@NotNull PsiElement body, @Nullable AnnotationHolder holder) {
    this.root = body;
    this.holder = holder;
    beginScope();
  }

  public HaxeExpressionEvaluatorContext createChild(PsiElement body) {
    HaxeExpressionEvaluatorContext that = new HaxeExpressionEvaluatorContext(body, this.holder);
    that.scope = this.scope;
    that.beginScope();
    return that;
  }

  public void addReturnType(ResultHolder type, PsiElement element) {
    this.returns.add(type);
    this.returnElements.add(element);
    this.returnInfos.add(new HaxeExpressionEvaluatorReturnInfo(element, type));
  }

  public ResultHolder getReturnType() {
    if (returns.isEmpty()) return SpecificHaxeClassReference.getVoid(root).createHolder();
    return HaxeTypeUnifier.unify(ResultHolder.types(returns), root, UnificationRules.DEFAULT).createHolder();
  }

  public List<ResultHolder> getReturnValues() {
    return returns;
  }

  public List<HaxeExpressionEvaluatorReturnInfo> getReturnInfos() {
    return returnInfos;
  }

  public List<PsiElement> getReturnElements() {
    return returnElements;
  }

  public HaxeDocumentModel getDocument() {
    return HaxeDocumentModel.fromElement(root);
  }

  public HaxeScope beginScope() {
    scope = new HaxeScope<ResultHolder>(scope);
    return scope;
  }

  public void endScope() {
    scope = scope.parent;
  }

  public void setLocal(String key, ResultHolder value) {
    this.scope.set(key, value);
  }

  public void setLocalWhereDefined(String key, ResultHolder value) {
    this.scope.setWhereDefined(key, value);
  }

  public boolean has(String key) {
    return this.scope.has(key);
  }

  public ResultHolder get(String key) {
    return this.scope.get(key);
  }


  @NotNull
  public void addError(PsiElement element, String error, HaxeFixer... fixers) {
    if (holder == null) return;
    AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, error).range(element);
    for (HaxeFixer fixer : fixers) {
      builder.withFix(fixer);
    }
    builder.create();
  }

  @NotNull
  public void addWarning(PsiElement element, String error, HaxeFixer... fixers) {
    if (holder == null) return ;
    AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.WARNING, error).range(element);
    for (HaxeFixer fixer : fixers) {
      builder.withFix(fixer);
    }
    builder.create();
  }

  public boolean isReportingErrors() {
    return holder != null;
  }



  @NotNull
  public void addUnreachable(PsiElement element) {
    if (holder == null) return ;
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .textAttributes(HaxeSyntaxHighlighterColors.LINE_COMMENT)
      .range(element)
      .create();

  }

  final public List<HaxeExpressionEvaluatorContext> lambdas = new LinkedList<HaxeExpressionEvaluatorContext>();
  public void addLambda(HaxeExpressionEvaluatorContext child) {
    lambdas.add(child);
  }

  public HaxeScope<ResultHolder> getScope() {
    return scope;
  }
}
