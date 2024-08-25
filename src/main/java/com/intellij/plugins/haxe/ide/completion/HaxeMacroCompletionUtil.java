package com.intellij.plugins.haxe.ide.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import static com.intellij.plugins.haxe.ide.completion.HaxeCompletionUtil.flushChanges;
import static com.intellij.plugins.haxe.ide.completion.HaxeCompletionUtil.reformatAndAdjustIndent;
import static com.intellij.plugins.haxe.ide.completion.KeywordCompletionData.*;

public class HaxeMacroCompletionUtil {

  public static @NotNull LookupElement reificationLookupElement(MacroCompletionData data) {

    String displayName = data.getDisplayName();
    String displayDescription = data.getDisplayDescription();

    String templateValue = data.getTemplate();

    String template = templateValue != null ? templateValue : displayName;


    LookupElementBuilder builder = LookupElementBuilder.create(data.getKeyword(), template)
      .withBoldness(false)
      .withItemTextItalic(true)
      .withTypeText(displayDescription, true)
      .withPresentableText(displayName);

    builder = builder.withInsertHandler((context, item) -> {
      Editor editor = context.getEditor();
      Project project = editor.getProject();

      int caretOffset = template.lastIndexOf(CARET);
      String content = template.replaceAll(CARET, "");

      Document document = editor.getDocument();
      int startOffset = context.getStartOffset();
      document.replaceString(startOffset, context.getSelectionEndOffset(), content);

      if (caretOffset != -1) {
        TextRange range = new TextRange(startOffset, startOffset + content.length());
        int caret = startOffset + caretOffset;
        editor.getCaretModel().moveToOffset(caret);

        flushChanges(project, document);
        reformatAndAdjustIndent(context, range);
      }
    });


    return builder;
  }

}
