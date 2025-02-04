/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2014 AS3Boyan
 * Copyright 2014-2014 Elias Ku
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
package com.intellij.plugins.haxe.ide.actions;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.codeInspection.HintAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.plugins.haxe.HaxeBundle;
import com.intellij.plugins.haxe.HaxeFileType;
import com.intellij.plugins.haxe.lang.psi.HaxeClass;
import com.intellij.plugins.haxe.lang.psi.HaxeComponent;
import com.intellij.plugins.haxe.util.HaxeAddImportHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author: Fedor.Korotkov
 */
public class HaxeTypeAddImportIntentionAction implements HintAction, QuestionAction, LocalQuickFix {
  private final List<HaxeComponent> candidates;
  private final PsiElement myType;
  private Editor myEditor;

  public HaxeTypeAddImportIntentionAction(@NotNull PsiElement type, @NotNull List<HaxeComponent> components) {
    myType = type;
    candidates = components;
  }

  @Override
  public boolean showHint(@NotNull Editor editor) {
    myEditor = editor;
    TextRange range = InjectedLanguageManager.getInstance(myType.getProject()).injectedToHost(myType, myType.getTextRange());
    HintManager.getInstance().showQuestionHint(editor, getText(), range.getStartOffset(), range.getEndOffset(), this);
    return true;
  }

  @NotNull
  @Override
  public String getText() {
    if (candidates.size() > 1) {
      final HaxeClass haxeClass = (HaxeClass)candidates.iterator().next();
      return HaxeBundle.message("add.import.multiple.candidates", haxeClass.getQualifiedName());
    }
    else if (candidates.size() == 1) {
      final HaxeClass haxeClass = (HaxeClass)candidates.iterator().next();
      return haxeClass.getQualifiedName() + " ?";
    }
    return "";
  }

  @NotNull
  @Override
  public String getName() {
    return getText();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    invoke(project, myEditor, descriptor.getPsiElement().getContainingFile());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myType.isValid();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    if (candidates.size() > 1) {
      PsiElement[] psiElements = candidates.toArray(new PsiElement[0]);
      PsiElementProcessor<PsiElement> processor = element -> {
        CommandProcessor.getInstance().executeCommand(project, () -> doImport(element), getClass().getName(), this);
        return true;
      };
      ApplicationManager.getApplication().invokeLater(() -> {
      new PsiTargetNavigator<>(psiElements)
        .createPopup(project, HaxeBundle.message("choose.class.to.import.title"), processor)
        .showInBestPositionFor(editor);
      });
    }
    else if (!candidates.isEmpty())  {
      doImport(candidates.iterator().next());
    }
  }

  private void doImport(final PsiElement component) {
    PsiFile file = myType.getContainingFile();

    WriteCommandAction.writeCommandAction(myType.getProject(), file)
      .run(() -> {
        HaxeAddImportHelper.addImport(((HaxeClass)component).getQualifiedName(), file);
        PsiUtilCore.ensureValid(file);
      });
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile original) {
    PsiFile file = (PsiFile)original.copy();

    HaxeComponent next = candidates.iterator().next();
    if (next instanceof HaxeClass haxeClass) {
      HaxeAddImportHelper.addImport((haxeClass).getQualifiedName(), file);
      return new IntentionPreviewInfo.CustomDiff(HaxeFileType.INSTANCE, null, original.getText(), file.getText(), true);
    }
    return HintAction.super.generatePreview(project, editor, file);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public boolean execute() {
    final PsiFile containingFile = myType.getContainingFile();
    invoke(containingFile.getProject(), myEditor, containingFile);
    return true;
  }
}
