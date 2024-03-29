package com.intellij.plugins.haxe.model.type;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.plugins.haxe.HaxeBundle;
import com.intellij.plugins.haxe.ide.annotator.HaxeStandardAnnotation;
import com.intellij.plugins.haxe.ide.annotator.semantics.HaxeCallExpressionUtil;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypeSets;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.lang.psi.impl.AbstractHaxeNamedComponent;
import com.intellij.plugins.haxe.lang.psi.impl.HaxeReferenceExpressionImpl;
import com.intellij.plugins.haxe.model.*;
import com.intellij.plugins.haxe.model.fixer.*;
import com.intellij.plugins.haxe.model.type.resolver.ResolveSource;
import com.intellij.plugins.haxe.util.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes.KUNTYPED;
import static com.intellij.plugins.haxe.lang.psi.impl.HaxeReferenceImpl.getLiteralClassName;
import static com.intellij.plugins.haxe.lang.psi.impl.HaxeReferenceImpl.tryToFindTypeFromCallExpression;
import static com.intellij.plugins.haxe.model.type.HaxeGenericResolverUtil.createInheritedClassResolver;
import static com.intellij.plugins.haxe.model.type.SpecificTypeReference.*;
import static com.intellij.plugins.haxe.model.type.HaxeExpressionEvaluator.*;

@CustomLog
public class HaxeExpressionEvaluatorHandlers {



  static ResultHolder handleTernaryExpression(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeTernaryExpression ternaryExpression) {
    HaxeExpression[] list = ternaryExpression.getExpressionList().toArray(new HaxeExpression[0]);
    SpecificTypeReference type1 = handle(list[1], context, resolver).getType();
    SpecificTypeReference type2 = handle(list[2], context, resolver).getType();
    return HaxeTypeUnifier.unify(type1, type2, ternaryExpression)
      .createHolder();
  }

  static ResultHolder handleBinaryExpression(HaxeExpressionEvaluatorContext context, HaxeGenericResolver resolver,
                                                     HaxeBinaryExpression expression) {
    if (
      (expression instanceof HaxeAdditiveExpression) ||
      (expression instanceof HaxeModuloExpression) ||
      (expression instanceof HaxeBitwiseExpression) ||
      (expression instanceof HaxeShiftExpression) ||
      (expression instanceof HaxeLogicAndExpression) ||
      (expression instanceof HaxeLogicOrExpression) ||
      (expression instanceof HaxeCompareExpression) ||
      (expression instanceof HaxeCoalescingExpression) ||
      (expression instanceof HaxeMultiplicativeExpression)
    ) {
      PsiElement[] children = expression.getChildren();
      String operatorText;
      if (children.length == 3) {
        operatorText = children[1].getText();
        SpecificTypeReference left = handle(children[0], context, resolver).getType();
        SpecificTypeReference right = handle(children[2], context, resolver).getType();
        left = resolveAnyTypeDefs(left);
        right = resolveAnyTypeDefs(right);
        return HaxeOperatorResolver.getBinaryOperatorResult(expression, left, right, operatorText, context).createHolder();
      }
      else {
        operatorText = getOperator(expression, HaxeTokenTypeSets.OPERATORS);
        SpecificTypeReference left = handle(children[0], context, resolver).getType();
        SpecificTypeReference right = handle(children[1], context, resolver).getType();
        left = resolveAnyTypeDefs(left);
        right = resolveAnyTypeDefs(right);
        return HaxeOperatorResolver.getBinaryOperatorResult(expression, left, right, operatorText, context).createHolder();
      }
    }
    return createUnknown(expression);
  }

  static ResultHolder handleTypeCheckExpr(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeTypeCheckExpr typeCheckExpr) {
    PsiElement[] children = typeCheckExpr.getChildren();
    if (children.length == 2) {
      SpecificTypeReference statementType = handle(children[0], context, resolver).getType();
      SpecificTypeReference assertedType = SpecificTypeReference.getUnknown(children[1]);
      if (children[1] instanceof HaxeTypeOrAnonymous) {
        HaxeTypeOrAnonymous toa = typeCheckExpr.getTypeOrAnonymous();
        if (toa != null ) {
          assertedType = HaxeTypeResolver.getTypeFromTypeOrAnonymous(toa).getType();
        }
      }
      // When we have proper unification (not failing to dynamic), then we should be checking if the
      // values unify.
      //SpecificTypeReference unified = HaxeTypeUnifier.unify(statementType, assertedType, element);
      //if (!unified.canAssign(statementType)) {
      if (!assertedType.canAssign(statementType)) {
        context.addError(typeCheckExpr, "Statement of type '" + statementType.getElementContext().getText() + "' does not unify with asserted type '" + assertedType.getElementContext().getText() + ".'");
        // TODO: Develop some fixers.
        // annotation.registerFix(new HaxeCreateLocalVariableFixer(accessName, element));
      }

      return statementType.createHolder();
    }
    return createUnknown(typeCheckExpr);
  }

  static ResultHolder handleGuard(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeGuard haxeGuard) {
    HaxeExpression guardExpression = haxeGuard.getExpression();
    SpecificTypeReference expr = handle(guardExpression, context, resolver).getType();
    if (!SpecificTypeReference.getBool(haxeGuard).canAssign(expr)) {
      context.addError(
        guardExpression,
        "If expr " + expr + " should be bool",
        new HaxeCastFixer(guardExpression, expr, SpecificHaxeClassReference.getBool(haxeGuard))
      );
    }

    if (expr.isConstant()) {
      context.addWarning(guardExpression, "If expression constant");
    }
    return expr.createHolder();
  }

  static ResultHolder handleReferenceExpression( HaxeExpressionEvaluatorContext context, HaxeGenericResolver resolver,
                                                         HaxeReferenceExpression element) {
    PsiElement[] children = element.getChildren();
    ResultHolder typeHolder = children.length == 0 ? SpecificTypeReference.getUnknown(element).createHolder() : handle(children[0], context,
                                                                                                                       resolver);
    boolean resolved = !typeHolder.getType().isUnknown();
    for (int n = 1; n < children.length; n++) {
      PsiElement child = children[n];
      SpecificTypeReference typeReference = typeHolder.getType();
      if (typeReference.isString() && typeReference.isConstant() && child.textMatches("code")) {
        String str = (String)typeReference.getConstant();
        typeHolder = SpecificTypeReference.getInt(element, (str != null && !str.isEmpty()) ? str.charAt(0) : -1).createHolder();
        if (str == null || str.length() != 1) {
          context.addError(element, "String must be a single UTF8 char");
        }
      } else {

        if (typeReference.isUnknown()) continue;

        // unwrap Null
        //TODO make util for unwrap/get underlying type of Null<T>? (or fix resolver ?)
        if (typeReference.isNullType()) {
          typeHolder = typeHolder.getClassType().getSpecifics()[0];
        }

        // TODO: Yo! Eric!!  This needs to get fixed.  The resolver is coming back as Dynamic, when it should be String

        // Grab the types out of the original resolver (so we don't modify it), and overwrite them
        // (by adding) with the class' resolver. That way, we get the combination of the two, and
        // any parameters provided/set in the class will override any from the calling context.
        HaxeGenericResolver localResolver = new HaxeGenericResolver();
        localResolver.addAll(resolver);

        SpecificHaxeClassReference classType = typeHolder.getClassType();
        if (null != classType) {
          localResolver.addAll(classType.getGenericResolver());
        }
        String accessName = child.getText();
        ResultHolder access = typeHolder.getType().access(accessName, context, localResolver);
        if (access == null) {
          resolved = false;

          if (children.length == 1) {
            context.addError(children[n], "Can't resolve '" + accessName + "' in " + typeHolder.getType(),
                             new HaxeCreateLocalVariableFixer(accessName, element));
          }
          else {
            context.addError(children[n], "Can't resolve '" + accessName + "' in " + typeHolder.getType(),
                             new HaxeCreateMethodFixer(accessName, element),
                             new HaxeCreateFieldFixer(accessName, element));
          }

        }
        if (access != null) typeHolder = access;
      }
    }

    // If we aren't walking the body, then we might not have seen the reference.  In that
    // case, the type is still unknown.  Let's see if the resolver can figure it out.
    if (!resolved) {
      PsiReference reference = element.getReference();
      if (reference != null) {
        PsiElement subelement = reference.resolve();
        if (subelement != element) {
          if (subelement instanceof HaxeReferenceExpression referenceExpression) {
            PsiElement resolve = referenceExpression.resolve();
            if (resolve != element)
              typeHolder = handleWithRecursionGuard(resolve, context, resolver);
          }
          if (subelement instanceof HaxeClass haxeClass) {

            HaxeClassModel model = haxeClass.getModel();
            HaxeClassReference classReference = new HaxeClassReference(model, element);

            if (haxeClass.isGeneric()) {
              @NotNull ResultHolder[] specifics = resolver.getSpecificsFor(classReference);
              typeHolder = SpecificHaxeClassReference.withGenerics(classReference, specifics).createHolder();
            }
            else {
              typeHolder = SpecificHaxeClassReference.withoutGenerics(classReference).createHolder();
            }

            // check if pure Class Reference
            if (reference instanceof HaxeReferenceExpressionImpl expression) {
              if (expression.isPureClassReferenceOf(haxeClass.getName())) {
                // wrap in Class<> or Enum<>
                SpecificHaxeClassReference originalClass = SpecificHaxeClassReference.withoutGenerics(model.getReference());
                SpecificHaxeClassReference wrappedClass =
                  SpecificHaxeClassReference.getStdClass(haxeClass.isEnum() ? ENUM : CLASS, element,
                                                         new ResultHolder[]{new ResultHolder(originalClass)});
                typeHolder = wrappedClass.createHolder();
              }
            }
          }
          else if (subelement instanceof HaxeFieldDeclaration fieldDeclaration) {
            HaxeVarInit init = fieldDeclaration.getVarInit();
            if (init != null) {
              HaxeExpression initExpression = init.getExpression();
              HaxeGenericResolver initResolver = HaxeGenericResolverUtil.generateResolverFromScopeParents(initExpression);
              typeHolder = HaxeTypeResolver.getFieldOrMethodReturnType((AbstractHaxeNamedComponent)subelement, initResolver);
            }
            else {
              HaxeTypeTag tag = fieldDeclaration.getTypeTag();
              if (tag != null) {
                typeHolder = HaxeTypeResolver.getTypeFromTypeTag(tag, fieldDeclaration);
                HaxeClass  usedIn = PsiTreeUtil.getParentOfType((PsiElement)reference, HaxeClass.class);
                HaxeClass containingClass = (HaxeClass)fieldDeclaration.getContainingClass();
                if (usedIn != null && containingClass != null && usedIn != containingClass && containingClass.isGeneric()) {
                  HaxeGenericResolver inheritedClassResolver = createInheritedClassResolver(containingClass, usedIn, resolver);
                  HaxeGenericResolver resolverForContainingClass = inheritedClassResolver.getSpecialization(null).toGenericResolver(containingClass);
                  ResultHolder resolve = resolverForContainingClass.resolve(typeHolder);
                  if (!resolve.isUnknown())typeHolder = resolve;
                }

              }
            }
          }
          else if (subelement instanceof HaxeMethodDeclaration methodDeclaration) {
            boolean isFromCallExpression = reference instanceof  HaxeCallExpression;
            SpecificFunctionReference type = methodDeclaration.getModel().getFunctionType(isFromCallExpression ? resolver : resolver.withoutAssignHint());
            if (!isFromCallExpression) {
              //  expression is referring to the method not calling it.
              //  assign hint should be used for substituting parameters instead of being used as return type
              type = resolver.substituteTypeParamsWithAssignHintTypes(type);
            }
            typeHolder = type.createHolder();
          }

          else if (subelement instanceof HaxeForStatement forStatement) {
            // key-value iterator is not relevant here as it will be resolved to HaxeIteratorkey  or HaxeIteratorValue
            final HaxeComponentName name = forStatement.getComponentName();
            // if element text matches  for loops  iterator  i guess we can consider it a match?
            if (name != null && element.textMatches(name)) {
              final HaxeIterable iterable = forStatement.getIterable();
              if (iterable != null) {
                ResultHolder iterator = handle(iterable, context, resolver);
                if (iterator.isClassType()) {
                  iterator = iterator.getClassType().fullyResolveTypeDefAndUnwrapNullTypeReference().createHolder();
                }
                // get specific from iterator as thats the type for our variable
                ResultHolder[] specifics = iterator.getClassType().getSpecifics();
                if (specifics.length > 0) {
                  typeHolder = specifics[0];
                }
              }
            }
          }
          else if (subelement instanceof HaxeIteratorkey || subelement instanceof HaxeIteratorValue) {
            typeHolder = findIteratorType(element, subelement);
          }

          else if (subelement instanceof HaxeSwitchCaseCaptureVar caseCaptureVar) {
            HaxeSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(caseCaptureVar, HaxeSwitchStatement.class);
            if (switchStatement.getExpression() != null) {
              typeHolder = handle(switchStatement.getExpression(), context, resolver);
            }
          }

          else if (subelement instanceof HaxeSwitchCaseExpr caseExpr) {
            HaxeSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(caseExpr, HaxeSwitchStatement.class);
            if (switchStatement.getExpression() != null) {
              typeHolder = handle(switchStatement.getExpression(), context, resolver);
            }
          }

          else {
            // attempt to resolve sub-element using default handle logic
            if (!(subelement instanceof PsiPackage)) {
              typeHolder = handleWithRecursionGuard(subelement, context, resolver);

            }
            if (typeHolder == null) {
              typeHolder = SpecificTypeReference.getUnknown(element).createHolder();
            }
          }
        }
      }
    }

    if (typeHolder != null) {
      // overriding  context  to avoid problems with canAssign thinking this is a "Pure" class reference
      return typeHolder.withElementContext(element);
    }else {
     return SpecificTypeReference.getDynamic(element).createHolder();
    }
  }

  static ResultHolder handleRegularExpressionLiteral(HaxeRegularExpressionLiteral regexLiteral) {
    HaxeClass regexClass = HaxeResolveUtil.findClassByQName(getLiteralClassName(HaxeTokenTypes.REG_EXP), regexLiteral);
    if (regexClass != null) {
      return SpecificHaxeClassReference.withoutGenerics(new HaxeClassReference(regexClass.getModel(), regexLiteral)).createHolder();
    }
    return createUnknown(regexLiteral);
  }

  static ResultHolder handleStringLiteralExpression(PsiElement element) {
    // @TODO: check if it has string interpolation inside, in that case text is not constant
    String constant = HaxeStringUtil.unescapeString(element.getText());
    return SpecificHaxeClassReference.primitive("String", element, constant).createHolder();
  }

  static ResultHolder handleSwitchCaseCaptureVar(HaxeGenericResolver resolver, HaxeSwitchCaseCaptureVar captureVar) {
    HaxeResolveResult result = HaxeResolveUtil.getHaxeClassResolveResult(captureVar, resolver.getSpecialization(null));
    if (result.isHaxeClass()) {
      return result.getSpecificClassReference(result.getHaxeClass(), resolver).createHolder();
    }else if (result.isFunctionType()) {
      return result.getSpecificClassReference(result.getFunctionType(), resolver).createHolder();
    }
    return createUnknown(captureVar);
  }

  static ResultHolder handleFieldDeclaration(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeFieldDeclaration declaration) {
    HaxeTypeTag typeTag = declaration.getTypeTag();

    if (typeTag!= null) {
      return HaxeTypeResolver.getTypeFromTypeTag(typeTag, declaration);
    }else {
      HaxeVarInit init = declaration.getVarInit();
      if (init != null) {
        return handle(init.getExpression(), context, resolver);
      }
    }
    return createUnknown(declaration);
  }

  static ResultHolder handleSpreadExpression(HaxeGenericResolver resolver, HaxeSpreadExpression spreadExpression) {
    HaxeExpression expression = spreadExpression.getExpression();
    // we treat restParameters as arrays, so we need to "unwrap" the array to get the correct type.
    // (currently restParameters and Arrays are the only types you can spread afaik. and only in method calls)
    if (expression instanceof HaxeReferenceExpression referenceExpression) {
      ResultHolder type = HaxeTypeResolver.getPsiElementType(referenceExpression, resolver);
      if (type.isClassType()) {
        ResultHolder[] specifics = type.getClassType().getSpecifics();
        if (specifics.length == 1) {
          return specifics[0];
        }
      }
    }
    else if (expression instanceof HaxeArrayLiteral arrayLiteral) {
      HaxeResolveResult result = arrayLiteral.resolveHaxeClass();
      SpecificHaxeClassReference reference = result.getSpecificClassReference(expression, resolver);
      @NotNull ResultHolder[] specifics = reference.getSpecifics();
      if (specifics.length == 1) {
        return specifics[0];
      }
    }
    return createUnknown(spreadExpression);
  }


  static ResultHolder handleParameter(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeParameter parameter) {
    HaxeTypeTag typeTag = parameter.getTypeTag();
    if (typeTag != null) {
      ResultHolder typeFromTypeTag = HaxeTypeResolver.getTypeFromTypeTag(typeTag, parameter);
      ResultHolder resolve = resolver.resolve(typeFromTypeTag);
      if (!resolve.isUnknown()) return resolve;
      return typeFromTypeTag;
    }

    HaxeVarInit init = parameter.getVarInit();
    if (init != null) {
      ResultHolder holder = handle(init, context, resolver);
      if (!holder.isUnknown()) {
        return holder;
      }
    }else {
      if (parameter.getParent().getParent() instanceof HaxeFunctionLiteral functionLiteral) {
        ResultHolder holder = tryToFindTypeFromCallExpression(functionLiteral, parameter);
        if (holder!= null && !holder.isUnknown()) {
          ResultHolder resolve = resolver.resolve(holder);
          return resolve != null && !resolve.isUnknown() ? resolve : holder;
        }else {
          return createUnknown(parameter);
        }
      }else {
        HaxeMethod method = PsiTreeUtil.getParentOfType(parameter, HaxeMethod.class);
        ResultHolder holder = searchReferencesForType(parameter.getComponentName(), context, resolver, method.getBody());
        if (holder!= null && !holder.isUnknown()) {
          return holder;
        }
      }
    }
    return createUnknown(parameter);
  }

  static ResultHolder handleNewExpression(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeNewExpression expression) {
    HaxeType type = expression.getType();
    if (type != null) {
      if (isMacroVariable(type.getReferenceExpression().getIdentifier())){
        return SpecificTypeReference.getDynamic(expression).createHolder();
      }
      ResultHolder hint = resolver.getAssignHint();
      ResultHolder typeHolder = HaxeTypeResolver.getTypeFromType(type, resolver);
      if (hint != null && hint.isClassType()) {
        HaxeGenericResolver localResolver = new HaxeGenericResolver();
        HaxeGenericResolver hintsResolver = hint.getClassType().getGenericResolver();
        localResolver.addAll(hintsResolver);
        ResultHolder resolvedWithHint = localResolver.resolve(typeHolder);
        if (resolvedWithHint != null && !resolvedWithHint.isUnknown()) typeHolder = resolvedWithHint;
      }

      if (!typeHolder.isUnknown() && typeHolder.getClassType() != null) {
        SpecificHaxeClassReference classReference = typeHolder.getClassType();
        HaxeClassModel classModel = classReference.getHaxeClassModel();
        HaxeGenericResolver classResolver = classReference.getGenericResolver();
        if (classModel != null) {
          HaxeMethodModel constructor = classModel.getConstructor(classResolver);
          if (constructor != null) {
            HaxeMethod method = constructor.getMethod();
            HaxeMethodModel methodModel = method.getModel();
            if (methodModel.getGenericParams().isEmpty()) {
              HaxeCallExpressionUtil.CallExpressionValidation validation = HaxeCallExpressionUtil.checkConstructor(expression);
              HaxeGenericResolver resolverFromCallExpression = validation.getResolver();

              if (resolverFromCallExpression != null) {
                ResultHolder resolve = resolverFromCallExpression.resolve(typeHolder);
                if (!resolve.isUnknown()) typeHolder = resolve;
              }
            }
          }
        }
      }
      // if new expression is missing typeParameters try to resolve from usage
      if (type.getTypeParam() == null && typeHolder.getClassType() != null && typeHolder.getClassType().getSpecifics().length > 0) {
        HaxePsiField fieldDeclaration = PsiTreeUtil.getParentOfType(expression, HaxePsiField.class);
        if (fieldDeclaration != null && fieldDeclaration.getTypeTag() == null) {
          SpecificHaxeClassReference classType = typeHolder.getClassType();
          // if class does not have any  generics there  no need to search for references
          if (classType != null  && classType.getSpecifics().length > 0) {
            ResultHolder searchResult = searchReferencesForTypeParameters(fieldDeclaration, context, resolver, typeHolder);
            if (!searchResult.isUnknown()) {
              typeHolder = searchResult;
            }
          }
        }
      }
      if (typeHolder.getType() instanceof SpecificHaxeClassReference classReference) {
        final HaxeClassModel clazz = classReference.getHaxeClassModel();
        if (clazz != null) {
          HaxeMethodModel constructor = clazz.getConstructor(resolver);
          if (constructor == null) {
            context.addError(expression, "Class " + clazz.getName() + " doesn't have a constructor", new HaxeFixer("Create constructor") {
              @Override
              public void run() {
                // @TODO: Check arguments
                clazz.addMethod("new");
              }
            });
          } else {
            //checkParameters(element, constructor, expression.getExpressionList(), context, resolver);
          }
        }
      }
      return typeHolder.duplicate();
    }
    return createUnknown(expression);
  }


  static ResultHolder handleEnumExtractedValue(HaxeEnumExtractedValue extractedValue) {
    HaxeEnumArgumentExtractor extractor = PsiTreeUtil.getParentOfType(extractedValue, HaxeEnumArgumentExtractor.class);

    // TODO mlo should probably move the haxe model logic to the PSI implementation so we can cache it
    HaxeEnumExtractorModel extractorModel =  new HaxeEnumExtractorModel(extractor);
    HaxeEnumValueModel enumValueModel =  extractorModel.getEnumValueModel();
    if (enumValueModel != null) {
      int index = extractorModel.findExtractValueIndex(extractedValue);
      HaxeGenericResolver extractorResolver =  extractorModel.getGenericResolver();
      ResultHolder parameterType = enumValueModel.getParameterType(index, extractorResolver);
      if (parameterType != null) return parameterType;
    }
    return createUnknown(extractedValue);
  }

  static ResultHolder handleForStatement(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeForStatement forStatement) {
    final HaxeExpression forStatementExpression = forStatement.getExpression();
    final HaxeKeyValueIterator keyValueIterator = forStatement.getKeyValueIterator();
    final HaxeComponentName name = forStatement.getComponentName();
    final HaxeIterable iterable = forStatement.getIterable();
    final PsiElement body = forStatement.getLastChild();
    context.beginScope();

    if(context.getScope().deepSearchForReturnValues) handle(body, context, resolver);

    try {
      final SpecificTypeReference iterableValue = handle(iterable, context, resolver).getType();
      ResultHolder iteratorResult = iterableValue.getIterableElementType(resolver);
      SpecificTypeReference type = iteratorResult != null ? iteratorResult.getType() : null;
      //TODO: HACK?
      // String class in in standard lib is  currently missing iterator methods
      // this is a workaround  so we can iterate on chars in string.
      if (type == null && iterableValue.isString()) {
        type = iterableValue;
      }
      if (type != null) {
        if (forStatementExpression != null) {
          ResultHolder handle = handle(forStatementExpression, context, resolver);
          if (handle.getType() != null) {
            return handle.getType().createHolder();
          }
        }
        if (type.isTypeParameter()) {
          if (iterable.getExpression() instanceof  HaxeReference reference) {
            HaxeResolveResult result = reference.resolveHaxeClass();
            HaxeGenericResolver classResolver = result.getGenericResolver();
            ResultHolder holder = type.createHolder();
            ResultHolder resolved = classResolver.resolve(holder);
            if (!resolved.isUnknown()) {
              return resolved;
            }
          }
        }
        return new ResultHolder(type);
      }
      if ( type != null) {
        if (iterableValue.isConstant()) {
          if (iterableValue.getConstant() instanceof HaxeRange constant) {
            type = type.withRangeConstraint(constant);
          }
        }
        if (name != null) {
          context.setLocal(name.getText(), new ResultHolder(type));
        } else if (keyValueIterator != null) {
          context.setLocal(keyValueIterator.getIteratorkey().getComponentName().getText(), new ResultHolder(type));
          context.setLocal(keyValueIterator.getIteratorValue().getComponentName().getText(), new ResultHolder(type));
        }
        return handle(body, context, resolver);
      }
    }
    finally {
      context.endScope();
    }
    return createUnknown(forStatement);
  }

  static ResultHolder createUnknown(PsiElement element) {
    return SpecificHaxeClassReference.getUnknown(element).createHolder();
  }

  static ResultHolder handlePrefixExpression(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxePrefixExpression prefixExpression) {
    HaxeExpression expression = prefixExpression.getExpression();
    ResultHolder typeHolder = handle(expression, context, resolver);
    SpecificTypeReference type = typeHolder.getType();
    if (type.getConstant() != null) {
      String operatorText = getOperator(prefixExpression, HaxeTokenTypeSets.OPERATORS);
      if (operatorText != "") {
        return type.withConstantValue(HaxeTypeUtils.applyUnaryOperator(type.getConstant(), operatorText)).createHolder();
      }
    }
    return typeHolder;
  }

  static ResultHolder handleIfStatement(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeIfStatement ifStatement) {
    SpecificTypeReference guardExpr = handle(ifStatement.getGuard(), context, resolver).getType();
    HaxeGuardedStatement guardedStatement = ifStatement.getGuardedStatement();
    HaxeElseStatement elseStatement = ifStatement.getElseStatement();

    PsiElement eTrue = UsefulPsiTreeUtil.getFirstChildSkipWhiteSpacesAndComments(guardedStatement);
    PsiElement eFalse = UsefulPsiTreeUtil.getFirstChildSkipWhiteSpacesAndComments(elseStatement);

    SpecificTypeReference tTrue = null;
    SpecificTypeReference tFalse = null;
    if (eTrue != null) tTrue = handle(eTrue, context, resolver).getType();
    if (eFalse != null) tFalse = handle(eFalse, context, resolver).getType();
    if (guardExpr.isConstant()) {
      if (guardExpr.getConstantAsBool()) {
        if (tFalse != null) {
          context.addUnreachable(eFalse);
        }
      } else {
        if (tTrue != null) {
          context.addUnreachable(eTrue);
        }
      }
    }

    // No 'else' clause means the if results in a Void type.
    if (null == tFalse) tFalse = SpecificHaxeClassReference.getVoid(ifStatement);

    return HaxeTypeUnifier.unify(tTrue, tFalse, ifStatement).createHolder();
  }

  static ResultHolder handleFunctionLiteral(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeFunctionLiteral function) {
    HaxeParameterList params = function.getParameterList(); // TODO mlo: get expected type to use if signature/parameters are without types
    if (params == null) {
      return SpecificHaxeClassReference.getInvalid(function).createHolder();
    }
    LinkedList<SpecificFunctionReference.Argument> arguments = new LinkedList<>();
    ResultHolder returnType = null;
    context.beginScope();
    try {
      if (params instanceof HaxeOpenParameterList openParamList) {
        // Arrow function with a single, unparenthesized, parameter.

        // TODO: Infer the type from first usage in the function body.
        ResultHolder argumentType = SpecificTypeReference.getUnknown(function).createHolder();
        String argumentName = openParamList.getComponentName().getName();
        context.setLocal(argumentName, argumentType);
        // TODO check if rest param?
        arguments.add(new SpecificFunctionReference.Argument(0, false, false, argumentType, argumentName));
      } else {
        List<HaxeParameter> list = params.getParameterList();
        for (int i = 0; i < list.size(); i++) {
          HaxeParameter parameter = list.get(i);
          //ResultHolder argumentType = HaxeTypeResolver.getTypeFromTypeTag(parameter.getTypeTag(), function);
          ResultHolder argumentType = handleWithRecursionGuard(parameter, context, resolver);
          if (argumentType == null) argumentType = SpecificTypeReference.getUnknown(parameter).createHolder();
          context.setLocal(parameter.getName(), argumentType);
          // TODO check if rest param?
          boolean optional = parameter.getOptionalMark() != null || parameter.getVarInit() != null;
          arguments.add(new SpecificFunctionReference.Argument(i, optional, false, argumentType, parameter.getName()));
        } // TODO: Add Void if list.size() == 0
      }
      context.addLambda(context.createChild(function.getLastChild()));
      HaxeTypeTag tag = (function.getTypeTag());
      if (null != tag) {
        returnType = HaxeTypeResolver.getTypeFromTypeTag(tag, function);
      } else {
        // If there was no type tag on the function, then we try to infer the value:
        // If there is a block to this method, then return the type of the block.  (See PsiBlockStatement above.)
        // If there is not a block, but there is an expression, then return the type of that expression.
        // If there is not a block, but there is a statement, then return the type of that statement.
        HaxeBlockStatement block = function.getBlockStatement();
        if (null != block) {
          //// note : as we enter a block we are leaving the scope where we could use the assignHint directly
          HaxeExpressionEvaluatorContext functionBlockContext = new HaxeExpressionEvaluatorContext(function);
          ResultHolder handled = handle(block, functionBlockContext, resolver.withoutAssignHint());

          if (!functionBlockContext.getReturnValues().isEmpty()) {
            returnType = HaxeTypeUnifier.unifyHolders(functionBlockContext.getReturnValues(), function);
          }else  if (block.getExpressionList().size() == 1){
            returnType = handled;
          }else {
            returnType = SpecificHaxeClassReference.getVoid(function).createHolder();
          }
        } else if (null != function.getExpression()) {
          returnType = handle(function.getExpression(), context, resolver);
        } else {
          // Only one of these can be non-null at a time.
          PsiElement possibleStatements[] = {function.getDoWhileStatement(), function.getForStatement(), function.getIfStatement(),
            function.getReturnStatement(), function.getThrowStatement(), function.getWhileStatement()};
          for (PsiElement statement : possibleStatements) {
            if (null != statement) {
              returnType = handle(statement, context, resolver);
              break;
            }
          }
        }
      }
    }
    finally {
      context.endScope();
    }
    return new SpecificFunctionReference(arguments, returnType, null, function, function).createHolder();
  }

  static ResultHolder handleArrayAccessExpression(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeArrayAccessExpression arrayAccessExpression) {
    final List<HaxeExpression> list = arrayAccessExpression.getExpressionList();
    if (list.size() >= 2) {
      final SpecificTypeReference left = handle(list.get(0), context, resolver).getType();
      final SpecificTypeReference right = handle(list.get(1), context, resolver).getType();
      if (left.isArray()) {
        Object constant = null;
        if (left.isConstant()) {
          if (left.getConstant() instanceof List array) {
            //List array = (List)left.getConstant();
            // TODO got class cast exception here due to constant being "HaxeAbstractClassDeclarationImpl
            //  possible expression causing issue: ("this[x + 1]  in  abstractType(Array<Float>)" ?)

            final HaxeRange constraint = right.getRangeConstraint();
            HaxeRange arrayBounds = new HaxeRange(0, array.size());
            if (right.isConstant()) {
              final int index = HaxeTypeUtils.getIntValue(right.getConstant());
              if (arrayBounds.contains(index)) {
                constant = array.get(index);
              }
              else {
                context.addWarning(arrayAccessExpression, "Out of bounds " + index + " not inside " + arrayBounds);
              }
            }
            else if (constraint != null) {
              if (!arrayBounds.contains(constraint)) {
                context.addWarning(arrayAccessExpression, "Out of bounds " + constraint + " not inside " + arrayBounds);
              }
            }
          }
        }
        ResultHolder arrayType = left.getArrayElementType().getType().withConstantValue(constant).createHolder();
        ResultHolder resolved = resolver.resolve(arrayType);
        return resolved.isUnknown() ? arrayType : resolved;
      }
      //if not native array, look up ArrayAccessGetter method and use result
      if(left instanceof SpecificHaxeClassReference classReference) {
        // make sure we fully resolve and unwrap any nulls and typedefs before searching for accessors
        SpecificTypeReference reference = classReference.fullyResolveTypeDefAndUnwrapNullTypeReference();
        if (reference instanceof SpecificHaxeClassReference fullyResolved){
          classReference = fullyResolved;
        }

        HaxeClass haxeClass = classReference.getHaxeClass();
        if (haxeClass != null) {
          HaxeNamedComponent getter = haxeClass.findArrayAccessGetter(resolver);
          if (getter instanceof HaxeMethodDeclaration methodDeclaration) {
            HaxeMethodModel methodModel = methodDeclaration.getModel();
            HaxeGenericResolver localResolver = classReference.getGenericResolver();
            HaxeGenericResolver methodResolver = methodModel.getGenericResolver(localResolver);
            localResolver.addAll(methodResolver);// apply constraints from methodSignature (if any)
            ResultHolder returnType = methodModel.getReturnType(localResolver);
            if (returnType.getType().isNullType()) localResolver.resolve(returnType);
            if (returnType != null) return returnType;
          }
          // hack to work around external ArrayAccess interface, interface that has no methods but tells compiler that implementing class has array access
          else if (getter instanceof HaxeExternInterfaceDeclaration interfaceDeclaration) {
            HaxeGenericSpecialization leftResolver = classReference.getGenericResolver().getSpecialization(getter);
            HaxeResolveResult resolvedInterface = HaxeResolveUtil.getHaxeClassResolveResult(interfaceDeclaration, leftResolver);
            ResultHolder type = resolvedInterface.getGenericResolver().resolve("T");
            if (type != null) return type;
          }
        }
      }
    }
    return createUnknown(arrayAccessExpression);
  }

  static ResultHolder handleIteratorExpression(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeIteratorExpression iteratorExpression) {
    final List<HaxeExpression> list = iteratorExpression.getExpressionList();
    if (list.size() >= 2) {
      final SpecificTypeReference left = handle(list.get(0), context, resolver).getType();
      final SpecificTypeReference right = handle(list.get(1), context, resolver).getType();
      Object constant = null;
      if (left.isConstant() && right.isConstant()) {
        constant = new HaxeRange(
          HaxeTypeUtils.getIntValue(left.getConstant()),
          HaxeTypeUtils.getIntValue(right.getConstant())
        );
      }
      return SpecificHaxeClassReference.getIterator(SpecificHaxeClassReference.getInt(iteratorExpression)).withConstantValue(constant)
        .createHolder();
    }
    return createUnknown(iteratorExpression);
  }

  static ResultHolder handleSuperExpression(HaxeExpressionEvaluatorContext context, HaxeGenericResolver resolver,
                                                    HaxeSuperExpression superExpression) {
    /*
    log.debug("-------------------------");
    final HaxeExpressionList list = HaxePsiUtils.getChildWithText(element, HaxeExpressionList.class);
    log.debug(element);
    log.debug(list);
    final List<HaxeExpression> parameters = (list != null) ? list.getExpressionList() : Collections.<HaxeExpression>emptyList();
    final HaxeMethodModel method = HaxeJavaUtil.cast(HaxeMethodModel.fromPsi(element), HaxeMethodModel.class);
    if (method == null) {
      context.addError(element, "Not in a method");
    }
    if (method != null) {
      final HaxeMethodModel parentMethod = method.getParentMethod();
      if (parentMethod == null) {
        context.addError(element, "Calling super without parent constructor");
      } else {
        log.debug(element);
        log.debug(parentMethod.getFunctionType());
        log.debug(parameters);
        checkParameters(element, parentMethod.getFunctionType(), parameters, context);
        //log.debug(method);
        //log.debug(parentMethod);
      }
    }
    return SpecificHaxeClassReference.getVoid(element);
    */
    final HaxeMethodModel method = HaxeJavaUtil.cast(HaxeBaseMemberModel.fromPsi(superExpression), HaxeMethodModel.class);
    final HaxeMethodModel parentMethod = (method != null) ? method.getParentMethod(resolver) : null;
    if (parentMethod != null) {
      return parentMethod.getFunctionType(resolver).createHolder();
    }
    context.addError(superExpression, "Calling super without parent constructor");
    return createUnknown(superExpression);
  }

  static ResultHolder handleValueExpression(HaxeExpressionEvaluatorContext context,
                                                    HaxeGenericResolver resolver,
                                                    HaxeValueExpression valueExpression) {
    if (valueExpression.getSwitchStatement() != null){
      return handle(valueExpression.getSwitchStatement(), context, resolver);
    }
    if (valueExpression.getIfStatement() != null){
      return handle(valueExpression.getIfStatement(), context, resolver);
    }
    if (valueExpression.getTryStatement() != null){
      return handle(valueExpression.getTryStatement(), context, resolver);
    }
    if (valueExpression.getVarInit() != null){
      return handle(valueExpression.getVarInit(), context, resolver);
    }
    if (valueExpression.getExpression() != null){
      return handle(valueExpression.getExpression(), context, resolver);
    }
    return createUnknown(valueExpression);
  }

  static ResultHolder handlePrimitives(PsiElement element, HaxePsiToken psiToken) {
    IElementType type = psiToken.getTokenType();

    if (type == HaxeTokenTypes.LITINT || type == HaxeTokenTypes.LITHEX || type == HaxeTokenTypes.LITOCT) {
      return SpecificHaxeClassReference.primitive("Int", element, Long.decode(element.getText())).createHolder();
    } else if (type == HaxeTokenTypes.LITFLOAT) {
      Float value = Float.valueOf(element.getText());
      return SpecificHaxeClassReference.primitive("Float", element, Double.parseDouble(element.getText()))
        .withConstantValue(value)
        .createHolder();
    } else if (type == HaxeTokenTypes.KFALSE || type == HaxeTokenTypes.KTRUE) {
      Boolean value = type == HaxeTokenTypes.KTRUE;
      return SpecificHaxeClassReference.primitive("Bool", element, type == HaxeTokenTypes.KTRUE)
        .withConstantValue(value)
        .createHolder();
    } else if (type == HaxeTokenTypes.KNULL) {
      return SpecificHaxeClassReference.primitive("Dynamic", element, HaxeNull.instance).createHolder();
    } else {
      if(log.isDebugEnabled())log.debug("Unhandled token type: " + type);
      return SpecificHaxeClassReference.getDynamic(element).createHolder();
    }
  }

  static ResultHolder handleArrayLiteral(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeArrayLiteral arrayLiteral) {
    HaxeExpressionList list = arrayLiteral.getExpressionList();

    // Check if it's a comprehension.
    if (list != null) {
      final List<HaxeExpression> expressionList = list.getExpressionList();
      if (expressionList.isEmpty()) {
        final PsiElement child = list.getFirstChild();
        if ((child instanceof HaxeForStatement) || (child instanceof HaxeWhileStatement)) {
          return SpecificTypeReference.createArray(handle(child, context, resolver), arrayLiteral).createHolder();
        }
      }
    }

    ArrayList<SpecificTypeReference> references = new ArrayList<SpecificTypeReference>();
    ArrayList<Object> constants = new ArrayList<Object>();
    boolean allConstants = true;
    if (list != null) {
      for (HaxeExpression expression : list.getExpressionList()) {
        // dropping AssignHint as we are in an array so field type will include the array part.
        SpecificTypeReference type = handle(expression, context, resolver.withoutAssignHint()).getType();
        if (!type.isConstant()) {
          allConstants = false;
        } else {
          constants.add(type.getConstant());
        }
        // Convert enum Value types to Enum class  (you cant have an Array of EnumValue types)
        if (type instanceof  SpecificEnumValueReference enumValueReference) {
          type = enumValueReference.getEnumClass();
        }
        references.add(type);
      }
    }
    // an attempt at suggesting what to unify  types into (useful for when typeTag is an anonymous structure as those would never be used in normal unify)
    SpecificTypeReference suggestedType = null;
    ResultHolder  typeTagType = findInitTypeForUnify(arrayLiteral);
    if (typeTagType!= null) {
      // we expect Array<T> or collection type with type parameter (might not work properly if type is implicit cast)
      if (typeTagType.getClassType() != null) {
        @NotNull ResultHolder[] specifics = typeTagType.getClassType().getSpecifics();
        if (specifics.length == 1) {
          suggestedType = specifics[0].getType();
        }
      }
    }
    // empty expression with type tag (var x:Array<T> = []), no need to look for usage, use typetag
    if (references.isEmpty() && suggestedType != null && !suggestedType.isUnknown()) {
      return typeTagType;
    } else {
      ResultHolder elementTypeHolder = references.isEmpty()
                                       ? SpecificTypeReference.getUnknown(arrayLiteral).createHolder()
                                       : HaxeTypeUnifier.unify(references, arrayLiteral, suggestedType).withoutConstantValue().createHolder();

      SpecificTypeReference result = SpecificHaxeClassReference.createArray(elementTypeHolder, arrayLiteral);
      if (allConstants) result = result.withConstantValue(constants);
      ResultHolder holder = result.createHolder();

      // try to resolve typeParameter when we got empty literal array with declaration without typeTag
      if (elementTypeHolder.isUnknown()) {
        // note to avoid recursive loop we only  do this check if its part of a varInit and not part of any expression,
        // it would not make sense trying to look it up in a callExpression etc because then its type should be defined in the parameter list.
        if (arrayLiteral.getParent() instanceof HaxeVarInit) {
          HaxePsiField declaringField =
            UsefulPsiTreeUtil.findParentOfTypeButStopIfTypeIs(arrayLiteral, HaxePsiField.class, HaxeCallExpression.class);
          if (declaringField != null) {
            ResultHolder searchResult = searchReferencesForTypeParameters(declaringField, context, resolver, holder);
            if (!searchResult.isUnknown()) holder = searchResult;
          }
        }
      }
      return holder;
    }
  }

  static ResultHolder handleMapLiteral(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeMapLiteral mapLiteral) {
    HaxeMapInitializerExpressionList listElement = mapLiteral.getMapInitializerExpressionList();
    List<HaxeExpression> initializers = new ArrayList<>();

    // In maps, comprehensions don't have expression lists, but they do have one single initializer.
    if (null == listElement) {
      HaxeMapInitializerForStatement forStatement = mapLiteral.getMapInitializerForStatement();
      HaxeMapInitializerWhileStatement whileStatement = mapLiteral.getMapInitializerWhileStatement();
      HaxeExpression fatArrow = null;
      while (null != forStatement || null != whileStatement) {
        if (null != forStatement) {
          fatArrow = forStatement.getMapInitializerExpression();
          whileStatement = forStatement.getMapInitializerWhileStatement();
          forStatement = forStatement.getMapInitializerForStatement();
        } else {
          fatArrow = whileStatement.getMapInitializer();
          forStatement = whileStatement.getMapInitializerForStatement();
          whileStatement = whileStatement.getMapInitializerWhileStatement();
        }
      }
      if (null != fatArrow) {
        initializers.add(fatArrow);
      } else {
        log.error("Didn't find an initializer in a map comprehension: " + mapLiteral.toString(),
                  new HaxeDebugUtil.InvalidValueException(mapLiteral.toString() + '\n' + HaxeDebugUtil.elementLocation(mapLiteral)));
      }
    } else {
      initializers.addAll(listElement.getMapInitializerExpressionList());
    }

    ArrayList<SpecificTypeReference> keyReferences = new ArrayList<>(initializers.size());
    ArrayList<SpecificTypeReference> valueReferences = new ArrayList<>(initializers.size());
    HaxeGenericResolver resolverWithoutHint = resolver.withoutAssignHint();
    for (HaxeExpression ex : initializers) {
      HaxeMapInitializerExpression fatArrow = (HaxeMapInitializerExpression)ex;

      SpecificTypeReference keyType = handle(fatArrow.getFirstChild(), context, resolverWithoutHint).getType();
      if (keyType instanceof SpecificEnumValueReference enumValueReference) {
        keyType = enumValueReference.getEnumClass();
      }
      keyReferences.add(keyType);
      SpecificTypeReference valueType = handle(fatArrow.getLastChild(), context, resolverWithoutHint).getType();
      if (valueType instanceof SpecificEnumValueReference enumValueReference) {
        valueType = enumValueReference.getEnumClass();
      }
      valueReferences.add(valueType);
    }

    // XXX: Maybe track and add constants to the type references, like arrays do??
    //      That has implications on how they're displayed (e.g. not as key=>value,
    //      but as separate arrays).
    ResultHolder keyTypeHolder = HaxeTypeUnifier.unify(keyReferences, mapLiteral).withoutConstantValue().createHolder();
    ResultHolder valueTypeHolder = HaxeTypeUnifier.unify(valueReferences, mapLiteral).withoutConstantValue().createHolder();

    SpecificTypeReference result = SpecificHaxeClassReference.createMap(keyTypeHolder, valueTypeHolder, mapLiteral);
    ResultHolder holder = result.createHolder();
    return holder;
  }

  @NotNull
  static ResultHolder handleExpressionList(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeExpressionList expressionList) {
    ArrayList<ResultHolder> references = new ArrayList<ResultHolder>();
    for (HaxeExpression expression : expressionList.getExpressionList()) {
      references.add(handle(expression, context, resolver));
    }
    return HaxeTypeUnifier.unifyHolders(references, expressionList);
  }

  static ResultHolder handleCallExpression(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeCallExpression callExpression) {
    HaxeExpression callLeft = callExpression.getExpression();
    SpecificTypeReference functionType = handle(callLeft, context, resolver).getType();

    // @TODO: this should be innecessary when code is working right!
    if (functionType.isUnknown()) {
      if (callLeft instanceof HaxeReference) {
        PsiReference reference = callLeft.getReference();
        if (reference != null) {
          PsiElement subelement = reference.resolve();
          if (subelement instanceof HaxeMethod haxeMethod) {
            functionType = haxeMethod.getModel().getFunctionType(resolver);
          }
        }
      }
    }

    if (functionType.isUnknown()) {
      if(log.isDebugEnabled()) log.debug("Couldn't resolve " + callLeft.getText());
    }

    List<HaxeExpression> parameterExpressions = null;
    if (callExpression.getExpressionList() != null) {
      parameterExpressions = callExpression.getExpressionList().getExpressionList();
    } else {
      parameterExpressions = Collections.emptyList();
    }

    if (functionType instanceof  SpecificHaxeClassReference classReference && classReference.isTypeDef() ) {
      functionType = classReference.fullyResolveTypeDefReference();
    }
    if (functionType instanceof SpecificEnumValueReference enumValueConstructor) {
      // TODO, this probably should not be handled here, but its detected as a call expression


      SpecificHaxeClassReference enumClass = enumValueConstructor.enumClass;
      HaxeGenericResolver enumResolver = enumClass.getGenericResolver();
      SpecificFunctionReference constructor = enumValueConstructor.getConstructor();

      List<ResultHolder> list = parameterExpressions.stream()
        .map(expression -> HaxeExpressionEvaluator.evaluate(expression, new HaxeExpressionEvaluatorContext(expression), enumResolver).result)
        .toList();


      ResultHolder holder = enumClass.createHolder();
      SpecificHaxeClassReference type = holder.getClassType();
      @NotNull ResultHolder[] specifics = type.getSpecifics();
      // convert any parameter that matches argument of type TypeParameter into specifics for enum type
      HaxeGenericParam param = enumClass.getHaxeClass().getGenericParam();
      List<HaxeGenericParamModel> params = enumClass.getHaxeClassModel().getGenericParams();

      Map<String, List<ResultHolder>> genericsMap = new HashMap<>();
      params.forEach(g -> genericsMap.put(g.getName(), new ArrayList<>()));

      for (HaxeGenericParamModel model : params) {
        String genericName = model.getName();

        int parameterIndex = 0;
        List<SpecificFunctionReference.Argument> arguments = constructor.getArguments();
        for (int argumentIndex = 0; argumentIndex < arguments.size(); argumentIndex++) {
          SpecificFunctionReference.Argument argument = arguments.get(argumentIndex);
          if (parameterIndex < list.size()) {
            ResultHolder parameter = list.get(parameterIndex++);
            if (argument.getType().canAssign(parameter)) {
              if (argument.getType().getType() instanceof SpecificHaxeClassReference classReference ){
                if (classReference.isTypeParameter() && genericName.equals(classReference.getClassName())) {
                  genericsMap.get(genericName).add(parameter);
                } else {
                  if (argument.getType().isClassType()) {
                    HaxeGenericResolver parameterResolver = parameter.getClassType().getGenericResolver();
                    ResultHolder test = parameterResolver.resolve(genericName);
                    if (test != null && !test.isUnknown()) {
                      genericsMap.get(genericName).add(parameter);
                    }
                  }
                }
              }
            }
          }
        }
      }
      // unify all usage of generics
      for (int i = 0; i < params.size(); i++) {
        HaxeGenericParamModel g = params.get(i);
        String name = g.getName();
        List<ResultHolder> holders = genericsMap.get(name);
        ResultHolder unified = HaxeTypeUnifier.unifyHolders(holders, callExpression);
        enumResolver.add(name, unified, ResolveSource.CLASS_TYPE_PARAMETER);
        specifics[i] = unified;
      }
      return holder;

    }
    if (functionType instanceof SpecificFunctionReference ftype) {

      ResultHolder returnType = ftype.getReturnType();

      HaxeGenericResolver functionResolver = new HaxeGenericResolver();
      functionResolver.addAll(resolver);
      HaxeGenericResolverUtil.appendCallExpressionGenericResolver(callExpression, functionResolver);

      ResultHolder resolved = functionResolver.resolveReturnType(returnType);
      if (resolved != null && !resolved.isUnknown()) {
        returnType = resolved;
      }
      if(returnType.isUnknown() || returnType.isDynamic() || returnType.isVoid()) {
        return returnType.duplicate();
      }

      if(returnType.isFunctionType()){
        return returnType.getFunctionType().createHolder();
      }

      if(returnType.isClassType() || returnType.isEnumValueType()) {
        return returnType.withOrigin(ftype.context);
      }

    }

    if (functionType.isDynamic()) {
      for (HaxeExpression expression : parameterExpressions) {
        handle(expression, context, resolver);
      }

      return functionType.withoutConstantValue().createHolder();
    }

    // @TODO: resolve the function type return type
    return createUnknown(callExpression);
  }




  static ResultHolder handleVarInit(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeVarInit varInit) {
    final HaxeExpression expression = varInit.getExpression();
    if (expression == null) {
      return SpecificTypeReference.getInvalid(varInit).createHolder();
    }
    return handle(expression, context, resolver);
  }

  @NotNull
  static ResultHolder handleLocalVarDeclaration(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeLocalVarDeclaration varDeclaration) {
    final HaxeComponentName name = varDeclaration.getComponentName();
    final HaxeVarInit init = varDeclaration.getVarInit();
    final HaxeTypeTag typeTag = varDeclaration.getTypeTag();
    final ResultHolder unknownResult = createUnknown(varDeclaration);
    HaxeGenericResolver localResolver = new HaxeGenericResolver();
    localResolver.addAll(resolver);
    if(init != null) {
      // find any type parameters used in init expression as the return type might be of that type
      HaxeGenericResolver initResolver = HaxeGenericResolverUtil.generateResolverFromScopeParents(init.getExpression());
      localResolver.addAll(initResolver.withoutUnknowns());
    }
    final ResultHolder initResult = init != null
                                    ? handle(init, context, localResolver)
                                    : unknownResult;
    final ResultHolder typeTagResult = typeTag != null
                                       ? HaxeTypeResolver.getTypeFromTypeTag(typeTag, varDeclaration)
                                       : unknownResult;

    ResultHolder result = typeTag != null ? typeTagResult : initResult;

    if (init == null && typeTag == null) {
      // search for usage to determine type
      return searchReferencesForType(varDeclaration.getComponentName(), context, resolver, null);
    }

    if (init != null && typeTag != null) {
      if (context.holder != null) {
        if (!typeTagResult.canAssign(initResult)) {
          context.addError(
            varDeclaration,
            "Can't assign " + initResult + " to " + typeTagResult,
            new HaxeTypeTagChangeFixer(typeTag, initResult.getType()),
            new HaxeTypeTagRemoveFixer(typeTag)
          );
        }
      }
    }

    context.setLocal(name.getText(), result);

    return result;
  }

  static ResultHolder handleAssignExpression(HaxeExpressionEvaluatorContext context, HaxeGenericResolver resolver, PsiElement element) {
    final PsiElement left = element.getFirstChild();
    final PsiElement right = element.getLastChild();
    if (left != null && right != null) {
      final ResultHolder leftResult = handle(left, context, resolver);
      final ResultHolder rightResult = handle(right, context, resolver);

      if (leftResult.isUnknown()) {
        leftResult.setType(rightResult.getType());
        context.setLocalWhereDefined(left.getText(), leftResult);
      }
      leftResult.removeConstant();

      final SpecificTypeReference leftValue = leftResult.getType();
      final SpecificTypeReference rightValue = rightResult.getType();

      //leftValue.mutateConstantValue(null);

      // skipping `canAssign` check if we dont have a holder to add annotations to
      // this is probably just waste of time when resolving in files we dont have open.
      // TODO try to  see if we need this or can move it so its not executed unnessesary
      if (context.holder != null) {
        if (!leftResult.canAssign(rightResult)) {

          List<HaxeExpressionConversionFixer> fixers = HaxeExpressionConversionFixer.createStdTypeFixers(right, rightValue, leftValue);
          AnnotationBuilder builder = HaxeStandardAnnotation
            .typeMismatch(context.holder, right, rightValue.toStringWithoutConstant(), leftValue.toStringWithoutConstant())
            .withFix(new HaxeCastFixer(right, rightValue, leftValue));

          fixers.forEach(builder::withFix);
          builder.create();
        }
      }

      if (leftResult.isImmutable()) {
        context.addError(element, HaxeBundle.message("haxe.semantic.trying.to.change.an.immutable.value"));
      }

      return rightResult;
    }
    return createUnknown(element);
  }

  static ResultHolder handleLocalVarDeclarationList(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeLocalVarDeclarationList varDeclarationList) {
    // Var declaration list is a statement that returns a Void type, not the type of the local vars it creates.
    // We still evaluate its sub-parts so that we can set the known value types of variables in the scope.
    for (HaxeLocalVarDeclaration part : varDeclarationList.getLocalVarDeclarationList()) {
      handle(part, context, resolver);
    }
    return SpecificHaxeClassReference.getVoid(varDeclarationList).createHolder();
  }

  static ResultHolder handleWhileStatement(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeWhileStatement whileStatement) {
    HaxeDoWhileBody whileBody = whileStatement.getBody();
    HaxeBlockStatement blockStatement = null != whileBody ? whileBody.getBlockStatement() : null;
    List<HaxeExpression> list = null != blockStatement ? blockStatement.getExpressionList() : Collections.emptyList();
    SpecificTypeReference type = null;
    HaxeExpression lastExpression = null;
    for (HaxeExpression expression : list) {
      type = handle(expression, context, resolver).getType();
      lastExpression = expression;
    }
    if (type == null) {
      type = SpecificTypeReference.getDynamic(whileStatement);
    }
    if (!type.isBool() && lastExpression != null) {
      context.addError(
        lastExpression,
        "While expression must be boolean",
        new HaxeCastFixer(lastExpression, type, SpecificHaxeClassReference.getBool(whileStatement))
      );
    }

    PsiElement body = whileStatement.getLastChild();
    if (body != null) {
      //return SpecificHaxeClassReference.createArray(result); // @TODO: Check this
      return handle(body, context, resolver);
    }

    return createUnknown(whileStatement);
  }

  static ResultHolder handleCastExpression(HaxeCastExpression castExpression) {
    HaxeTypeOrAnonymous anonymous = castExpression.getTypeOrAnonymous();
    if (anonymous != null) {
      return HaxeTypeResolver.getTypeFromTypeOrAnonymous(anonymous);
    } else {
      return createUnknown(castExpression);
    }
  }

  @NotNull
  static ResultHolder handleRestParameter(HaxeRestParameter restParameter) {
    HaxeTypeTag tag = restParameter.getTypeTag();
    ResultHolder type = HaxeTypeResolver.getTypeFromTypeTag(tag, restParameter);
    return new ResultHolder(SpecificTypeReference.getStdClass(ARRAY, restParameter, new ResultHolder[]{type}));
  }

  static ResultHolder handleIdentifier(HaxeExpressionEvaluatorContext context, HaxeIdentifier identifier) {
    if (isMacroVariable(identifier)) {
      return SpecificTypeReference.getDynamic(identifier).createHolder();
    }
    // If it has already been seen, then use whatever type is already known.
    ResultHolder holder = context.get(identifier.getText());

    if (holder == null) {
      // context.addError(element, "Unknown variable", new HaxeCreateLocalVariableFixer(element.getText(), element));

      return SpecificTypeReference.getUnknown(identifier).createHolder();
    }

    return holder;
  }

  static ResultHolder handleThisExpression(HaxeGenericResolver resolver, HaxeThisExpression thisExpression) {
    //PsiReference reference = element.getReference();
    //HaxeClassResolveResult result = HaxeResolveUtil.getHaxeClassResolveResult(element);
    HaxeClass ancestor = UsefulPsiTreeUtil.getAncestor(thisExpression, HaxeClass.class);
    if (ancestor == null) return SpecificTypeReference.getDynamic(thisExpression).createHolder();
    HaxeClassModel model = ancestor.getModel();
    if (model.isAbstractType()) {
      SpecificHaxeClassReference reference = model.getUnderlyingClassReference(resolver);
      if (null != reference) {
        return reference.createHolder();
      }
    }
    ResultHolder[] specifics =  HaxeTypeResolver.resolveDeclarationParametersToTypes(model.haxeClass, resolver);
    return SpecificHaxeClassReference.withGenerics(new HaxeClassReference(model, thisExpression), specifics).createHolder();
  }

  @NotNull
  static ResultHolder handleSwitchCaseBlock(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeSwitchCaseBlock caseBlock) {
    List<HaxeReturnStatement> list = caseBlock.getReturnStatementList();
    for (HaxeReturnStatement  statement : list) {
      ResultHolder returnType = handle(statement, context, resolver);
      context.addReturnType(returnType, statement);
    }
    List<HaxeExpression> expressions = caseBlock.getExpressionList();
    if (!expressions.isEmpty()) {
      HaxeExpression lastExpression = expressions.get(expressions.size() - 1);
      return handle(lastExpression, context, resolver);
    }
    return new ResultHolder(SpecificHaxeClassReference.getVoid(caseBlock));
  }

  @NotNull
  static ResultHolder handleSwitchStatement(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeSwitchStatement switchStatement) {
    // TODO: Evaluating result of switch statement should properly implemented
    List<SpecificTypeReference> typeList = new LinkedList<>();
    SpecificTypeReference bestGuess = null;

    if(switchStatement.getSwitchBlock() != null) {
      List<HaxeSwitchCase> caseList = switchStatement.getSwitchBlock().getSwitchCaseList();

      for (HaxeSwitchCase switchCase : caseList) {
        HaxeSwitchCaseBlock block = switchCase.getSwitchCaseBlock();
        if (block != null) {
          ResultHolder handle = handle(block, context, resolver);
          if (!handle.isUnknown())typeList.add(handle.getType());
        }
      }

      for (SpecificTypeReference typeReference : typeList) {
        if (typeReference.isVoid()) continue;
        if (bestGuess == null) {
          bestGuess = typeReference;
          continue;
        }
        bestGuess = HaxeTypeUnifier.unify(bestGuess, typeReference, switchStatement);
      }
    }

    if (bestGuess != null) {
      return new ResultHolder(bestGuess);
    }else {
      return new ResultHolder(SpecificHaxeClassReference.getUnknown(switchStatement));
    }
  }

  static ResultHolder handleCodeBlock(HaxeExpressionEvaluatorContext context, HaxeGenericResolver resolver, PsiElement element) {
    context.beginScope();
    context.getScope().deepSearchForReturnValues = true;

    ResultHolder type = createUnknown(element);
    boolean deadCode = false;
    for (PsiElement childElement : element.getChildren()) {
      type = handle(childElement, context, resolver);
      if (deadCode) {
        //context.addWarning(childElement, "Unreachable statement");
        context.addUnreachable(childElement);
      }
      if (childElement instanceof HaxeReturnStatement) {
        deadCode = true;
      }
    }
    context.endScope();
    return type;
  }

  static ResultHolder handleIterable(
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver,
    HaxeIterable iterable) {
    ResultHolder iteratorParent = handle(iterable.getExpression(), context, resolver);
    SpecificTypeReference type = iteratorParent.getType();
    if (!type.isNumeric()) {
      if (iteratorParent.isClassType()) {
        SpecificHaxeClassReference haxeClassReference = iteratorParent.getClassType();
        HaxeGenericResolver localResolver =  new HaxeGenericResolver();
        localResolver.addAll(resolver);
        localResolver.addAll(haxeClassReference.getGenericResolver());// replace parent/old resolver values with newer from class reference
        if (haxeClassReference != null && haxeClassReference.getHaxeClassModel() != null) {
          HaxeForStatement parentForLoop = PsiTreeUtil.getParentOfType(iterable, HaxeForStatement.class);

          if(haxeClassReference.isTypeDefOfClass()) {
            SpecificTypeReference typeReference = haxeClassReference.fullyResolveTypeDefReference();
            if (typeReference instanceof  SpecificHaxeClassReference classReference) {
              HaxeGenericResolver typeDefResolved = classReference.getGenericResolver();
              localResolver.addAll(typeDefResolved);
            }
          }

          if (parentForLoop.getKeyValueIterator() == null) {
            HaxeMemberModel iterator = haxeClassReference.getHaxeClassModel().getMember("iterator", resolver);
            if (iterator instanceof HaxeMethodModel methodModel) {
              return methodModel.getReturnType(localResolver);
            }
          }else {
            HaxeMemberModel iterator = haxeClassReference.getHaxeClassModel().getMember("keyValueIterator", resolver);
            if (iterator instanceof HaxeMethodModel methodModel) {
              return methodModel.getReturnType(localResolver);
            }
          }
        }
      }
    }

    return handle(iterable.getExpression(), context, resolver);
  }

  @NotNull
  static ResultHolder handleTryStatement(HaxeExpressionEvaluatorContext context,
                                                 HaxeGenericResolver resolver,
                                                 HaxeTryStatement tryStatement) {
    //  try-catch can be used as a value expression all blocks must be evaluated and unified
    //  we should also iterate trough so we can pick up any return statements
    @NotNull PsiElement[] children = tryStatement.getChildren();
    List<ResultHolder> blockResults = new ArrayList<>();
    for (PsiElement child : children) {
      blockResults.add(handle(child, context, resolver));
    }
    return HaxeTypeUnifier.unifyHolders(blockResults, tryStatement);
  }

  static ResultHolder handleReturnStatement(HaxeExpressionEvaluatorContext context,
                                                    HaxeGenericResolver resolver,
                                                    HaxeReturnStatement returnStatement) {

    ResultHolder result = SpecificHaxeClassReference.getVoid(returnStatement).createHolder();
    if (isUntypedReturn(returnStatement)) return result;
    PsiElement[] children = returnStatement.getChildren();
    if (children.length >= 1) {
      result = handle(children[0], context, resolver);
    }
    context.addReturnType(result, returnStatement);
    return result;
  }

  static ResultHolder handleImportAlias(PsiElement element, HaxeImportAlias alias) {
    HaxeResolveResult result = alias.resolveHaxeClass();
    HaxeClass haxeClass = result.getHaxeClass();
    if (haxeClass == null) {
      return new ResultHolder(SpecificHaxeClassReference.getUnknown(element));
    }else {
      return SpecificHaxeClassReference.withoutGenerics(new HaxeClassReference(haxeClass.getModel(), element)).createHolder();
    }
  }

  static SpecificTypeReference resolveAnyTypeDefs(SpecificTypeReference reference) {
    if (reference instanceof SpecificHaxeClassReference classReference && classReference.isTypeDef()) {
      if(classReference.isTypeDefOfFunction()) {
        return classReference.resolveTypeDefFunction();
      }else {
        SpecificHaxeClassReference resolvedClass = classReference.resolveTypeDefClass();
        return resolveAnyTypeDefs(resolvedClass);
      }
    }
    return reference;
  }

  static void checkParameters(
    final PsiElement callelement,
    final HaxeMethodModel method,
    final List<HaxeExpression> arguments,
    final HaxeExpressionEvaluatorContext context,
    final HaxeGenericResolver resolver
  ) {
    checkParameters(callelement, method.getFunctionType(resolver), arguments, context, resolver);
  }

  static void checkParameters(
    PsiElement callelement,
    SpecificFunctionReference ftype,
    List<HaxeExpression> parameterExpressions,
    HaxeExpressionEvaluatorContext context,
    HaxeGenericResolver resolver
  ) {
    if (!context.isReportingErrors()) return;

    List<SpecificFunctionReference.Argument> parameterTypes = ftype.getArguments();

    int parameterTypesSize = parameterTypes.size();
    int parameterExpressionsSize = parameterExpressions.size();
    int len = Math.min(parameterTypesSize, parameterExpressionsSize);

    for (int n = 0; n < len; n++) {
      ResultHolder type = HaxeTypeResolver.resolveParameterizedType(parameterTypes.get(n).getType(), resolver);
      HaxeExpression expression = parameterExpressions.get(n);
      ResultHolder value = handle(expression, context, resolver);

      if (context.holder != null) {
        if (!type.canAssign(value)) {
          context.addError(
            expression,
            "Can't assign " + value + " to " + type,
            new HaxeCastFixer(expression, value.getType(), type.getType())
          );
        }
      }
    }

    //log.debug(ftype.getDebugString());
    // More parameters than expected
    if (parameterExpressionsSize > parameterTypesSize) {
      for (int n = parameterTypesSize; n < parameterExpressionsSize; n++) {
        context.addError(parameterExpressions.get(n), "Unexpected argument");
      }
    }
    // Less parameters than expected
    else if (parameterExpressionsSize < ftype.getNonOptionalArgumentsCount()) {
      context.addError(callelement, "Less arguments than expected");
    }
  }

  static private String getOperator(PsiElement field, TokenSet set) {
    ASTNode operatorNode = field.getNode().findChildByType(set);
    if (operatorNode == null) return "";
    return operatorNode.getText();
  }

  static int getDistance(PsiReference reference, int offset) {
    return reference.getAbsoluteRange().getStartOffset() - offset;
  }

  static boolean isMacroVariable(HaxeIdentifier identifier) {
    return identifier.getMacroId() != null;
  }

  @Nullable
  static ResultHolder findInitTypeForUnify(@NotNull PsiElement field) {
    HaxeVarInit varInit = PsiTreeUtil.getParentOfType(field, HaxeVarInit.class);
    if (varInit != null) {
      HaxeFieldDeclaration type = PsiTreeUtil.getParentOfType(varInit, HaxeFieldDeclaration.class);
      if (type!= null) {
        HaxeTypeTag tag = type.getTypeTag();
        if (tag != null) {
          ResultHolder typeTag = HaxeTypeResolver.getTypeFromTypeTag(tag, field);
          if (!typeTag.isUnknown()) {
            return typeTag;
          }
        }
      }
    }
    return null;
  }

  static boolean isUntypedReturn(HaxeReturnStatement statement) {
    PsiElement child = statement.getFirstChild();
    while(child != null) {
      if (child instanceof HaxePsiToken psiToken) {
        if (psiToken.getTokenType() == KUNTYPED) return true;
      }
      child = child.getNextSibling();
    }
    return false;
  }

}
