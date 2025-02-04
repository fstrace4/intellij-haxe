/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2014 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017-2017 Ilya Malanin
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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.StandardPatterns;
import com.intellij.plugins.haxe.HaxeLanguage;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypeSets;
import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.model.HaxeBaseMemberModel;
import com.intellij.plugins.haxe.model.HaxeEnumValueModel;
import com.intellij.plugins.haxe.model.evaluator.HaxeExpressionEvaluator;
import com.intellij.plugins.haxe.model.type.ResultHolder;
import com.intellij.plugins.haxe.model.type.SpecificHaxeClassReference;
import com.intellij.plugins.haxe.model.type.SpecificTypeReference;
import com.intellij.plugins.haxe.util.HaxeElementGenerator;
import com.intellij.plugins.haxe.util.UsefulPsiTreeUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import icons.HaxeIcons;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.plugins.haxe.ide.completion.HaxeCommonCompletionPattern.*;
import static com.intellij.plugins.haxe.ide.completion.HaxeKeywordCompletionPatterns.*;
import static com.intellij.plugins.haxe.ide.completion.HaxeKeywordCompletionUtil.*;
import static com.intellij.plugins.haxe.ide.completion.HaxeKeywordCompletionUtil.addKeywords;
import static com.intellij.plugins.haxe.ide.completion.KeywordCompletionData.keywordOnly;
import static com.intellij.plugins.haxe.ide.completion.KeywordCompletionData.keywordWithSpace;
import static com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypeSets.*;
import static com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes.*;
import static java.util.function.Predicate.not;

/**
 * @author: Fedor.Korotkov
 */
public class HaxeKeywordCompletionContributor extends CompletionContributor {
  private static final Set<String> allowedKeywords = new HashSet<String>() {
    {
      for (IElementType elementType : HaxeTokenTypeSets.KEYWORDS.getTypes()) {
        add(elementType.toString());
      }
    }
  };
  private static final PsiElementPattern.Capture<PsiElement> afterCaseKeyword =
    psiElement().afterSiblingSkipping(skippableWhitespace, psiElement().withText("case"));

  public HaxeKeywordCompletionContributor() {


    // foo.b<caret> - bad
    // i<caret> - good
    extend(CompletionType.BASIC,
           psiElement().inFile(StandardPatterns.instanceOf(HaxeFile.class))
             .andNot(idInExpression.and(inComplexExpression)),
           new CompletionProvider<>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               suggestKeywords(parameters.getPosition(), result, context);
             }
           });
  }

  private static void suggestKeywords(PsiElement position, @NotNull CompletionResultSet result, ProcessingContext context) {
    List<String> keywordsFromParser = new ArrayList<>();
    final HaxeFile cloneFile = createCopyWithFakeIdentifierAsComment(position, keywordsFromParser);
    PsiElement completionElementAsComment = cloneFile.findElementAt(position.getTextOffset());

    List<LookupElement> lookupElements = new ArrayList<>();

    if(completionElementAsComment != null && completionElementAsComment.getParent() == completionElementAsComment.getContainingFile()) {
      addKeywords(lookupElements, PACKAGE_KEYWORD);
    }

    boolean isPPExpression = psiElement().withElementType(PPEXPRESSION).accepts(position);
    // avoid showing keyword suggestions when not relevant
    if (allowLookupPattern.accepts(completionElementAsComment)) {
      if (!isPPExpression) {
        if (dotFromIterator.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, Set.of(keywordOnly(OTRIPLE_DOT)));
          return;
        }

        if (packageExpected.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, PACKAGE_KEYWORD);
          return;
        }

        if (toplevelScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, TOP_LEVEL_KEYWORDS);
        }

        if (moduleScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, MODULE_STRUCTURES_KEYWORDS);
          addKeywords(lookupElements, VISIBILITY_KEYWORDS);
        }

        if (classDeclarationScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, CLASS_DEFINITION_KEYWORDS);
        }
        if (interfaceDeclarationScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, INTERFACE_DEFINITION_KEYWORDS);
        }

        if (abstractTypeDeclarationScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, ABSTRACT_DEFINITION_KEYWORDS);
        }

        if (interfaceBodyScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, INTERFACE_BODY_KEYWORDS);
        }

        if (classBodyScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, CLASS_BODY_KEYWORDS);
          addKeywords(lookupElements, VISIBILITY_KEYWORDS);
          addKeywords(lookupElements, ACCESSIBILITY_KEYWORDS);
        }


        if (functionBodyScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, METHOD_BODY_KEYWORDS);
          addKeywords(lookupElements, VALUE_KEYWORDS);
        }
        if (initScope.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, VALUE_KEYWORDS);
        }


        if (insideSwitchCase.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, SWITCH_BODY_KEYWORDS);
          addEnumValuesIfSourceIsEnum(completionElementAsComment, lookupElements);

          HaxeSwitchCase type = PsiTreeUtil.getPrevSiblingOfType(completionElementAsComment, HaxeSwitchCase.class);
          if (type!= null) {
            // TODO, solve this using getVariants and walkTree
            addSwitchVars(type, lookupElements);
          }
        }

        if (isAfterIfStatement.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, Set.of(keywordWithSpace(KELSE)));
        }

        if (isInsideLoopBlock.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, LOOP_BODY_KEYWORDS);
        }

        if (isInsideForIterator.accepts(completionElementAsComment)) {
          addKeywords(lookupElements, LOOP_ITERATOR_KEYWORDS);
        }

        HaxePropertyAccessor propertyAccessor = PsiTreeUtil.getParentOfType(position, HaxePropertyAccessor.class);
        if (isPropertyGetterValue.accepts(propertyAccessor)) {
          result.stopHere();
          lookupElements.clear();
          addKeywords(lookupElements, PROPERTY_KEYWORDS, 1.1f);
          addKeywords(lookupElements, Set.of(keywordOnly(PROPERTY_GET)), 1.2f);
        }
        if (isPropertySetterValue.accepts(propertyAccessor)) {
          result.stopHere();
          lookupElements.clear();
          addKeywords(lookupElements, PROPERTY_KEYWORDS, 1.1f);
          addKeywords(lookupElements, Set.of(keywordOnly(PROPERTY_SET)), 1.2f);
        }
      }
      addKeywords(lookupElements, PP_KEYWORDS, -0.2f);
      addKeywords(lookupElements, MISC_KEYWORDS, -0.1f);

    }
    // Remove keyword if if previous sibling is the same keyword (ex. "new new" or "switch switch" does not make sense)
    PsiElement prevSibling = UsefulPsiTreeUtil.getPrevSiblingSkipWhiteSpacesAndComments(completionElementAsComment, false);
    if (prevSibling!= null) {
      lookupElements.removeIf(element -> prevSibling.textMatches(element.getObject().toString()));
    }

    result.addAllElements(lookupElements);
  }

  private static void addSwitchVars(HaxeSwitchCase type, List<LookupElement> lookupElements) {
    Collection<HaxeEnumExtractedValueReference> extractedValues = PsiTreeUtil.findChildrenOfType(type, HaxeEnumExtractedValueReference.class);
    Set<LookupElementBuilder> variables = extractedValues.stream().map(value -> value.getComponentName().getIdentifier().getText())
      .map(lookupString -> LookupElementBuilder.create(lookupString).withIcon(HaxeIcons.Field))
      .collect(Collectors.toSet());
    lookupElements.addAll(variables);

    Collection<HaxeSwitchCaseCaptureVar> captureVar = PsiTreeUtil.findChildrenOfType(type, HaxeSwitchCaseCaptureVar.class);
    Set<LookupElementBuilder> captureVars = captureVar.stream().map(value -> value.getComponentName().getIdentifier().getText())
      .map(lookupString -> LookupElementBuilder.create(lookupString).withIcon(HaxeIcons.Field))
      .collect(Collectors.toSet());
    lookupElements.addAll(captureVars);
  }

  private static void addEnumValuesIfSourceIsEnum(PsiElement completionElementAsComment, List<LookupElement> lookupElements) {
    boolean isAfterCase = afterCaseKeyword.accepts(completionElementAsComment);
    HaxeSwitchStatement parent = PsiTreeUtil.getParentOfType(completionElementAsComment, HaxeSwitchStatement.class);
    if (parent == null) return;

    HaxeExpression expression = parent.getExpression();
    if (expression == null) return;

    ResultHolder holder = HaxeExpressionEvaluator.evaluate(expression, null).result;
    if (!holder.isEnum()) return;

    SpecificTypeReference type = holder.getType();
    if (type instanceof SpecificHaxeClassReference classReference) {
      List<HaxeSwitchCase> list = parent.getSwitchBlock().getSwitchCaseList();
      List<String> alreadyInUse =
        list.stream().map(HaxeSwitchCase::getSwitchCaseExprList).filter(not(List::isEmpty)).map(exprs -> exprs.get(0).getText())
          .toList();
      List<HaxeBaseMemberModel> members = classReference.getHaxeClassModel().getMembers(null);
      for (HaxeBaseMemberModel member : members) {

        if (member instanceof HaxeEnumValueModel model) {
          String name = member.getName();
          if (alreadyInUse.contains(name)) continue;
          String completionText = (isAfterCase ? "" : "case ") + name + " ";

          LookupElementBuilder element = LookupElementBuilder.create(model, completionText)
            .withIcon(HaxeIcons.Enum)
            .withItemTextItalic(true);

          LookupElement element1 = PrioritizedLookupElement.withPriority(element, 10000);
          lookupElements.add(element1);
        }
      }
    }
  }


  private static HaxeFile createCopyWithFakeIdentifierAsComment(PsiElement position, List<String> keywordsFromParser) {

    final HaxeFile posFile = (HaxeFile)position.getContainingFile();
    final TextRange posRange = position.getTextRange();

    // clone original content
    HaxeFile clonedFile = (HaxeFile)posFile.copy();
    int offset = posRange.getStartOffset();

    // replace dummy identifier with comment so it does not affect the parsing and psi structure
    PsiElement dummyIdentifier = clonedFile.findElementAt(offset);
    PsiElement comment = HaxeElementGenerator.createDummyComment(posFile.getProject(), dummyIdentifier.getTextLength());
    PsiElement elementToReplace = dummyIdentifier;

    //make sure we replace the "root" element of the identifier
    // we dont want to replace identifier inside a reference and keep the reference etc.
    while (elementToReplace.getPrevSibling() == null && elementToReplace.getParent() != null) {
      PsiElement parent = elementToReplace.getParent();
      if (parent == clonedFile) break;
      elementToReplace = parent;
    }
    elementToReplace.replace(comment);

    // reparse content
    HaxeFile file = (HaxeFile)PsiFileFactory.getInstance(posFile.getProject()).createFileFromText("a.hx", HaxeLanguage.INSTANCE, clonedFile.getText(), true, false);
    TreeUtil.ensureParsed(file.getNode());
    return file;
  }


}
