package com.intellij.plugins.haxe.ide.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResult;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.plugins.haxe.HaxeComponentType;
import com.intellij.plugins.haxe.ide.annotator.semantics.HaxeCallExpressionUtil;
import com.intellij.plugins.haxe.ide.lookup.*;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.model.*;
import com.intellij.plugins.haxe.model.type.ResultHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.plugins.haxe.ide.completion.HaxeCommonCompletionPattern.identifierInNewExpression;
import static com.intellij.plugins.haxe.ide.lookup.HaxeCompletionPriorityData.*;

public class HaxeCompletionPriorityUtil {

  public static Set<CompletionResult> calculatePriority(Set<CompletionResult> completions, CompletionParameters parameters) {
    // ignore any completion that does not implement boost
    List<HaxeLookupElement> lookupList =
      completions.stream().filter(result -> result.getLookupElement() instanceof HaxeLookupElement)
        .map(result -> (HaxeLookupElement)result.getLookupElement())
        .toList();

    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (identifierInNewExpression.accepts(position)) {
      return prioritizeConstructorsRemoveOtherMembers(completions);
    }
    // is argument (get parameter type)
    boolean sorted = false;
    if (!sorted)sorted = trySortForExtends(position, lookupList);
    if (!sorted) sorted = trySortForArgument(position, lookupList); // NOTE TO SELF: function keyword if parameter is function typ
    //TODO WiP
    // is for-loop (prioritize itrables)
    // is extends (if class, then class, if interface then interfaces)
    // is implements (interfaces)
    // is IF (prioritize bool)
    // is switch block (find expression type, prioritize type)
    //  - if enumvalue  compare  declaring class with type

    // local variables first (3), then fields(2) then  methods (1)

    //DEBUG STUFF:
    List<HaxeLookupElement> debugSortedList = lookupList.stream().sorted((o1, o2) -> {
      double calculate1 = o1.getPriority().calculate();
      double calculate2 = o2.getPriority().calculate();
      if (calculate1 > calculate2) return -1;
      if (calculate1 < calculate2) return 1;
      return 0;

    }).toList();

    return completions;
  }

  /*
   * If part of an inherit list, prioritize classes and interfaces
   *   - if part of an interface, prioritize other interfaces to extend
   *   - if part of class,  prioritize classes if in extends, and interfaces in implements lists
   */
  private static boolean trySortForExtends(PsiElement position, List<HaxeLookupElement> list) {
    HaxeInheritList inheritList = PsiTreeUtil.getParentOfType(position, HaxeInheritList.class);
    if (inheritList != null) {
      HaxeClassDeclaration classDeclaration = PsiTreeUtil.getParentOfType(inheritList, HaxeClassDeclaration.class);
      if (classDeclaration != null) {
        HaxeExtendsDeclaration extendsDeclaration = PsiTreeUtil.getParentOfType(position, HaxeExtendsDeclaration.class);
        if (extendsDeclaration != null) {
          list.stream()
            .filter(element -> element instanceof  HaxePsiLookupElement lookupElement && lookupElement.getType() == HaxeComponentType.CLASS)
            .forEach(element -> element.getPriority().type += 1);
          return true;
        }
        HaxeImplementsDeclaration implementsDeclaration = PsiTreeUtil.getParentOfType(position, HaxeImplementsDeclaration.class);
        if (implementsDeclaration != null) {
          list.stream()
            .filter(element -> element instanceof  HaxePsiLookupElement lookupElement && lookupElement.getType() == HaxeComponentType.INTERFACE)
            .forEach(element -> element.getPriority().type += 1);
          return true;
        }
      }else {
        list.stream()
          .filter(element -> element instanceof  HaxePsiLookupElement lookupElement && lookupElement.getType() == HaxeComponentType.INTERFACE)
          .forEach(element -> element.getPriority().type += 1);
        return true;
      }
    }
    return false;
  }

  private static Set<CompletionResult> prioritizeConstructorsRemoveOtherMembers(Set<CompletionResult> completions) {
    Stream<CompletionResult>  constructors =  completions.stream()
      .filter(result -> result.getLookupElement() instanceof HaxeConstructorLookupElement)
      .peek(result -> ((HaxeConstructorLookupElement)result.getLookupElement()).getPriority().type += 1);
    Stream<CompletionResult> others =  completions.stream().filter(result -> !(result.getLookupElement() instanceof HaxeLookupElement));
    return Stream.concat(constructors, others).collect(Collectors.toSet());
  }

  private static boolean trySortForArgument(PsiElement position, List<HaxeLookupElement> lookupElements) {
    HaxeCallExpression callExpression = PsiTreeUtil.getParentOfType(position, HaxeCallExpression.class, true, HaxeNewExpression.class);
    HaxeNewExpression newExpression = PsiTreeUtil.getParentOfType(position, HaxeNewExpression.class, true, HaxeCallExpression.class);
    if (newExpression == null &&  callExpression == null) return false;

    HaxeCallExpressionUtil.CallExpressionValidation validation = null;
    if (callExpression != null) {
      validation = getValidationForMethod(callExpression);
    }
    if (newExpression != null && newExpression.getType() != null) {
      boolean completeType = newExpression.getType().textMatches(position);
      // if completing type name
      if (completeType) {
        lookupElements.stream()
          .filter(element -> element instanceof HaxeClassLookupElement)
          .forEach(e -> e.getPriority().type += 1);
        return true;
      }else {
        // else if completing parameters
        validation = HaxeCallExpressionUtil.checkConstructor(newExpression);
      }
    }

    if (validation!= null) {
      Collection<ResultHolder> parameterTypes = validation.getParameterIndexToType().values();
      List<String> names = validation.getParameterNames();
      lookupElements.stream()
        .peek( element ->  {if(element instanceof HaxePackageLookupElement lookupElement) lookupElement.getPriority().type -=0.1;})
        .filter(lookupElement -> lookupElement instanceof HaxeMemberLookupElement)
        .peek(element -> element.getPriority().type +=1)
        .map(lookupElement -> (HaxeMemberLookupElement)lookupElement)
        .forEach( r-> memberAssignCalculation(r, parameterTypes));
      return true;
    }
    return false;
  }

  private static HaxeCallExpressionUtil.CallExpressionValidation getValidationForMethod(HaxeCallExpression callExpression) {
    if (callExpression.getExpression() instanceof HaxeReferenceExpression referenceExpression) {
      PsiElement resolve = referenceExpression.resolve();
      if (resolve instanceof HaxeMethod method) {
        return HaxeCallExpressionUtil.checkMethodCall(callExpression, method);
      }
    }
    return null;
  }


  private static void memberAssignCalculation(HaxeMemberLookupElement element, Collection<ResultHolder> parameterTypes) {
    HaxeBaseMemberModel model = element.getModel();
    if (model == null) return;

    if(model instanceof HaxeLocalVarModel) {
      element.getPriority().type += LOCAL_VAR;
    }

    if(model instanceof HaxeFieldModel) {
      element.getPriority().type += FIELD;
    }

    if (model instanceof HaxeMethodModel) {
      element.getPriority().type += METHOD;
    }

    ResultHolder type = model.getResultType(null);
    if (type != null && !type.isVoid()) {
      if (parameterTypes.stream().anyMatch(type::canAssign)) {
        element.getPriority().assignable += 1;
      }
    }
  }


  private static @NotNull CompletionResult boost(CompletionResult result, double finalBoost) {
    LookupElement element = result.getLookupElement();
    if (element instanceof HaxeMemberLookupElement lookupElement) {
      if (lookupElement.getModel() instanceof HaxeFieldModel) {
        return result.withLookupElement(PrioritizedLookupElement.withPriority(element, finalBoost));
      }
    }
    return result;
  }

  public static CompletionResult convertToPrioritized(CompletionResult result) {
    if (result.getLookupElement() instanceof HaxeLookupElement element) {
      return CompletionResult.wrap(element.toPrioritized(), result.getPrefixMatcher(), result.getSorter());
    }
    return result;
  }

}
