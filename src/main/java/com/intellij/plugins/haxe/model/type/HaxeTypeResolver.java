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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.lang.psi.impl.AbstractHaxeNamedComponent;
import com.intellij.plugins.haxe.lang.psi.impl.HaxeMethodImpl;
import com.intellij.plugins.haxe.model.*;
import com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluator;
import com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluatorContext;
import com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluatorReturnInfo;
import com.intellij.plugins.haxe.model.type.SpecificFunctionReference.Argument;
import com.intellij.plugins.haxe.util.HaxeAbstractEnumUtil;
import com.intellij.plugins.haxe.util.HaxeResolveUtil;
import com.intellij.plugins.haxe.util.UsefulPsiTreeUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluatorHandlers.isDynamicBecauseOfNullValueInit;
import static com.intellij.plugins.haxe.model.evaluator.HaxeExpressionUsageUtil.tryToFindTypeFromUsage;

public class HaxeTypeResolver {
  @NotNull
  static public ResultHolder getFieldOrMethodReturnType(@NotNull AbstractHaxeNamedComponent comp) {
    return getFieldOrMethodReturnType(comp, null);
  }

  // @TODO: Check if cache works
  @NotNull
  static public ResultHolder getFieldOrMethodReturnType(@NotNull HaxeNamedComponent comp, @Nullable HaxeGenericResolver resolver) {
    // @TODO: cache should check if any related type has changed, which return depends
    if (comp.getContainingFile() == null) {
      return SpecificHaxeClassReference.getUnknown(comp).createHolder();
    }

    // EMB - Skip the cache while debugging.  There may be a recursive issue.  There are definitely multi-threading issues.
    //long stamp = comp.getContainingFile().getModificationStamp();
    //if (comp._cachedType == null || comp._cachedTypeStamp != stamp) {
    //  comp._cachedType = _getFieldOrMethodReturnType(comp, resolver);
    //  comp._cachedTypeStamp = stamp;
    //}
    //
    //return comp._cachedType;
    return _getFieldOrMethodReturnType(comp, resolver);
  }

  @NotNull
  static public ResultHolder getMethodFunctionType(PsiElement comp, @Nullable HaxeGenericResolver resolver) {
    if (comp instanceof HaxeMethod method) {
      resolver = resolver == null ? null : resolver.withoutUnknowns();
      HaxeGenericResolver methodResolver = method.getModel().getGenericResolver(null);
      methodResolver.addAll(resolver);
      methodResolver = methodResolver.removeClassScopeIfMethodIsPresent().withoutUnknowns();
      return method.getModel().getFunctionType(methodResolver).createHolder();
    }
    // @TODO: error
    return SpecificTypeReference.getInvalid(comp).createHolder();
  }

  @NotNull
  static private ResultHolder _getFieldOrMethodReturnType(HaxeNamedComponent comp, @Nullable HaxeGenericResolver resolver) {
    try {
      if (comp instanceof PsiMethod) {
        return getFunctionReturnType(comp, resolver);
      }
      else if (comp instanceof HaxeFunctionLiteral) {
        return getFunctionReturnType(comp, resolver);
      }
      else if (comp instanceof HaxeEnumValueDeclaration enumValueDeclaration) {
        return getEnumReturnType(enumValueDeclaration, resolver);
      }
      else if (comp instanceof HaxeParameter parameter) {
        return getPsiElementType(parameter, resolver);
      }
      else {
        return getFieldType(comp, resolver);
      }
    }
    catch (Throwable e) {
      e.printStackTrace();
      return SpecificTypeReference.getUnknown(comp).createHolder();
    }
  }

  @NotNull
  public static ResultHolder getEnumReturnType(HaxeEnumValueDeclaration comp, HaxeGenericResolver resolver) {
    HaxeEnumValueModel model = (HaxeEnumValueModel)comp.getModel();
    HaxeClassModel declaringEnum = model.getDeclaringEnum();
    // type tags on enum constructors should not affect the returned enumValue
    if(declaringEnum!= null && !declaringEnum.isAbstractType()) {
      return new SpecificEnumValueReference(comp, comp.getParent(), resolver).createHolder();
    }
    if (model instanceof HaxeEnumValueConstructorModel constructorModel) {
      ResultHolder result = getTypeFromTypeTag(constructorModel.getEnumValuePsi().getTypeTag(), comp.getParent());
      if (result.isUnknown()) {
        return  new SpecificEnumValueReference(comp, comp.getParent(), resolver).createHolder();
      }else {
        return result;
      }
    }
    return SpecificHaxeClassReference.getUnknown(comp).createHolder();
  }

  @NotNull
  static private ResultHolder getFieldType(HaxeNamedComponent comp, HaxeGenericResolver resolver) {
    //ResultHolder type = getTypeFromTypeTag(comp);
    // Here detect assignment
    final ResultHolder abstractEnumType = HaxeAbstractEnumUtil.getFieldType(comp, resolver);
    if (abstractEnumType != null) {
      return abstractEnumType;
    }
    if (comp instanceof HaxeObjectLiteralElement literalElement) {
      HaxeExpression expression = literalElement.getExpression();
      if(expression != null) {
        return HaxeTypeResolver.getPsiElementType(expression, resolver);
      }
    }

    if (comp instanceof HaxePsiField psiField) {
      ResultHolder result = null;
      ResultHolder initType = null;
      HaxeVarInit init = psiField.getVarInit();
      if (init != null) {
        PsiElement child = init.getExpression();
        initType = HaxeTypeResolver.getPsiElementType(child, resolver);
        boolean isConstant = psiField.hasModifierProperty(HaxePsiModifier.INLINE) && psiField.isStatic();
        result = isConstant ? initType : initType.withConstantValue(null);
      }

      HaxeTypeTag typeTag = psiField.getTypeTag();
      if (typeTag != null) {
        ResultHolder typeFromTag = getTypeFromTypeTag(typeTag, comp);
        if (resolver != null) {
          ResultHolder resolved = resolver.resolve(typeFromTag);
          if (resolved != null) typeFromTag = resolved;
          if (psiField.isOptional() && !typeFromTag.isNullWrappedType()) {
            typeFromTag = typeFromTag.wrapInNullType();
          }
        }
        final Object initConstant = result != null ? result.getType().getConstant() : null;
        if (typeFromTag != null) {
          result = typeFromTag.withConstantValue(initConstant);
        }
      }
      // look for usage (only relevant for  localVars)
      if (init == null && typeTag == null) {
        HaxeTypeResolver.getPsiElementType(psiField, null);
      }
      if (result != null) {
        if (typeTag == null && isDynamicBecauseOfNullValueInit(initType)) {
          HaxeComponentName componentName = psiField.getComponentName();
          result = tryToFindTypeFromUsage(componentName, initType, initType, new HaxeExpressionEvaluatorContext(psiField), resolver, componentName.getContainingFile());
        }
        return result;
      }
    }
    if (comp instanceof  HaxeGenericListPart genericListPart) {
      HaxeComponentName componentName = genericListPart.getComponentName();
      if(componentName != null) {
        HaxeClassReference reference = new HaxeClassReference(genericListPart.getName(), componentName, true);
        return SpecificHaxeClassReference.withoutGenerics(reference).createHolder();
      }
    }

    return SpecificTypeReference.getUnknown(comp).createHolder();
  }

  /**
   * Resolves declaration (NOT reference) parameters to types.
   *
   * @param comp     Declaration with parameters to resolve
   * @param resolver Resolver from a *reference* with instance parameters to apply to the declaration.
   * @return A list of specific resolved types matching the parameters of the declaration.
   */
  @NotNull
  static public ResultHolder[] resolveDeclarationParametersToTypes(@NotNull HaxeNamedComponent comp, HaxeGenericResolver resolver) {
    return resolveDeclarationParametersToTypes(comp, resolver, true);
  }

  @NotNull
  static public ResultHolder[] resolveDeclarationParametersToTypes(@NotNull HaxeNamedComponent comp,
                                                                   HaxeGenericResolver resolver,
                                                                   boolean resolveElementTypes) {

    List<HaxeGenericParamModel> genericParams = null;

    // Note that this clause must come before 'comp instanceof HaxeClass'.
    if (comp instanceof HaxeAnonymousType) {
      // For typedefs of anonymous functions, the generic parameters from the typedef are used.
      // Switch the context.
      HaxeNamedComponent typeParameterContributor = HaxeResolveUtil.findTypeParameterContributor(comp);
      comp = null != typeParameterContributor ? typeParameterContributor : comp;
    }

    if (comp instanceof HaxeTypedefDeclaration typedefDeclaration) {
      // TODO: Make a HaxeTypedefModel and use it here.
      HaxeGenericParam param = typedefDeclaration.getGenericParam();
      genericParams = translateGenericParamsToModelList(param);
    }
    else if (comp instanceof HaxeClass haxeClass) {
      HaxeClassModel model = haxeClass.getModel();
      genericParams = model.getGenericParams();
    }
    else if (comp instanceof HaxeMethodDeclaration methodDeclaration) {
      HaxeMethodModel model = methodDeclaration.getModel();
      genericParams = model.getGenericParams();
    }
    else if (comp instanceof HaxeEnumValueDeclaration enumValueDeclaration) {
      HaxeModel model =  enumValueDeclaration.getModel();
      if (model instanceof  HaxeEnumValueConstructorModel constructorModel) {
        genericParams = constructorModel.getGenericParams();
      }
    }

    if (null != genericParams && !genericParams.isEmpty()) {
      ResultHolder[] specifics = new ResultHolder[genericParams.size()];
      int i = 0;
      for (HaxeGenericParamModel param : genericParams) {
        ResultHolder resolved = null;
        if (null != resolver) {
          resolved = resolver.resolve(param.getName());  // Null if no name match.
        }
        if (null == resolved && resolveElementTypes) {
          resolved = getPsiElementType(param.getPsi(), comp, resolver);
        }
        ResultHolder result;
        if (resolved != null && !resolved.isUnknown()) {
          result = resolved;
        }else if (resolveElementTypes && !isDynamic(comp)) { // hiding typeParameters for dynamic
          HaxeClassReference clazz = new HaxeClassReference(param.getName(), param.getPsi(), true);
          result = new ResultHolder(SpecificHaxeClassReference.withoutGenerics(clazz));
        }else {
          result = new ResultHolder(SpecificTypeReference.getUnknown(param.getPsi()));
        }
        specifics[i++] = result;
      }
      return specifics;
    }
    return ResultHolder.EMPTY;
  }

  private static boolean isDynamic(HaxeNamedComponent comp) {
    return comp instanceof HaxeClass aClass && aClass.getModel().getName().equals("Dynamic");
  }

  private static List<HaxeGenericParamModel> translateGenericParamsToModelList(HaxeGenericParam param) {
    List<HaxeGenericParamModel> genericParams = null;
    if (null != param) {
      List<HaxeGenericListPart> list = param.getGenericListPartList();
      genericParams = new ArrayList<>(list.size());
      int index = 0;
      for (HaxeGenericListPart listPart : list) {
        genericParams.add(new HaxeGenericParamModel(listPart, index));
        index++;
      }
    }
    return genericParams;
  }

  /**
   * Resolves the given type, if it's generic, against the resolver and then
   * resolves its type parameters, if any, against the same resolver.
   *
   * @param result   A type result
   * @param resolver
   * @return
   */
  @NotNull
  static public ResultHolder resolveParameterizedType(@NotNull ResultHolder result, HaxeGenericResolver resolver) {
    return resolveParameterizedType(result, resolver, false);
  }


  private static final RecursionGuard<ResultHolder> propagateRecursionGuard = RecursionManager.createGuard("propagateRecursionGuard");

  @NotNull
  static public ResultHolder resolveParameterizedType(@NotNull ResultHolder result, HaxeGenericResolver resolver, boolean returnType) {
    SpecificTypeReference typeReference = result.getType();
    if (resolver != null) {
      if (typeReference instanceof SpecificHaxeClassReference haxeClassReference && typeReference.isTypeParameter()) {
        String className = haxeClassReference.getClassName();
        ResultHolder resolved = returnType ? resolver.resolveReturnType(haxeClassReference) : resolver.resolve(className);
        if (null != resolved && !resolved.isUnknown()) {
          result = resolved;
          // removing from resolver to avoid attempting to propagate to the resolved class
          // if T = Array<T> and we continue to propagate T into Array<T>, then it will go on forever Array<Array<Array<...>>
          resolver = resolver.without(className).withoutUnknowns();
        }
      }
    }
    final HaxeGenericResolver finalResolver = resolver;
    // Resolve any generics on the resolved type as well.
    typeReference = result.getType();
    if (typeReference instanceof SpecificHaxeClassReference classReference) {
      ResultHolder holder = propagateRecursionGuard.computePreventingRecursion(result, true, () ->
        SpecificHaxeClassReference.propagateGenericsToType(classReference.createHolder(), finalResolver, returnType));
      if (holder != null) result = holder;
    }

    return result;
  }

  @NotNull
  static private ResultHolder getFunctionReturnType(HaxeNamedComponent comp, HaxeGenericResolver resolver) {
    if (comp instanceof HaxeMethodImpl method) {
      HaxeTypeTag typeTag = method.getTypeTag();
      if (typeTag != null) {
        if (resolver != null) {
          HaxeTypeOrAnonymous typeOrAnonymous = typeTag.getTypeOrAnonymous();
          if (typeOrAnonymous != null) {
            HaxeClass aClass = (HaxeClass)method.getContainingClass();
            HaxeGenericResolver localResolver = new HaxeGenericResolver();
            HaxeGenericResolver classSpecificResolver = HaxeGenericSpecialization.fromGenericResolver(null, resolver).toGenericResolver(aClass).withoutUnknowns();
            localResolver.addAll(resolver);
            localResolver.addAll(classSpecificResolver); // overwrite any existing typeParameters with specifics for class
            ResultHolder resolve = HaxeTypeResolver.getTypeFromTypeOrAnonymous(typeOrAnonymous, localResolver, true);
            if (resolve != null && !resolve.isUnknown()) {
              return resolve;
            }
          }
        }
        return resolveParameterizedType(getTypeFromTypeTag(typeTag, comp), resolver, true);
      }
    }
    if (comp instanceof HaxeConstructor constructor) {
      // TODO constrcutors should return their declaringClass type
      HaxeClassModel declaringClass = constructor.getModel().getDeclaringClass();
      ResultHolder type = declaringClass.getInstanceType();
      if (resolver != null) {
        type = resolver.withoutUnknowns().resolve(type);
      }
      return type;
    }
    else if (comp instanceof HaxeMethod method) {
      HaxeMethodModel methodModel = method.getModel();
      PsiElement psi = methodModel.getBodyPsi();
      if (psi == null) psi = methodModel.getBasePsi();

      // local  function declarations  must use return statements as opposite to HaxeFunctionLiteral and lambda expressions
      // witch can use the last expression as return value
      final PsiElement methodBody = psi;
      List<HaxeReturnStatement> returnStatementList =
        CachedValuesManager.getCachedValue(methodBody, () -> HaxeTypeResolver.findReturnStatementsForMethod(methodBody));
      List<ResultHolder> returnTypes = returnStatementList.stream().map(statement ->  {
        if (processedElements.get().contains(statement)) {
          return null; // possible recursion, ignore this return statement
        }else {
          return getPsiElementType(statement, resolver);
        }
      }).filter(Objects::nonNull)
        .toList();

      if (returnTypes.isEmpty() && returnStatementList.isEmpty()) {
        return SpecificHaxeClassReference.getVoid(psi).createHolder();
      }
      if (returnStatementList.isEmpty()) {
        return SpecificHaxeClassReference.getDynamic(psi).createHolder();
      }

      ResultHolder holder = HaxeTypeUnifier.unifyHolders(returnTypes, psi, UnificationRules.PREFER_VOID);

      // method typeParameters should have been used when resolving returnTypes, we want to avoid double resolve
      return resolveParameterizedType(holder, resolver == null ? null : resolver.withoutMethodTypeParameters());

    }
    else if (comp instanceof HaxeFunctionLiteral) {
      final HaxeExpressionEvaluatorContext context = getPsiElementType(comp.getLastChild(), (AnnotationHolder)null, resolver);
      return resolveParameterizedType(context.getReturnType(), resolver);
    }
    else {
      throw new RuntimeException("Can't determine function type if the element isn't a method or function literal.");
    }
  }

  @NotNull
  public static CachedValueProvider.Result<List<HaxeReturnStatement>> findReturnStatementsForMethod(PsiElement psi) {

    if (psi instanceof HaxeReturnStatement statement) {
      return new CachedValueProvider.Result<>(List.of(statement), psi);
    }
    else {
      List<HaxeReturnStatement> statements = new ArrayList<>();
      // search for ReturnStatements but filter out any that are part of local functions
      Collection<HaxeReturnStatement> returnStatements = PsiTreeUtil.findChildrenOfType(psi, HaxeReturnStatement.class);
      for (HaxeReturnStatement statement : returnStatements) {
        HaxePsiCompositeElement type = PsiTreeUtil.getParentOfType(statement, HaxeLocalFunctionDeclaration.class, HaxeFunctionLiteral.class, HaxeMacroTopLevelDeclaration.class);
        // we want to avoid returning return statements that are in a deeper scope / inside a local function, however we might also be
        // searching for the returnType of a local function, so we check if any parent of local function is null or the same as the function
        // we are searching
        if (type == null || type == psi.getParent()) statements.add(statement);
      }
      return new CachedValueProvider.Result<>(statements, psi);
    }
  }

  private static boolean isVoidReturn(HaxeReturnStatement statement) {
    //instead of checking all possible types that a return statement might have
    // we just check if its only child is ";" to determine if its a void return
    PsiElement child = statement.getFirstChild();
    if (child == null || child.getNextSibling() == null || child.getNextSibling().textMatches(";")) {
      return true;
    }
    return false;
  }

  @NotNull
  static public ResultHolder getTypeFromTypeTag(@Nullable final HaxeTypeTag typeTag, @NotNull PsiElement context) {
    if (typeTag != null) {

      final HaxeTypeOrAnonymous typeOrAnonymous = typeTag.getTypeOrAnonymous();
      if (typeOrAnonymous != null) {
        return getTypeFromTypeOrAnonymous(typeOrAnonymous);
      }

      final HaxeFunctionType functionType = typeTag.getFunctionType();
      if (functionType != null) {
        return getTypeFromFunctionType(functionType);
      }
    }

    return SpecificTypeReference.getUnknown(context).createHolder();
  }

  @NotNull
  static public ResultHolder getTypeFromTypeTag(AbstractHaxeNamedComponent comp, @NotNull PsiElement context) {
    return getTypeFromTypeTag(PsiTreeUtil.getChildOfType(comp, HaxeTypeTag.class), context);
  }

  @NotNull
  static public ResultHolder getTypeFromFunctionType(HaxeFunctionType type) {
    ArrayList<Argument> args = new ArrayList<>();

    List<HaxeFunctionArgument> list = type.getFunctionArgumentList();
    for (int i = 0; i < list.size(); i++) {
      HaxeFunctionArgument argument = list.get(i);
      ResultHolder argumentType = getTypeFromFunctionArgument(argument);
      boolean optional = argument.getOptionalMark() != null;
      boolean rest = argument.getRestArgumentType() != null;
      args.add(new Argument(i, optional, rest, argumentType, getArgumentName(argument)));
    }

    if (args.size() == 1 && args.get(0).isVoid()) {
      args.clear();
    }

    ResultHolder returnValue = null;
    HaxeFunctionReturnType returnType = type.getFunctionReturnType();
    if (returnType != null) {
      HaxeFunctionType functionType = returnType.getFunctionType();
      if (functionType != null) {
        returnValue = getTypeFromFunctionType(functionType);
      }
      else {
        HaxeTypeOrAnonymous typeOrAnonymous = returnType.getTypeOrAnonymous();
        if (typeOrAnonymous != null) {
          returnValue = getTypeFromTypeOrAnonymous(typeOrAnonymous);
        }
      }
    }

    if (returnValue == null) {
      returnValue = SpecificTypeReference.getInvalid(type).createHolder();
    }

    return new SpecificFunctionReference(args, returnValue, type, type).createHolder();
  }

  static String getArgumentName(HaxeFunctionArgument argument) {
    HaxeComponentName componentName = argument.getComponentName();
    String argumentName = null;
    if (componentName != null) {
      argumentName = componentName.getIdentifier().getText();
    }

    return argumentName;
  }

  public static ResultHolder getTypeFromFunctionArgument(HaxeFunctionArgument argument) {
    HaxeFunctionType functionType = argument.getFunctionType();
    if (functionType != null) return getTypeFromFunctionType(functionType);

    HaxeTypeOrAnonymous typeOrAnonymous = argument.getTypeOrAnonymous();
    if (typeOrAnonymous != null) return getTypeFromTypeOrAnonymous(typeOrAnonymous);

    return SpecificTypeReference.getUnknown(argument).createHolder();
  }

  /**
   * Resolves the type reference in HaxeType, including type parameters,
   * WITHOUT generic parameters being fully resolved.
   * See {@link SpecificHaxeClassReference#propagateGenericsToType(SpecificHaxeClassReference, HaxeGenericResolver)}
   * to fully resolve generic parameters.
   * <p>
   * NOTE: If types were constrained in scope, (e.g. {@code subClass<T:Constraint> extends superClass<T>})the type
   * parameter resolves to the constraint type because that's what {@link HaxeResolver#resolve} returns.
   *
   * @param type - Type reference.
   * @return - resolved type with non-generic parameters resolved.
   * (e.g. &lt;T&gt; will remain an unresolved reference to T.)
   */
  @NotNull
  static public ResultHolder getTypeFromType(@NotNull HaxeType type) {
    return getTypeFromType(type, null);
  }

  static public boolean isTypeParameter(HaxeReferenceExpression expression) {
    if (PsiTreeUtil.getParentOfType(expression, HaxeTypeParam.class) != null) {
      return true;
    }
    if (expression.resolve() instanceof HaxeGenericListPart) {
      return true;
    }
    return false;
  }

  /**
   * Resolves the type reference in HaxeType, including type parameters,
   * and fully resolving type parameters if they are fully specified types or
   * appear in the HaxeGenericResolver.
   * See {@link SpecificHaxeClassReference#propagateGenericsToType(SpecificHaxeClassReference, HaxeGenericResolver)}
   * to fully resolve generic parameters.
   *
   * @param type     - Type reference.
   * @param resolver - Resolver containing a type->parameter map.
   * @return - resolved type with non-generic parameters resolved.
   * (e.g. &lt;T&gt; will remain an unresolved reference to T.)
   */
  @NotNull
  static public ResultHolder getTypeFromType(@NotNull HaxeType type, @Nullable HaxeGenericResolver resolver) {
    return getTypeFromType(type, resolver, false);
  }

  static public ResultHolder getTypeFromType(@NotNull HaxeType type, @Nullable HaxeGenericResolver resolver, boolean useAssignHint) {
    if (resolver != null && !resolver.isEmpty()) {
      ResultHolder resolve = resolver.resolve(type, useAssignHint);
      if (resolve != null && !resolve.isUnknown()) {
        return resolve;
      }
    }

    HaxeReferenceExpression expression = type.getReferenceExpression();
    HaxeClassReference reference;
    ResultHolder result = HaxeExpressionEvaluator.evaluate(expression, new HaxeGenericResolver()).result;
    final HaxeClass resolvedHaxeClass =( result != null  && !result.isUnknown() && result.isClassType()) ? result.getClassType().getHaxeClass() : null;
    if (resolvedHaxeClass == null) {
      boolean isTypeParameter = isTypeParameter(expression);
      reference = new HaxeClassReference(expression.getText(), type, isTypeParameter);
    }
    else {
      reference = new HaxeClassReference(resolvedHaxeClass.getModel(), type);
    }

    HaxeTypeParam param = type.getTypeParam();
    ArrayList<ResultHolder> references = new ArrayList<>();
    if (param != null) {
      for (HaxeTypeListPart part : param.getTypeList().getTypeListPartList()) {
        ResultHolder partResult = null;
        if (resolver != null && !resolver.isEmpty()) {
          partResult = resolver.resolve(part, useAssignHint);
        }
        if (null == partResult) {
          HaxeFunctionType fnType = part.getFunctionType();
          if (fnType != null) {
            partResult = getTypeFromFunctionType(fnType);
          }
          else {
            HaxeTypeOrAnonymous toa = part.getTypeOrAnonymous();
            if (toa != null) {
              partResult = getTypeFromTypeOrAnonymous(toa, resolver);
            }
          }
        }
        if (null == partResult) {
          partResult = SpecificTypeReference.getUnknown(type).createHolder();
        }
        references.add(partResult);
      }
    }
    else if (null != resolvedHaxeClass) {

      ResultHolder[] specifics = result.getClassType().getGenericResolver().getSpecificsFor(resolvedHaxeClass);
      Collections.addAll(references, specifics);
    }
    return SpecificHaxeClassReference.withGenerics(reference, references.toArray(ResultHolder.EMPTY)).createHolder();
  }

  @NotNull
  static public ResultHolder getTypeFromTypeOrAnonymous(@NotNull HaxeTypeOrAnonymous typeOrAnonymous) {
    return getTypeFromTypeOrAnonymous(typeOrAnonymous, null);
  }

  static public ResultHolder getTypeFromTypeOrAnonymous(@NotNull HaxeTypeOrAnonymous typeOrAnonymous,
                                                        @Nullable HaxeGenericResolver parentResolver) {
    return getTypeFromTypeOrAnonymous(typeOrAnonymous, parentResolver, false);
  }

  static public ResultHolder getTypeFromTypeOrAnonymous(@NotNull HaxeTypeOrAnonymous typeOrAnonymous,
                                                        @Nullable HaxeGenericResolver parentResolver,
                                                        boolean useAssignHint) {
    // @TODO: Do a proper type resolving
    HaxeType type = typeOrAnonymous.getType();
    if (type != null) {
      return getTypeFromType(type, parentResolver, useAssignHint);
    }
    final HaxeAnonymousType anonymousType = typeOrAnonymous.getAnonymousType();
    if (anonymousType != null) {
      HaxeNamedComponent contributor = HaxeResolveUtil.findTypeParameterContributor(anonymousType);
      if (null != contributor ) {
        HaxeClassModel contributorModel = HaxeClassModel.fromElement(contributor);
        if (contributorModel.hasGenericParams()) {
          HaxeGenericResolver localResolver = new HaxeGenericResolver();
          // attempt at avoiding recursion issues
          // for "function X<T:{}>();" contributor would be function X and T would be the anonymous type
          // if we try to copy generics from X to T we need collect generics and end up trying to resolve T again
          if(!isChildOf(anonymousType, contributor)) {
            HaxeGenericResolver resolver = contributorModel.getGenericResolver(parentResolver);
            localResolver.addAll(resolver);
          }
          localResolver.addAll(parentResolver); // anonymous inherits its typeParameter from parent
          return SpecificHaxeAnonymousReference.withGenerics(new HaxeClassReference(anonymousType.getModel(), typeOrAnonymous), localResolver)
            .createHolder();
        }
      }
      return SpecificHaxeClassReference.withoutGenerics(new HaxeClassReference(anonymousType.getModel(), typeOrAnonymous)).createHolder();
    }
    return SpecificTypeReference.getDynamic(typeOrAnonymous).createHolder();
  }

  private static boolean isChildOf(HaxeAnonymousType type, HaxeNamedComponent contributor) {
    PsiElement parent = type.getParent();
    while(parent != null) {
      if (parent == contributor)
        return true;
      parent = parent.getParent();
    }
    return false;
  }

  @NotNull
  static public ResultHolder getPsiElementType(@NotNull PsiElement element, HaxeGenericResolver resolver) {
    return getPsiElementType(element, (PsiElement)null, resolver);
  }

  @NotNull
  static public ResultHolder getPsiElementType(@NotNull PsiElement element,
                                               @Nullable PsiElement resolveContext,
                                               HaxeGenericResolver resolver) {
    if (element == resolveContext) return SpecificTypeReference.getInvalid(element).createHolder();
    return getPsiElementType(element, (AnnotationHolder)null, resolver).result;
  }

  static private void checkMethod(PsiElement element, HaxeExpressionEvaluatorContext context) {
    if (!(element instanceof HaxeMethod)) return;
    final HaxeTypeTag typeTag = UsefulPsiTreeUtil.getChild(element, HaxeTypeTag.class);
    ResultHolder expectedType = SpecificTypeReference.getDynamic(element).createHolder();
    if (typeTag == null) {
      final List<HaxeExpressionEvaluatorReturnInfo> infos = context.getReturnInfos();
      if (!infos.isEmpty()) {
        expectedType = infos.get(0).type();
      }
    }
    else {
      expectedType = getTypeFromTypeTag(typeTag, element);
    }

    if (expectedType == null) return;
    for (HaxeExpressionEvaluatorReturnInfo retinfo : context.getReturnInfos()) {
      if (context.holder != null) {
        if (expectedType.canAssign(retinfo.type())) continue;
        context.addError(
          retinfo.element(),
          "Can't return " + retinfo.type() + ", expected " + expectedType.toStringWithoutConstant()
        );
      }
    }
  }

  @NotNull
  static public HaxeExpressionEvaluatorContext getPsiElementType(@NotNull PsiElement element, @Nullable AnnotationHolder holder,
                                                                 HaxeGenericResolver resolver) {
    return evaluateFunction(new HaxeExpressionEvaluatorContext(element, holder), resolver);
  }

  // @TODO: hack to avoid stack overflow, until a proper non-static fix is done
  //        At least, we've made it thread local, so the threads aren't stomping on each other any more.
  static private ThreadLocal<? extends Set<PsiElement>> processedElements = ThreadLocal.withInitial(HashSet::new);

  @NotNull
  static public HaxeExpressionEvaluatorContext evaluateFunction(@NotNull HaxeExpressionEvaluatorContext context,
                                                                HaxeGenericResolver resolver) {
    PsiElement element = context.root;
    if (processedElements.get().contains(element)) {
      context.result = SpecificHaxeClassReference.getUnknown(element).createHolder();
      return context;
    }

    processedElements.get().add(element);
    try {
      HaxeExpressionEvaluator.evaluate(element, context, resolver);
      checkMethod(element.getParent(), context);

      for (HaxeExpressionEvaluatorContext lambda : context.lambdas) {
        evaluateFunction(lambda, resolver);
      }

      return context;
    }
    finally {
      processedElements.get().remove(element);
    }
  }
}
