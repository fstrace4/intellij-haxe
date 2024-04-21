/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2015 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017-2018 Ilya Malanin
 * Copyright 2018-2020 Eric Bishton
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
package com.intellij.plugins.haxe.model.type;

import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.plugins.haxe.ide.annotator.semantics.HaxeCallExpressionUtil;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.lang.psi.impl.AbstractHaxeNamedComponent;
import com.intellij.plugins.haxe.model.*;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;


import  static com.intellij.plugins.haxe.model.type.HaxeExpressionEvaluatorHandlers.*;

@CustomLog
public class HaxeExpressionEvaluator {
  static { log.setLevel(LogLevel.INFO); }


  @NotNull
  static public HaxeExpressionEvaluatorContext evaluate(PsiElement element, HaxeGenericResolver resolver) {
    ProgressIndicatorProvider.checkCanceled();
    HaxeExpressionEvaluatorContext context = new HaxeExpressionEvaluatorContext(element);
    context.result = handle(element, context, resolver);
    return context;
  }

  // evaluation of complex expressions can in some cases result in needing the type for a psiElement multiple times
  // untyped parameters and variables can cause a lot of unnecessary computation if we have to re-evaluate them
  // in order to avoid this we put any useful results in a thread-local map that we clear once we are done with the evaluation
  record CacheRecord(ResultHolder holder, String resolverAsString){}
  private static final ThreadLocal<Map<PsiElement, CacheRecord>> resultCache = ThreadLocal.withInitial(HashMap::new);
  private static final ThreadLocal<Map<PsiElement, AtomicInteger>> resultCacheHits = ThreadLocal.withInitial(HashMap::new);
  private static final ThreadLocal<Stack<PsiElement>> processingStack = ThreadLocal.withInitial(Stack::new);


  private static RecursionGuard<PsiElement> evaluationRecursionGuard = RecursionManager.createGuard("evaluationRecursionGuard");

  @NotNull
  static public HaxeExpressionEvaluatorContext evaluate(PsiElement element, HaxeExpressionEvaluatorContext context,
                                                        HaxeGenericResolver resolver) {
    try {
      processingStack.get().push(element);
      ProgressIndicatorProvider.checkCanceled();
      context.result = handle(element, context, resolver);
      return context;
    }
    finally {
      cleanUp();
    }
  }
  @NotNull
  static public HaxeExpressionEvaluatorContext evaluateWithRecursionGuard(PsiElement element, HaxeExpressionEvaluatorContext context,
                                                                          HaxeGenericResolver resolver) {
    try {
      processingStack.get().push(element);
      ResultHolder result = handleWithRecursionGuard(element, context, resolver);
      context.result = result != null ? result : createUnknown(element);
      return context;
    }
    finally {
      cleanUp();
    }
  }

  private static void cleanUp() {
    Stack<PsiElement> processing = processingStack.get();
    processing.pop();
    if (processing.isEmpty()) {
      //if (!resultCache.get().isEmpty()) {
      //  log.info(" cached references" + resultCache.get().size());
      //}
      resultCache.set(new HashMap<>());
    }
  }



  // keep package protected, don't use this one outside code running from evaluate()
  // if used outside  it can mess up the result cache
  @NotNull
  static ResultHolder handle(final PsiElement element,
                                     final HaxeExpressionEvaluatorContext context,
                                     final HaxeGenericResolver resolver) {
    try {
      ProgressIndicatorProvider.checkCanceled();
      return _handle(element, context, resolver);
    }
    catch (NullPointerException e) {
      // Make sure that these get into the log, because the GeneralHighlightingPass swallows them.
      log.error("Error evaluating expression type for element " + element.toString(), e);
      throw e;
    }
    catch (ProcessCanceledException e) {
      // Don't log these, because they are common, but DON'T swallow them, either; it makes things unresponsive.
      throw e;
    }

    catch (Throwable t) {
      // XXX: Watch this.  If it happens a lot, then maybe we shouldn't log it unless in debug mode.
      //log.warn("Error evaluating expression type for element " + (null == element ? "<null>" : element.toString()), t);
      //throw t;
    }
    return createUnknown(element != null ? element : context.root);
  }

  @NotNull
  static private ResultHolder _handle(final PsiElement element,
                                      final HaxeExpressionEvaluatorContext context,
                                      HaxeGenericResolver optionalResolver) {
    if (element == null) {
      return createUnknown(context.root);
    }
    HaxeGenericResolver resolver = optionalResolver != null ? optionalResolver: HaxeGenericResolverUtil.generateResolverFromScopeParents(element);

    if(log.isDebugEnabled())log.debug("Handling element: " + element);

    if (element instanceof PsiCodeBlock codeBlock) {
      return handleCodeBlock(context, resolver, codeBlock);
    }

    if (element instanceof HaxeImportAlias alias) {
      return handleImportAlias(element, alias);
    }
    // attempt at reducing unnecessary if checks by grouping psi types by their parent type
    if (element instanceof PsiStatement) {

      if (element instanceof HaxeReturnStatement returnStatement) {
        return handleReturnStatement(context, resolver, returnStatement);
      }

      if (element instanceof HaxeTryStatement tryStatement) {
        return handleTryStatement(context, resolver, tryStatement);
      }

      if (element instanceof HaxeCatchStatement catchStatement) {
        return handleCatchStatement(context, resolver, catchStatement);
      }

      if (element instanceof HaxeForStatement forStatement) {
        return handleForStatement(context, resolver, forStatement);
      }

      if (element instanceof HaxeSwitchStatement switchStatement) {
        return handleSwitchStatement(context, resolver, switchStatement);
      }

      if (element instanceof HaxeWhileStatement whileStatement) {
        return handleWhileStatement(context, resolver, whileStatement);
      }
      if (element instanceof HaxeMacroStatement macroStatement) {
        return handleMacroStatement(context, macroStatement, resolver);
      }

      if (element instanceof HaxeIfStatement ifStatement) {
        //return handleIfStatement(context, resolver, ifStatement);
        return resolveWithCache(ifStatement, resolver, () -> handleIfStatement(context, resolver, ifStatement));
      }
    }

    if (element instanceof  HaxeNamedComponent) {

      // must be before  HaxeLocalVarDeclaration as HaxeSwitchCaseCaptureVar extends HaxeLocalVarDeclaration
      if (element instanceof HaxeSwitchCaseCaptureVar captureVar) {
        return handleSwitchCaseCaptureVar(resolver, captureVar);
      }

      if (element instanceof HaxeFieldDeclaration declaration) {
        return handleFieldDeclaration(context, resolver, declaration);
      }

      if (element instanceof HaxeLocalVarDeclaration varDeclaration) {
        return handleLocalVarDeclaration(context, resolver, varDeclaration);
      }

      if (element instanceof HaxeLocalFunctionDeclaration functionDeclaration) {
        return functionDeclaration.getModel().getFunctionType(resolver).createHolder();
      }

      if (element instanceof HaxeValueIterator valueIterator) {
        return handleValueIterator(context, resolver, valueIterator);
      }
      if (element instanceof HaxeIteratorkey || element instanceof HaxeIteratorValue) {
        return resolveWithCache(element, resolver, () -> findIteratorType(element));
      }

      if (element instanceof HaxeEnumExtractedValue extractedValue) {
        return resolveWithCache(extractedValue, resolver, () -> handleEnumExtractedValue(extractedValue));
      }

      //NOTE: must be before HaxeParameter as HaxeRestParameter extends HaxeParameter
      if (element instanceof HaxeRestParameter restParameter) {
        return handleRestParameter(restParameter);
      }

      if (element instanceof HaxeParameter parameter) {
        boolean isUntyped = parameter.getTypeTag() == null && parameter.getVarInit() == null;
        if (isUntyped) {
          return resolveWithCache(element, resolver, () -> handleParameter(context, resolver, parameter));
        }
        return handleParameter(context, resolver, parameter);
      }

    }

    if (element instanceof HaxeReference) {

      if (element instanceof HaxeNewExpression expression) {
        return handleNewExpression(context, resolver, expression);
      }

      if (element instanceof HaxeThisExpression thisExpression) {
        return handleThisExpression(resolver, thisExpression);
      }

      if (element instanceof HaxeSuperExpression superExpression) {
        return handleSuperExpression(context, resolver, superExpression);
      }

      if (element instanceof HaxeCallExpression callExpression) {
        return resolveWithCache(callExpression, resolver, () -> handleCallExpression(context, resolver, callExpression));
      }

      if (element instanceof HaxeReferenceExpression referenceExpression) {
        return resolveWithCache(referenceExpression, resolver, () -> handleReferenceExpression(context, resolver, referenceExpression));
      }

      if (element instanceof HaxeCastExpression castExpression) {
        return handleCastExpression(castExpression);
      }

      if (element instanceof HaxeMapLiteral mapLiteral) {
        return handleMapLiteral(context, resolver, mapLiteral);
      }

      if (element instanceof HaxeArrayLiteral arrayLiteral) {
        return handleArrayLiteral(context, resolver, arrayLiteral);
      }

      if (element instanceof HaxeRegularExpressionLiteral regexLiteral) {
        return handleRegularExpressionLiteral(regexLiteral);
      }

      if (element instanceof HaxeStringLiteralExpression) {
        return handleStringLiteralExpression(element);
      }
    }

    if (element instanceof HaxeLocalVarDeclarationList varDeclarationList) {
      return handleLocalVarDeclarationList(context, resolver, varDeclarationList);
    }

    if (element instanceof HaxeIterable iterable) {
      return handleIterable(context, resolver, iterable);
    }


    if (element instanceof  HaxeSwitchCaseBlock caseBlock) {
      return handleSwitchCaseBlock(context, resolver, caseBlock);
    }

    if (element instanceof HaxeIdentifier identifier) {
      return handleIdentifier(context, identifier);
    }

    if (element instanceof HaxeSpreadExpression spreadExpression) {
      return handleSpreadExpression(resolver, spreadExpression);
    }


    if (element instanceof HaxeAssignExpression assignExpression) {
      return handleAssignExpression(context, resolver, assignExpression);
    }

    if (element instanceof HaxeVarInit varInit) {
      return handleVarInit(context, resolver, varInit);
    }

    if (element instanceof HaxeValueExpression valueExpression) {
      return handleValueExpression(context, resolver, valueExpression);
    }

    if (element instanceof HaxeLiteralExpression) {
      return handle(element.getFirstChild(), context, resolver);
    }

    if (element instanceof HaxeExpressionList expressionList) {
      return handleExpressionList(context, resolver, expressionList);
    }

    if (element instanceof HaxeFunctionLiteral function) {
      return resolveWithCache(function, resolver, () -> handleFunctionLiteral(context, resolver, function));
    }

    if (element instanceof HaxePsiToken primitive) {
      return handlePrimitives(element, primitive);
    }


    if (element instanceof HaxeIteratorExpression iteratorExpression) {
      return handleIteratorExpression(context, resolver, iteratorExpression);
    }

    if (element instanceof HaxeArrayAccessExpression arrayAccessExpression) {
      return handleArrayAccessExpression(context, resolver, arrayAccessExpression);
    }


    if (element instanceof HaxeGuard haxeGuard) {  // Guard expression for if statement or switch case.
      return handleGuard(context, resolver, haxeGuard);
    }

    if (element instanceof HaxeParenthesizedExpression) {
      return handle(element.getChildren()[0], context, resolver);
    }

    if (element instanceof HaxeTernaryExpression ternaryExpression) {
      return handleTernaryExpression(context, resolver, ternaryExpression);
    }

    if (element instanceof HaxePrefixExpression prefixExpression) {
      return handlePrefixExpression(context, resolver, prefixExpression);
    }

    if (element instanceof HaxeIsTypeExpression) {
      return SpecificHaxeClassReference.primitive("Bool", element, null).createHolder();
    }

    //NOTE: check inheritance list before moving this one
    //check if common parent before checking all accepted variants (note should not include HaxeAssignExpression, HaxeIteratorExpression etc)
    if (element instanceof HaxeBinaryExpression expression) {
      return handleBinaryExpression(context, resolver, expression);
    }

    if (element instanceof HaxeTypeCheckExpr typeCheckExpr) {
      return handleTypeCheckExpr(context, resolver, typeCheckExpr);
    }

    if (element instanceof AbstractHaxeNamedComponent namedComponent) {
      return HaxeTypeResolver.getFieldOrMethodReturnType(namedComponent, resolver);
    }
    if (element instanceof HaxeMacroValueExpression  macroValueExpression) {
      if (macroValueExpression.getExpression() != null) {
        ResultHolder result = handle(macroValueExpression.getExpression(), context, resolver);
        if (!result.isUnknown()) {
          return HaxeMacroTypeUtil.getExprOf(element, result).createHolder();
        }else {
          return HaxeMacroTypeUtil.getExpr(element).createHolder();
        }
      }
    }
    if (element instanceof HaxeMacroClassReification classReification) {
      return HaxeMacroTypeUtil.getTypeDefinition(classReification).createHolder();
    }
    if (element instanceof HaxeMacroTypeReification typeReification) {
      return HaxeMacroTypeUtil.getComplexType(typeReification).createHolder();
    }

    if (element instanceof HaxeMacroValueReification valueReification) {
      return handle(valueReification.getExpression(), context, resolver);
    }

    if (element instanceof HaxeMacroArrayReification arrayReification) {
      ResultHolder resultHolder = handle(arrayReification.getExpression(), context, resolver);
      if (!resultHolder.isUnknown()) {
        return resultHolder;
      }

    }
    // TODO handle postfix (ex. myVar++ etc)
    // todo handle object literals
    if (element instanceof HaxeObjectLiteral objectLiteral) {
      //TODO needs class or model to  extract fields and their corresponding values
      // TODO we must also be able to do CanAssign
      //return SpecificHaxeAnonymousReference.withoutGenerics(new HaxeClassReference("{...}", objectLiteral))
      //  .createHolder();
    }

    if(log.isDebugEnabled()) log.debug("Unhandled " + element.getClass());
    return createUnknown(element);
  }

  private static ResultHolder handleMacroStatement(HaxeExpressionEvaluatorContext context, HaxeMacroStatement macroStatement, HaxeGenericResolver resolver) {
    @NotNull PsiElement[] children = macroStatement.getChildren();
    if (children.length > 0) {
      PsiElement child = children[0];
      ResultHolder result = handle(child, context, resolver);
    if (result.isUnknown() || result.isDynamic()) {
        // copy constant so we can try to improve unification (ex. null values are dynamic)
        return HaxeMacroTypeUtil.getExpr(child).withConstantValue(result.getType().getConstant()).createHolder();
      }else {
        return HaxeMacroTypeUtil.getExprOf(child, result).createHolder();
      }
    }
    // Unknown type, but since its an expression it should be an Expr right?
    return HaxeMacroTypeUtil.getExpr(macroStatement).createHolder();
  }

  private static ResultHolder resolveWithCache(@NotNull PsiElement element, @NotNull HaxeGenericResolver resolver, Supplier<ResultHolder> resolveLogic) {
    Map<PsiElement, CacheRecord> map = resultCache.get();
    Map<PsiElement, AtomicInteger> hitCounter = resultCacheHits.get();
    String resolverAsString = resolver.toCacheString();
    if (map.containsKey(element) && map.get(element).resolverAsString().equals(resolverAsString)) {
      CacheRecord cacheRecord = map.get(element);
      hitCounter.get(element).incrementAndGet();
      return cacheRecord.holder();
    }else {
      ResultHolder result = resolveLogic.get();
      if (!result.isUnknown()) {

        if (result.getClassType() != null) {
          if (!result.isTypeParameter() && result.getClassType().getGenericResolver().isEmpty()) {
            hitCounter.put(element, new AtomicInteger(0));
            map.put(element, new CacheRecord(result, resolverAsString));
          }
        }else  if (result.isFunctionType()) {
          hitCounter.put(element, new AtomicInteger(0));
          map.put(element, new CacheRecord(result, resolverAsString));
        }
      }
      return result;
    }
  }



  @NotNull
  public static ResultHolder findIteratorType(PsiElement iteratorElement) {
    HaxeForStatement forStatement = PsiTreeUtil.getParentOfType(iteratorElement, HaxeForStatement.class);
    HaxeGenericResolver forResolver = HaxeGenericResolverUtil.generateResolverFromScopeParents(forStatement);

    HaxeIterable iterable = forStatement.getIterable();
    var keyValueIteratorType = HaxeTypeResolver.getPsiElementType(iterable, iteratorElement, forResolver);

    var iteratorType = keyValueIteratorType.getClassType();
    if (iteratorType.isTypeDef()) {
      SpecificTypeReference type = iteratorType.fullyResolveTypeDefReference();
      if (type instanceof SpecificHaxeClassReference  classReference) {
        iteratorType = classReference;
      }
    }
    var iteratorTypeResolver = iteratorType.getGenericResolver();

    HaxeClassModel classModel = iteratorType.getHaxeClassModel();
    if (classModel == null) return createUnknown(iteratorElement);;
    // NOTE if "String" we need to add iterator types manually as  std string class does not have this method
    if (iteratorType.isString()) {
      if (iteratorElement instanceof HaxeIteratorkey ) {
        return SpecificTypeReference.getInt(iteratorElement).createHolder();
      }else  if (iteratorElement instanceof  HaxeIteratorValue){
        return SpecificTypeReference.getString(iteratorElement).createHolder();
      }
    }

    HaxeMethodModel iteratorReturnType = (HaxeMethodModel)classModel.getMember("next", iteratorTypeResolver);
    if (iteratorReturnType == null) return createUnknown(iteratorElement);;

    HaxeGenericResolver nextResolver = iteratorReturnType.getGenericResolver(null);
    nextResolver.addAll(iteratorTypeResolver);

    ResultHolder returnType = iteratorReturnType.getReturnType(nextResolver);
    SpecificHaxeClassReference type = returnType.getClassType();
    HaxeGenericResolver genericResolver = type.getGenericResolver();

    if (keyValueIteratorType.getClassType()!= null) {
      genericResolver.addAll(keyValueIteratorType.getClassType().getGenericResolver());
    }

    if (iteratorElement instanceof HaxeIteratorkey ) {
      return type.getHaxeClassModel().getMember("key", null).getResultType(genericResolver);
    }else  if (iteratorElement instanceof  HaxeIteratorValue){
      return type.getHaxeClassModel().getMember("value", null).getResultType(genericResolver);
    }
    return createUnknown(iteratorElement);
  }



  @NotNull
  public static ResultHolder searchReferencesForType(final HaxeComponentName componentName,
                                                     final HaxeExpressionEvaluatorContext context,
                                                     final HaxeGenericResolver resolver,
                                                     @Nullable final PsiElement searchScope
  ) {
  return searchReferencesForType(componentName, context, resolver,searchScope, null);
  }

  @NotNull
  public static ResultHolder searchReferencesForType(final HaxeComponentName componentName,
                                                     final HaxeExpressionEvaluatorContext context,
                                                     final HaxeGenericResolver resolver,
                                                     @Nullable final PsiElement searchScopePsi,
                                                     @Nullable final ResultHolder hint
  ) {
      List<PsiReference> references = referenceSearch(componentName, searchScopePsi);

      for (PsiReference reference : references) {
        ResultHolder possibleType = checkSearchResult(context, resolver, reference,componentName,  hint);
        if (possibleType != null) return possibleType;
      }

    return createUnknown(componentName);
  }
  @NotNull
  public static List<PsiReference> referenceSearch(final HaxeComponentName componentName, @Nullable final PsiElement searchScope) {
    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(componentName.getProject());
    SearchScope scope = searchScope != null ? new LocalSearchScope(searchScope) :  searchHelper.getCodeUsageScope(componentName);
    return referenceSearch(componentName, scope);
  }

  public static List<PsiReference> referenceSearch(final HaxeComponentName componentName, @NotNull final SearchScope searchScope) {
    int offset = componentName.getIdentifier().getTextRange().getEndOffset();
    return new ArrayList<>(ReferencesSearch.search(componentName, searchScope).findAll()).stream()
      .sorted((r1, r2) -> {
        int i1 = getDistance(r1, offset);
        int i2 = getDistance(r2, offset);
        return i1 - i2;
      }).toList();
  }

  @Nullable
  private static ResultHolder checkSearchResult(HaxeExpressionEvaluatorContext context, HaxeGenericResolver resolver, PsiReference reference,
                                                HaxeComponentName originalComponent,
                                               @Nullable ResultHolder hint) {

    if (originalComponent.getParent() == reference) return  null;
    if (reference instanceof HaxeExpression expression) {
      if (expression.getParent() instanceof HaxeAssignExpression assignExpression) {
        HaxeExpression rightExpression = assignExpression.getRightExpression();
        ResultHolder result = handle(rightExpression, context, resolver);
        if (!result.isUnknown()) {
          return result;
        }
        HaxeExpression leftExpression = assignExpression.getLeftExpression();
        if (leftExpression instanceof HaxeReferenceExpression referenceExpression) {
          PsiElement resolve = referenceExpression.resolve();
          if (resolve instanceof HaxePsiField psiField) {
            HaxeTypeTag tag = psiField.getTypeTag();
            if (tag != null) {
              ResultHolder holder = HaxeTypeResolver.getTypeFromTypeTag(tag, resolve);
              if (!holder.isUnknown()) {
                return holder;
              }
            }
          }
        }
      }
    }
    if (reference instanceof HaxeReferenceExpression referenceExpression) {
      // reference is callExpression
      if (referenceExpression.getParent() instanceof HaxeCallExpression callExpression) {
        SpecificFunctionReference type = hint != null ? hint.getFunctionType() : null;
        if (type != null ) {
            HaxeCallExpressionUtil.CallExpressionValidation validation =
              HaxeCallExpressionUtil.checkFunctionCall(callExpression, type);


          Map<Integer, Integer> parameterToArgument = new HashMap<>();
          for(Map.Entry<Integer, Integer> entry : validation.getArgumentToParameterIndex().entrySet()){
            parameterToArgument.put(entry.getValue(), entry.getKey());
          }

          List<SpecificFunctionReference.Argument> newArgList = new ArrayList<>();

          Map<Integer, ResultHolder> argMap = validation.getArgumentIndexToType();

          List<SpecificFunctionReference.Argument> arguments = type.getArguments();
          for (SpecificFunctionReference.Argument argument : arguments) {
            int index = argument.getIndex();
            Integer argumentIndex = parameterToArgument.get(index);
            ResultHolder newValue = argMap.get(argumentIndex);
            // if not found use old value
            if (newValue == null) newValue = argument.getType();
            newArgList.add(new SpecificFunctionReference.Argument(argument.getIndex(), argument.isOptional(), argument.isRest(), newValue, argument.getName()));
          }

          return new SpecificFunctionReference(newArgList, type.returnValue ,type.functionType,  type.context).createHolder();
        }

      }
      // parameter in call expression
      if (referenceExpression.getParent().getParent() instanceof HaxeCallExpression callExpression) {
        if (callExpression.getExpression() instanceof HaxeReference callExpressionReference) {
          final PsiElement resolved = callExpressionReference.resolve();
          HaxeCallExpressionList list = callExpression.getExpressionList();
          // check if reference used as parameter
          ResultHolder paramType = findUsageAsParameterInFunctionCall(referenceExpression, callExpression, list, resolved);
          if (paramType != null) return paramType;
          // check if our reference is the callie
          final HaxeReference leftReference = PsiTreeUtil.getChildOfType(callExpression.getExpression(), HaxeReference.class);
          if (leftReference == reference) {
            if (resolved instanceof HaxeMethod method ) {
              HaxeCallExpressionUtil.CallExpressionValidation validation = HaxeCallExpressionUtil.checkMethodCall(callExpression, method);
              ResultHolder hintResolved = validation.getResolver().resolve(hint);
              if (hintResolved != null && !hintResolved.isUnknown()) return hintResolved;
            }
          }
        }
      }
    }
    return null;
  }

  private static @Nullable ResultHolder findUsageAsParameterInFunctionCall(HaxeReferenceExpression referenceExpression,
                                                  HaxeCallExpression callExpression,
                                                  HaxeCallExpressionList list,
                                                  PsiElement resolved) {
    int index = -1;
    if (list != null) index = list.getExpressionList().indexOf(referenceExpression);
    if (index > -1 && resolved instanceof HaxeMethod method) {
      HaxeCallExpressionUtil.CallExpressionValidation validation = HaxeCallExpressionUtil.checkMethodCall(callExpression, method);
      if (validation.isStaticExtension()) index++;
      ResultHolder paramType = validation.getParameterIndexToType().getOrDefault(index, null);
      if (paramType != null) return paramType;
    }
    return null;
  }

  @NotNull
  public static ResultHolder searchReferencesForTypeParameters(final HaxePsiField field,
                                                               final HaxeExpressionEvaluatorContext context,
                                                               final HaxeGenericResolver resolver, ResultHolder resultHolder) {
    resultHolder = resultHolder.duplicate();
    HaxeComponentName componentName = field.getComponentName();
    SpecificHaxeClassReference type = resultHolder.getClassType();
    SpecificHaxeClassReference classType = type;

    HaxeGenericResolver classResolver = classType.getGenericResolver();
    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(componentName.getProject());
    final SearchScope useScope = searchHelper.getCodeUsageScope(componentName);

    List<PsiReference> references = referenceSearch(componentName, useScope);

    for (PsiReference reference : references) {
      if (reference instanceof HaxeExpression expression) {
        if (expression.getParent() instanceof HaxeAssignExpression assignExpression) {
          HaxeExpression rightExpression = assignExpression.getRightExpression();
          ResultHolder result = handleWithRecursionGuard(rightExpression, context, resolver);
          if (result != null && !result.isUnknown() && result.getType().isSameType(resultHolder.getType())) {
            HaxeGenericResolver resultResolver = result.getClassType().getGenericResolver();
            HaxeGenericResolver resultResolverWithoutUnknowns = resultResolver.withoutUnknowns();
            // check that assigned value does not contain any unknown typeParameters (ex. someArrVar = [])
            if (resultResolver.names().length == resultResolverWithoutUnknowns.names().length) {
              return result;
            }
          }
        }
        if (expression.getParent() instanceof HaxeReferenceExpression referenceExpression) {
          PsiElement resolved = referenceExpression.resolve();
          if (resolved instanceof HaxeMethodDeclaration methodDeclaration
              && referenceExpression.getParent() instanceof HaxeCallExpression callExpression) {

            HaxeMethodModel methodModel = methodDeclaration.getModel();

            HaxeCallExpressionUtil.CallExpressionValidation validation =
              HaxeCallExpressionUtil.checkMethodCall(callExpression, methodModel.getMethod());

            HaxeGenericResolver resolverFromCallExpression = validation.getResolver();
            if (resolverFromCallExpression != null) {
              // removing any typeParameters that are local to the method (we only want type parameters that are class specific)
              resolverFromCallExpression.removeAll(methodModel.getGenericResolver(null).names());
              // bit of a hack to get rid of any unknowns
              ResultHolder resolve = resolverFromCallExpression.resolve(resultHolder.getClassType().replaceUnknownsWithTypeParameter());
              if (resolve != null && !resolve.isUnknown()) {
                return resolve;
              }
            }

          }
        }
        if (expression.getParent() instanceof HaxeArrayAccessExpression arrayAccessExpression) {
          // try to find setter first if that fails try getter
          if (classType.getHaxeClass() != null) { // need to check if Haxe class exists as it will be null when SDK is missing
            HaxeNamedComponent arrayAccessSetter = classType.getHaxeClass().findArrayAccessSetter(resolver);
            if (arrayAccessSetter instanceof HaxeMethodDeclaration methodDeclaration) {
              HaxeMethodModel methodModel = methodDeclaration.getModel();
              // make sure we are using class level typeParameters (and not method level)
              if (methodModel.getGenericParams().isEmpty()) {
                List<HaxeParameterModel> parameters = methodModel.getParameters();

                HaxeTypeTag keyParamPsi = parameters.get(0).getTypeTagPsi();
                HaxeTypeTag valueParamPsi = parameters.get(1).getTypeTagPsi();


                @NotNull String[] specificNames = classResolver.names();
                for (int i = 0; i < specificNames.length; i++) {
                  String keyPsiName = keyParamPsi.getTypeOrAnonymous().getType().getText();
                  // key
                  if (keyPsiName.equals(specificNames[i])) {
                    HaxeExpression keyExpression = arrayAccessExpression.getExpressionList().get(1);
                    ResultHolder handle = handle(keyExpression, context, resolver);
                    if (type.getSpecifics()[i].isUnknown()) {
                      type.getSpecifics()[i] = handle;
                    }
                    else {
                      ResultHolder unified = HaxeTypeUnifier.unify(handle, type.getSpecifics()[i]);
                      type.getSpecifics()[i] = unified;
                    }
                  }
                  // value
                  if (arrayAccessExpression.getParent() instanceof HaxeBinaryExpression binaryExpression) {
                    String valuePsiName = valueParamPsi.getTypeOrAnonymous().getType().getText();
                    if (valuePsiName.equals(specificNames[i])) {
                      HaxeExpression keyExpression = binaryExpression.getExpressionList().get(1);
                      ResultHolder handle = handle(keyExpression, context, resolver);
                      if (type.getSpecifics()[i].isUnknown()) {
                        type.getSpecifics()[i] = handle;
                      }
                      else {
                        ResultHolder unified = HaxeTypeUnifier.unify(handle, type.getSpecifics()[i]);
                        type.getSpecifics()[i] = unified;
                      }
                    }
                  }
                }
              }
            }
            else {
              HaxeNamedComponent arrayAccessGetter = classType.getHaxeClass().findArrayAccessGetter(resolver);
              if (arrayAccessGetter instanceof HaxeMethodDeclaration methodDeclaration) {
                HaxeMethodModel methodModel = methodDeclaration.getModel();
                // make sure we are using class level typeParameters (and not method level)
                if (methodModel.getGenericParams().isEmpty()) {
                  List<HaxeParameterModel> parameters = methodModel.getParameters();
                  HaxeParameterModel keyParameter = parameters.get(0);
                  HaxeTypeTag keyParamPsi = keyParameter.getTypeTagPsi();

                  @NotNull String[] specificNames = classResolver.names();
                  for (int i = 0; i < specificNames.length; i++) {
                    String keyPsiName = keyParamPsi.getTypeOrAnonymous().getType().getText();
                    if (keyPsiName.equals(specificNames[i])) {
                      HaxeExpression keyExpression = arrayAccessExpression.getExpressionList().get(1);
                      ResultHolder handle = handle(keyExpression, context, resolver);
                      if (type.getSpecifics()[i].isUnknown()) {
                        type.getSpecifics()[i] = handle;
                      }
                      else {
                        ResultHolder unified = HaxeTypeUnifier.unify(handle, type.getSpecifics()[i]);
                        type.getSpecifics()[i] = unified;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return  resultHolder;
  }
}
