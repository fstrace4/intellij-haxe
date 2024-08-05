package com.intellij.plugins.haxe.ide.annotator.semantics;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.model.FullyQualifiedInfo;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


import static com.intellij.plugins.haxe.ide.annotator.semantics.HaxeCallExpressionUtil.*;

public class HaxeCallExpressionAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof HaxeCallExpression expression) {
      if (expression.getExpression() instanceof HaxeReference reference) {
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof HaxePsiField  || resolved instanceof HaxeParameter ) {
          HaxeNamedComponent component = (HaxeNamedComponent)resolved;
          HaxeGenericResolver resolver = HaxeGenericResolverUtil.generateResolverFromScopeParents(reference);

          ResultHolder callieType = tryGetCallieType(expression);
          if (!callieType.isUnknown() && callieType.getClassType() != null) {
            resolver.addAll(callieType.getClassType().getGenericResolver());
          }


          ResultHolder type = HaxeTypeResolver.getFieldOrMethodReturnType(component, resolver);
          SpecificTypeReference typeReference;
          if (type.getClassType() != null) {
             typeReference = type.getClassType().fullyResolveTypeDefAndUnwrapNullTypeReference(true);
          } else  {
            typeReference = type.getType();
          }

          if (typeReference  instanceof  SpecificFunctionReference functionType) {
            // function type or function literal
            if ( functionType.method == null) {
              CallExpressionValidation validation = checkFunctionCall(expression, functionType);
              createAnnotations(holder, validation);
            } else {
              CallExpressionValidation validation = checkMethodCall(expression, functionType.method.getMethod());
              createAnnotations(holder, validation);
            }
          } else if (typeReference instanceof SpecificHaxeClassReference classReference) {
            if (classReference.getHaxeClassModel() != null) {
              if (classReference.getHaxeClassModel().isCallable()) return;
            }
            // if not enum value constructor or dynamic, show error
            if (!type.isEnumValueType() && !type.isDynamic() && !type.isUnknown()) {
              // TODO bundle
              holder.newAnnotation(HighlightSeverity.ERROR, typeReference.toPresentationString() + " is not a callable type")
                .range(element)
                .create();
            }
          }
        }
        else if (resolved instanceof HaxeMethod method) {
          if (isTrace(method))return;
          CallExpressionValidation validation = checkMethodCall(expression, method);
          createAnnotations(holder, validation);
        }
      }
    }
    if (element instanceof HaxeNewExpression newExpression) {
      CallExpressionValidation validation = checkConstructor(newExpression);
      createAnnotations(holder, validation);
    }
  }

  private void createAnnotations(@NotNull AnnotationHolder holder, CallExpressionValidation validation) {
    if (!validation.errors.isEmpty())createErrorAnnotations(validation, holder);
    if (!validation.warnings.isEmpty())createWarningAnnotations(validation, holder);
  }

  // the trace method in std does not have rest arg so we ignore it
  private static boolean isTrace(HaxeMethod method) {
    FullyQualifiedInfo info = method.getModel().getQualifiedInfo();
    if (info == null) return false;
    return info.className.equals("Log")
           && info.packagePath.equals("haxe")
           && info.memberName.equals("trace");
  }

  private void createErrorAnnotations(@NotNull CallExpressionValidation validation, @NotNull AnnotationHolder holder) {
    validation.errors.forEach(record -> holder.newAnnotation(HighlightSeverity.ERROR, record.message())
      .range(record.range())
      .create());
  }
  private void createWarningAnnotations(@NotNull CallExpressionValidation validation, @NotNull AnnotationHolder holder) {
    validation.warnings.forEach(record -> holder.newAnnotation(HighlightSeverity.WEAK_WARNING, record.message())
      .range(record.range())
      .create());
  }


}
