package com.intellij.plugins.haxe.ide.inspections.intentions;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.plugins.haxe.HaxeFileType;
import com.intellij.plugins.haxe.ide.annotator.semantics.HaxeCallExpressionUtil;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.model.HaxeFieldModel;
import com.intellij.plugins.haxe.model.type.HaxeExpressionEvaluator;
import com.intellij.plugins.haxe.model.type.HaxeExpressionEvaluatorContext;
import com.intellij.plugins.haxe.model.type.ResultHolder;
import com.intellij.plugins.haxe.model.type.SpecificHaxeClassReference;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class HaxeUnresolvedSymbolIntentionBase<T extends PsiElement> extends LocalQuickFixAndIntentionActionOnPsiElement {

  protected final @NotNull SmartPsiElementPointer<T> myPsiElementPointer;

  public HaxeUnresolvedSymbolIntentionBase(@NotNull T element) {
    super(element);
    myPsiElementPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return getText();
  }



  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {

    PsiElement element = myPsiElementPointer.getElement();

    PsiElement original = fileCopyClonePsiElement(element);
    PsiElement copy = fileCopyClonePsiElement(element);

    perform(project, copy, editor);
    // this might not be the best solution but it seems to work.
    // in order to get the correct line-number we need to compare the entire file and to only get the changes we need identical formatting
    String originalFormatted = CodeStyleManager.getInstance(project).reformat(original.getContainingFile(), true).getText();
    String copyReformated = CodeStyleManager.getInstance(project).reformat(copy.getContainingFile(), true).getText();


    return new IntentionPreviewInfo.CustomDiff(HaxeFileType.INSTANCE, null, originalFormatted, copyReformated, true);
  }


  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {

    perform(project, myPsiElementPointer.getElement(), editor);
  }

  protected abstract void perform(@NotNull Project project, @NotNull PsiElement element, @NotNull Editor editor);



  protected boolean needsToBeStatic() {
    HaxeMethodDeclaration type = PsiTreeUtil.getParentOfType(myPsiElementPointer.getElement(), HaxeMethodDeclaration.class);
    if (type != null) {
      return type.getModel().isStatic();
    }

    HaxeFieldDeclaration field = PsiTreeUtil.getParentOfType(myPsiElementPointer.getElement(), HaxeFieldDeclaration.class);
    if (field != null) {
      return ((HaxeFieldModel)field.getModel()).isStatic();
    }
    return false;
  }

  protected PsiElement fileCopyClonePsiElement(PsiElement psiElement) {
    PsiFile originalFile = psiElement.getContainingFile();
    PsiFile fileCopy = (PsiFile)originalFile.copy();
    return fileCopy.findElementAt(psiElement.getTextOffset());
  }


  protected PsiElement createNewLine(@NotNull Project project) {
    return PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n").copy();
  }

  protected String guessElementType() {
    PsiElement parent = myPsiElementPointer.getElement().getParent();
    if (parent instanceof HaxeCallExpressionList list) {
      return findTypeFromCallExpression(list, parent);
    }

    if (parent instanceof HaxeAssignExpression assign) {
      return findTypeFromAssignExpression(assign);
    }

    if (parent instanceof HaxeBinaryExpression expression) {
      return findTypeFromAddExpression(expression);
    }

    if (parent instanceof HaxeGuard) {
      return SpecificHaxeClassReference.BOOL;
    }

    if (parent instanceof HaxeVarInit init) {
      if(init.getParent() instanceof HaxePsiField declaration) {
        HaxeTypeTag tag = declaration.getTypeTag();
        if (tag != null) {
          return tag.getFunctionType() != null ? tag.getFunctionType().getText()
                                               : tag.getTypeOrAnonymous().getText();
        }
      }
    }
    return SpecificHaxeClassReference.DYNAMIC;
  }

  private String findTypeFromAddExpression(HaxeBinaryExpression expression) {
    HaxeExpression target = expression.getLeftExpression();
    if (target == myPsiElementPointer.getElement()) {
      target = expression.getRightExpression();
    }
    ResultHolder result = HaxeExpressionEvaluator.evaluate(target, null).result;
    if (!result.isUnknown()) return getTypeName(result);
    return SpecificHaxeClassReference.DYNAMIC;
  }

  private String findTypeFromAssignExpression(HaxeAssignExpression assign) {
    List<HaxeExpression> assignlist = assign.getExpressionList();
    HaxeExpression expression = assignlist.get(0);
    HaxeExpressionEvaluatorContext evaluated = HaxeExpressionEvaluator.evaluate(expression, null);
    ResultHolder result = evaluated.result;
    if (!result.isUnknown()) {
      return getTypeName(result);
    }
    return SpecificHaxeClassReference.DYNAMIC;
  }

  private String findTypeFromCallExpression(HaxeCallExpressionList list, PsiElement parent) {
    List<HaxeExpression> argList = list.getExpressionList();
    int index = argList.indexOf(myPsiElementPointer.getElement());

    if (index > -1) {
      if (parent.getParent() instanceof HaxeCallExpression callExpression) {
        if (callExpression.getExpression() instanceof HaxeReference reference) {
          PsiElement resolved = reference.resolve();
          if (resolved instanceof HaxeMethod method) {
            HaxeCallExpressionUtil.CallExpressionValidation validation = HaxeCallExpressionUtil.checkMethodCall(callExpression, method);
            Integer parameterIndex = validation.getArgumentToParameterIndex().get(index);
            if (parameterIndex != null) {
              ResultHolder paramType = validation.getParameterIndexToType().get(parameterIndex);
              if(paramType != null) return getTypeName(paramType);
            }
          }
        }
      }
    }
    return SpecificHaxeClassReference.DYNAMIC;
  }

  protected String  getTypeName(ResultHolder holder) {
      if(holder.isClassType()) return holder.getClassType().getClassName();
      else if(holder.isFunctionType()) return holder.getFunctionType().toPresentationString();
      else if(holder.isEnumValueType()) return holder.getEnumValueType().getEnumClass().getClassName();
      else return SpecificHaxeClassReference.DYNAMIC;
  }
}
