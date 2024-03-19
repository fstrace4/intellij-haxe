package com.intellij.plugins.haxe.ide.intention;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.plugins.haxe.HaxeBundle;
import com.intellij.plugins.haxe.HaxeLanguage;
import com.intellij.plugins.haxe.lang.psi.HaxeBlockStatement;
import com.intellij.plugins.haxe.lang.psi.HaxeMethod;
import com.intellij.plugins.haxe.lang.psi.HaxeMethodDeclaration;
import com.intellij.plugins.haxe.lang.psi.HaxeTypeTag;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.plugins.haxe.util.UsefulPsiTreeUtil.findParentOfTypeButStopIfTypeIs;

public class RemoveReturnTypeTagIntention extends BaseIntentionAction {


  public RemoveReturnTypeTagIntention() {
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }
  @NotNull
  @Override
  public String getText() {
    return HaxeBundle.message("haxe.quickfix.remove.return.type");
  }


  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file.getLanguage() != HaxeLanguage.INSTANCE) return false;

    HaxeMethod myMethod =  attemptToFindMethod(editor, file);

    return  myMethod instanceof HaxeMethodDeclaration declaration && declaration.getTypeTag() != null;
  }


  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    HaxeMethod myMethod =  attemptToFindMethod(editor, file);
    if (myMethod instanceof  HaxeMethodDeclaration declaration) {
      HaxeTypeTag tag = declaration.getTypeTag();
      if (tag != null) tag.delete();
    }
  }


  private @Nullable HaxeMethod attemptToFindMethod(Editor editor, PsiFile file) {
    PsiElement place = file.findElementAt(editor.getCaretModel().getOffset());
    if (place instanceof HaxeMethod method) {
      return method;
    }
    else if (place != null) {
      return findParentOfTypeButStopIfTypeIs(place, HaxeMethod.class, HaxeBlockStatement.class);
    }
    return null;
  }

}