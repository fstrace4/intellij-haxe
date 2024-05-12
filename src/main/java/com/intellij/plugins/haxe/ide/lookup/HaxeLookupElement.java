package com.intellij.plugins.haxe.ide.lookup;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;

public interface HaxeLookupElement {
  HaxeCompletionPriorityData getPriority();
  PrioritizedLookupElement<LookupElement> toPrioritized();
}
