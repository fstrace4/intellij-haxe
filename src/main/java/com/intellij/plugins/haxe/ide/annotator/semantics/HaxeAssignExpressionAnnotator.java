package com.intellij.plugins.haxe.ide.annotator.semantics;

import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.plugins.haxe.HaxeBundle;
import com.intellij.plugins.haxe.ide.annotator.HaxeStandardAnnotation;
import com.intellij.plugins.haxe.lang.psi.HaxeArrayLiteral;
import com.intellij.plugins.haxe.lang.psi.HaxeAssignExpression;
import com.intellij.plugins.haxe.lang.psi.HaxeMapLiteral;
import com.intellij.plugins.haxe.model.fixer.HaxeExpressionConversionFixer;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.plugins.haxe.model.type.resolver.ResolveSource;
import com.intellij.plugins.haxe.util.UsefulPsiTreeUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.plugins.haxe.ide.annotator.HaxeSemanticAnnotatorInspections.ASSIGNMENT_TYPE_COMPATIBILITY_CHECK;
import static com.intellij.plugins.haxe.ide.annotator.HaxeStandardAnnotation.typeMismatch;
import static com.intellij.plugins.haxe.lang.psi.HaxeResolver.typeHintKey;

public class HaxeAssignExpressionAnnotator implements Annotator {
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof HaxeAssignExpression assignExpression) {
      check(assignExpression, holder);
    }
  }

  public static void check(HaxeAssignExpression psi, AnnotationHolder holder) {
    if (!ASSIGNMENT_TYPE_COMPATIBILITY_CHECK.isEnabled(psi)) return;

    // TODO: Think about how to use models to do this instead. :/
    PsiElement lhs = UsefulPsiTreeUtil.getFirstChildSkipWhiteSpacesAndComments(psi);
    PsiElement assignOperation = UsefulPsiTreeUtil.getNextSiblingSkipWhiteSpacesAndComments(lhs);
    PsiElement rhs = UsefulPsiTreeUtil.getNextSiblingSkipWhiteSpacesAndComments(assignOperation);
    if (lhs == null || rhs == null) return;

    HaxeGenericResolver lhsResolver = HaxeGenericResolverUtil.generateResolverFromScopeParents(lhs);
    HaxeGenericResolver rhsResolver = HaxeGenericResolverUtil.generateResolverFromScopeParents(rhs);

    ResultHolder lhsType = HaxeTypeResolver.getPsiElementType(lhs, psi, lhsResolver);
    rhsResolver.add("", lhsType.tryUnwrapNullType(), ResolveSource.ASSIGN_TYPE);
    // if class add type hinting for resolver
    if(lhsType.isClassType()){
      SpecificHaxeClassReference type = lhsType.getClassType();
      if(type != null && type.getHaxeClass() != null) {
        String qualifiedName = type.getHaxeClass().getQualifiedName();
        rhs.putUserData(typeHintKey, qualifiedName);
      }
    }
    ResultHolder rhsType = HaxeTypeResolver.getPsiElementType(rhs, psi, rhsResolver);

    // Allow Literal Maps and arrays to be assigned to any specifics they have in common with assigned variable.
    if ((rhs instanceof HaxeMapLiteral || rhs instanceof HaxeArrayLiteral) && lhsType.isClassType() && rhsType.isClassType()) {
      ResultHolder[] lhsSpecifics = lhsType.getClassType().getSpecifics();
      ResultHolder[] rhsSpecifics = rhsType.getClassType().getSpecifics();
      int minSpecifics = Math.min(lhsSpecifics.length, rhsSpecifics.length);
      for (int i = 0; i < minSpecifics; i++) {
        ResultHolder unified = HaxeTypeUnifier.unify(lhsSpecifics[i], rhsSpecifics[i]);
        if(!unified.isUnknown()) {
          rhsSpecifics[i] = unified;
        }
      }
    }
    // hack for String since its not a class with operator overloads but can have any object added to it;
    if (lhsType.getType().isString() &&  assignOperation.textMatches("+=")) {
      return;
    }
    HaxeAssignContext  context = new HaxeAssignContext(lhs, rhs);
    if (!lhsType.canAssign(rhsType, context)) {
      List<HaxeExpressionConversionFixer> fixers = HaxeExpressionConversionFixer.createStdTypeFixers(rhs, rhsType.getType(), lhsType.getType());

      if(context.hasMissingMembers() || context.hasWrongTypeMembers()) {
        if(context.hasMissingMembers()) {
          HaxeStandardAnnotation.typeMismatchMissingMembers(holder, rhs, context)
            .create();
        }
        if(context.hasWrongTypeMembers()) {
          HaxeStandardAnnotation.addtypeMismatchWrongTypeMembersAnnotations(holder, rhs, context);
        }
      }else {
        AnnotationBuilder builder = typeMismatch(holder, rhs, rhsType.toPresentationString(), lhsType.toPresentationString());
        fixers.forEach(builder::withFix);
        builder.create();
      }
    }
    if (lhsType.isImmutable()) {
      // TODO: Think about providing a quick-fix for immutability; remember final markings come from metadata, too.
      holder.newAnnotation(HighlightSeverity.ERROR, HaxeBundle.message("haxe.semantic.cannot.assign.value.to.final.variable"))
        .range(psi)
        .create();
    }
  }
}
