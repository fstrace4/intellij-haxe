package com.intellij.plugins.haxe.ide.completion;


import com.intellij.psi.tree.IElementType;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

@Value
@AllArgsConstructor
public class MacroCompletionData {

  public static final String CARET = "<CARET>";

  IElementType keyword;
  String displayName;
  String displayDescription;
  String template;


  public static MacroCompletionData reificationData(@NotNull IElementType keyword, String prefix, String description) {
    return new MacroCompletionData(keyword, prefix + "{...}", description, prefix + "{" + CARET + "}");
  }

  public static MacroCompletionData macroFunctionData(@NotNull IElementType keyword, String prefix, String description) {
    return new MacroCompletionData(keyword, prefix + "(...)", description, prefix + "(" + CARET + ")");
  }
}
