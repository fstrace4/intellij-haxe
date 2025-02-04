/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2017 AS3Boyan
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
package com.intellij.plugins.haxe.ide.completion;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.plugins.haxe.config.sdk.HaxeSdkAdditionalDataBase;
import com.intellij.plugins.haxe.config.sdk.HaxeSdkUtil;
import com.intellij.plugins.haxe.ide.lookup.HaxeIndexedClassElement;
import com.intellij.plugins.haxe.ide.lookup.HaxeLookupElement;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Runs all of the completion contributors and filters/massages the results.  Note that this
 * should be loaded first, before all other Haxe contributors.
 * <p>
 * Created by ebishton on 2/23/17.
 */
public class HaxeControllingCompletionContributor extends CompletionContributor {

  public HaxeControllingCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(HaxeTokenTypes.ID)
             .withParent(HaxeIdentifier.class)
             .withSuperParent(2, HaxeReferenceExpression.class),

           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {

               // Run all of the providers so that we can capture all of their results.
               LinkedHashSet<CompletionResult> unfilteredCompletions = result.runRemainingContributors(parameters, false);
               // Now filter out duplicates, etc.
               Set<CompletionResult> filteredCompletions = filter(parameters, unfilteredCompletions);


               filteredCompletions =  HaxeCompletionPriorityUtil.calculatePriority(filteredCompletions, parameters);
               filteredCompletions.stream()
                 .map(HaxeCompletionPriorityUtil::convertToPrioritized)
                 .forEach(result::passResult); // Add everything we want to keep to the result set.

               // resolving PSI elements for index items is too slow for us to get correct item sorting (involves file parsing)
               // we still want the PsiReference for documentation lookups, but we dont strictly need it
               // for the sorting even tho it would be nice to also get the proximity sorting.
               updatePsiElementValues(filteredCompletions);

               // TODO mlo: suggest lambda / function when expected type is  functionType

               //TODO mlo: mechanism for filtering getters and setters ( get_X / set_x)  when properties exists ? (could be that noCompletion solves this)

               // Since we've already run all of the providers, don't let them be repeated.

               result.stopHere();
             }
           });
  }

  private static void updatePsiElementValues(Set<CompletionResult> filteredCompletions) {
    ReadAction.run(() -> {
      filteredCompletions.stream()
        .filter(r -> r.getLookupElement() instanceof HaxeIndexedClassElement)
        .map(r -> (HaxeIndexedClassElement)r.getLookupElement())
        .forEach(HaxeIndexedClassElement::updatePsiElement);
    });
  }

  private static Set<CompletionResult> filter(@NotNull CompletionParameters parameters,
                                              Set<CompletionResult> unfilteredCompletions) {

    if (null == unfilteredCompletions || unfilteredCompletions.size() <= 1) {
      // Nothing to filter.
      return unfilteredCompletions;
    }

    Set<CompletionResult> filtered = unfilteredCompletions;

    if (shouldRemoveDuplicateCompletions(parameters.getOriginalFile())) {
      filtered = removeDuplicateCompletions(unfilteredCompletions);
    }

    return filtered;
  }

  private static String getDedupeName(CompletionResult candidate) {
    LookupElement el = candidate.getLookupElement();
    if (el == null) return null;
    // we don't want to filter away classes with similar names we want to show classes from different packages and/or libs
    // for now we try use fully Qualified name for classes but this might break de-duping for compiler completion
    if (el.getObject() instanceof HaxeComponentName element) {
      if (element.getParent() instanceof HaxeClass haxeClass) {
        return haxeClass.getQualifiedName();
      }
    }
    else if (el.getObject() instanceof String stringValue) {
      return stringValue;
    }
    return el.getLookupString();
  }

  private static boolean shouldRemoveDuplicateCompletions(PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    if (null != vFile) { // Can't use in-memory file. TODO: Allow in-memory file for compiler completion.
      Module module = ModuleUtil.findModuleForFile(vFile, file.getProject());

      HaxeSdkAdditionalDataBase sdkData = HaxeSdkUtil.getSdkData(module);
      if (null != sdkData) {
        return sdkData.getRemoveCompletionDuplicatesFlag();
      }
    }
    return true;
  }

  private static Set<CompletionResult> removeDuplicateCompletions(Set<CompletionResult> unfilteredCompletions) {
    //TODO not sure if this is preferred now that resolving works better, but if compiler data contains all necessary data then maybe

    // We sort the elements according to name, giving preference to compiler-provided results
    // if the two names are identical.
    CompletionResult sorted[] = unfilteredCompletions.toArray(new CompletionResult[]{});
    Arrays.sort(sorted, new Comparator<CompletionResult>() {
      @Override
      public int compare(CompletionResult o1, CompletionResult o2) {
        LookupElement el1 = o1.getLookupElement();
        LookupElement el2 = o2.getLookupElement();
        String fnName1 = null != el1 ? el1.getLookupString() : null;
        String fnName2 = null != el2 ? el2.getLookupString() : null;

        // Can't really throw a null pointer exception... It would be thrown past sort.
        if (null == fnName1) return -1;
        if (null == fnName2) return 1;

        int comp = fnName1.compareTo(fnName2);
        if (0 == comp) {
          Object obj1 = el1.getObject();
          Object obj2 = el2.getObject();

          comp = obj1 instanceof HaxeCompilerCompletionItem
                 ? (obj2 instanceof HaxeCompilerCompletionItem ? 0 : -1)
                 : (obj2 instanceof HaxeCompilerCompletionItem ? 1 : 0);
        }
        return comp;
      }
    });

    // Now remove duplicates by looping over the list, dropping any that match the entry prior.
    ArrayList<CompletionResult> deduped = new ArrayList<CompletionResult>();
    String lastName = null;
    for (CompletionResult next : sorted) {

      String nextName = getDedupeName(next);
      // In the long run, it's probably not good enough just to check the name.  Multiple argument types may
      // be present, and we may be able to filter based on the local variables available.
      if (null == lastName || !lastName.equals(nextName)) {
        deduped.add(next);
      }
      //TODO
      // avoiding de-duping HaxeLookupElement here, its primarily results from  compiler that needs to be removed
      // de-duping only based on name here will remove classes with same name different package, and we want to avoid that.
      // we need a better way to solve the compiler results issue
      if (next.getLookupElement() instanceof HaxeLookupElement) {
        deduped.add(next);
      }
      lastName = nextName;
    }
    return Sets.newCopyOnWriteArraySet(deduped);
  }
}
