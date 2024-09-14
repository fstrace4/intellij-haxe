package com.intellij.plugins.haxe.model.evaluator;

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.plugins.haxe.ide.annotator.semantics.HaxeCallExpressionUtil;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.model.HaxeBaseMemberModel;
import com.intellij.plugins.haxe.model.HaxeClassModel;
import com.intellij.plugins.haxe.model.HaxeMethodModel;
import com.intellij.plugins.haxe.model.HaxeParameterModel;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluator.*;
import static com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluator.searchReferencesForType;
import static com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluatorHandlers.*;

public class HaxeExpressionUsageUtil {

  private static final RecursionGuard<PsiElement>
    searchReferencesForTypeGuard = RecursionManager.createGuard("searchReferencesForTypeGuard");


  public static @Nullable ResultHolder tryToFindTypeFromUsage(HaxeComponentName element,
                                                              ResultHolder result,
                                                              ResultHolder hint,
                                                              HaxeExpressionEvaluatorContext context,
                                                              HaxeGenericResolver resolver,
                                                              @Nullable PsiElement scope
  ) {
    ResultHolder searchResult = searchReferencesForTypeGuard
      .computePreventingRecursion(element, true, () -> searchReferencesForType(element, context, resolver, scope, hint));
    if (searchResult != null && !searchResult.isUnknown()) {
      if (result == null) {
        result = searchResult;
      }else if(isDynamicBecauseOfNullValueInit(result)){
        result = HaxeTypeUnifier.unify(result, searchResult, UnificationRules.UNIFY_NULL);
      }else if (searchResult.getType().isSameType(result.getType())) {
        result = HaxeTypeUnifier.unify(result, searchResult);
      }
    }
    return result;
  }

  public static @Nullable ResultHolder findUsageAsParameterInFunctionCall(HaxeExpression referenceExpression,
                                                                          HaxeCallExpression callExpression,
                                                                          HaxeCallExpressionList list,
                                                                          PsiElement resolved) {
    int index = -1;
    if (list != null) index = list.getExpressionList().indexOf(referenceExpression);
    if (index == -1) return null;

    if (resolved instanceof HaxeMethod method) {
      HaxeCallExpressionUtil.CallExpressionValidation validation = HaxeCallExpressionUtil.checkMethodCall(callExpression, method);
      if (validation.isStaticExtension()) index++;
      return validation.getParameterIndexToType().getOrDefault(index, null);
    }
    return null;
  }

  public static @Nullable ResultHolder findUsageAsParameterInFunctionCall(HaxeExpression referenceExpression,
                                                                          HaxeCallExpression callExpression,
                                                                          HaxeCallExpressionList list,
                                                                          SpecificFunctionReference functionReference) {
    int index = -1;
    if (list != null) index = list.getExpressionList().indexOf(referenceExpression);
    if (index == -1) return null;
    HaxeCallExpressionUtil.CallExpressionValidation validation =
      HaxeCallExpressionUtil.checkFunctionCall(callExpression, functionReference);

    if (validation.isStaticExtension()) index++;
    return validation.getParameterIndexToType().getOrDefault(index, null);
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
    //TODO we should handle this logic as monomorph as well and only accpet first value
    // NOTE : we might have to change this code a bit if we need to  iterate several references to collect multiple typeParameters / specifics
    for (PsiReference reference : references) {
      if (reference instanceof HaxeExpression expression) {
        PsiElement parent = expression.getParent();

        if (reference instanceof HaxeReferenceExpression referenceExpression) {
          ResultHolder unified = tryTypeFromParameterFromCallExpression(resultHolder, referenceExpression, parent);
          if (unified != null) return unified;
        }

        if (parent instanceof HaxeAssignExpression assignExpression) {
          ResultHolder result = tryTypeFromAssignExpression(context, resolver, resultHolder, assignExpression);
          if (result != null) return result;
        }
        if (parent instanceof HaxeReferenceExpression referenceExpression) {
          ResultHolder resolve = tryTypeFromReference(resultHolder, referenceExpression);
          if (resolve != null) return resolve;
        }

        if (parent instanceof HaxeObjectLiteralElement literalElement) {
          ResultHolder result = tryTypeFromObjectLiteral(context, resolver, literalElement);
          if (result != null) return result;
        }

        if (parent instanceof HaxeArrayAccessExpression arrayAccessExpression) {
          tryUpdateTypeParamFromArrayAccess(context, resolver, arrayAccessExpression, classType, classResolver, type);
        }

        if (parent instanceof HaxeObjectLiteralElement literalElement) {
          tryUpdateTypePAramFromOjbectLiteral(context, resolver, literalElement, type);
        }
      }
    }
    return  resultHolder;
  }

  private static void tryUpdateTypePAramFromOjbectLiteral(HaxeExpressionEvaluatorContext context,
                                                          HaxeGenericResolver resolver,
                                                          HaxeObjectLiteralElement literalElement,
                                                          SpecificHaxeClassReference type) {
    HaxeObjectLiteral objectLiteral = PsiTreeUtil.getParentOfType(literalElement, HaxeObjectLiteral.class);
    if (objectLiteral == null) return;

    ResultHolder objectLiteralType = findObjectLiteralType(context, resolver, objectLiteral);

    if (objectLiteralType != null && !objectLiteralType.isUnknown()) {
      SpecificHaxeClassReference typeFromUsage = objectLiteralType.getClassType();
      if (typeFromUsage != null && typeFromUsage.getHaxeClassModel() != null) {
        HaxeBaseMemberModel objectLiteralElementAsMember = typeFromUsage.getHaxeClassModel()
          .getMember(literalElement.getName(), typeFromUsage.getGenericResolver());

        if (objectLiteralElementAsMember != null) {
          ResultHolder objectLiteralElementType = objectLiteralElementAsMember.getResultType(resolver);
          if (objectLiteralElementType.getClassType() != null) {
            @NotNull ResultHolder[] specifics = objectLiteralElementType.getClassType().getSpecifics();

            for (int i = 0; i < type.getSpecifics().length; i++) {
              if (type.getSpecifics()[i].isUnknown()) {
                type.getSpecifics()[i] = specifics[i];
              }
              else {
                ResultHolder unified = HaxeTypeUnifier.unify(specifics[i], type.getSpecifics()[i]);
                type.getSpecifics()[i] = unified;
              }
            }
          }
        }
      }
    }
  }

  private static void tryUpdateTypeParamFromArrayAccess(HaxeExpressionEvaluatorContext context,
                                                        HaxeGenericResolver resolver,
                                                        HaxeArrayAccessExpression arrayAccessExpression,
                                                        SpecificHaxeClassReference classType,
                                                        HaxeGenericResolver classResolver,
                                                        SpecificHaxeClassReference type) {
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
                ResultHolder specific = type.getSpecifics()[i];
                ResultHolder unified = specific.isTypeParameter() ? handle : HaxeTypeUnifier.unify(handle, specific);
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
                  ResultHolder specific = type.getSpecifics()[i];
                  ResultHolder unified = specific.isTypeParameter() ? handle : HaxeTypeUnifier.unify(handle, specific);
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

  private static @Nullable ResultHolder tryTypeFromObjectLiteral(HaxeExpressionEvaluatorContext context,
                                                  HaxeGenericResolver resolver,
                                                  HaxeObjectLiteralElement literalElement) {
    HaxeObjectLiteral objectLiteral = PsiTreeUtil.getParentOfType(literalElement, HaxeObjectLiteral.class);
    if (objectLiteral != null) {
      ResultHolder result = searchReferencesForTypeGuard.computePreventingRecursion(objectLiteral, false, () -> {
        ResultHolder objectLiteralType = findObjectLiteralType(context, resolver, objectLiteral);
        if (objectLiteralType != null && !objectLiteralType.isUnknown()) {
          SpecificHaxeClassReference literlClassType = objectLiteralType.getClassType();
          if (literlClassType != null) {
            HaxeClassModel classModel = literlClassType.getHaxeClassModel();
            if (classModel != null) {
              HaxeGenericResolver genericResolver = literlClassType.getGenericResolver();
              HaxeBaseMemberModel member = classModel.getMember(literalElement.getName(), genericResolver);
              if (member != null) {
                ResultHolder resultType = member.getResultType(genericResolver);
                if (resultType != null && !resultType.isUnknown()) {
                  return resultType;
                }
              }
            }
          }
        }
        return null;
      });
      if (result != null) return result;
    }
    return null;
  }

  private static @Nullable ResultHolder tryTypeFromReference(ResultHolder resultHolder, HaxeReferenceExpression referenceExpression) {
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
    return null;
  }

  private static @Nullable ResultHolder tryTypeFromAssignExpression(HaxeExpressionEvaluatorContext context,
                                                  HaxeGenericResolver resolver,
                                                  ResultHolder resultHolder,
                                                  HaxeAssignExpression assignExpression) {
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
    return null;
  }

  private static @Nullable ResultHolder tryTypeFromParameterFromCallExpression(ResultHolder resultHolder,
                                                  HaxeReferenceExpression referenceExpression,
                                                  PsiElement parent) {
    if (parent != null && parent.getParent() instanceof HaxeCallExpression callExpression) {
      if (callExpression.getExpression() instanceof HaxeReference callExpressionReference) {
        final PsiElement resolved = callExpressionReference.resolve();
        HaxeCallExpressionList list = callExpression.getExpressionList();
        // check if reference used as parameter
        ResultHolder paramType = findUsageAsParameterInFunctionCall(referenceExpression, callExpression, list, resolved);
        if (paramType != null) {
          // probably not the best solution, but the goal is to keep the original type and only update typeParameters
          ResultHolder unified = HaxeTypeUnifier.unify(resultHolder, paramType);
          if (!unified.isUnknown()) return unified;
        }
      }
    }
    return null;
  }




}
