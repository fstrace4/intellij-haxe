/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2023 AS3Boyan
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
package com.intellij.plugins.haxe.ide.formatter;

import com.intellij.formatting.Block;
import com.intellij.formatting.Spacing;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.plugins.haxe.ide.formatter.settings.HaxeCodeStyleSettings;
import com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypeSets;
import com.intellij.plugins.haxe.metadata.util.HaxeMetadataUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import lombok.CustomLog;

import java.util.List;

import static com.intellij.plugins.haxe.lang.lexer.HaxeTokenTypes.*;
import static java.lang.Integer.max;

/**
 * @author: Fedor.Korotkov
 */
@CustomLog
public class HaxeSpacingProcessor {
  private final ASTNode myNode;
  private final CommonCodeStyleSettings mySettings;
  private final HaxeCodeStyleSettings myHaxeCodeStyleSettings;

  public HaxeSpacingProcessor(ASTNode node, CommonCodeStyleSettings settings, HaxeCodeStyleSettings haxeCodeStyleSettings) {
    myNode = node;
    mySettings = settings;
    myHaxeCodeStyleSettings = haxeCodeStyleSettings;
  }

  // Use this for debugging.  Beware: It is incredibly slow to log all of this.
  private String composeSpacingBlockData(Block child1, Block child2) {
    if (!(child1 instanceof AbstractBlock) || !(child2 instanceof AbstractBlock)) {
      return "Children not abstract blocks:" + nodeText(child1) + ", " + nodeText(child2);
    }
    final IElementType elementType = myNode.getElementType();
    final IElementType parentType = myNode.getTreeParent() == null ? null : myNode.getTreeParent().getElementType();
    final ASTNode node1 = ((AbstractBlock)child1).getNode();
    final IElementType type1 = node1.getElementType();
    final ASTNode node2 = ((AbstractBlock)child2).getNode();
    IElementType type2 = node2.getElementType();
    final ASTNode nodeNode1 = node1 == null ? null : node1.getFirstChildNode();
    final IElementType typeType1 = nodeNode1 == null ? null : nodeNode1.getElementType();
    final ASTNode nodeNode2 = node2 == null ? null : node2.getFirstChildNode();
    final IElementType typeType2 = nodeNode2 == null ? null : nodeNode2.getElementType();

    StringBuilder b = new StringBuilder();
    b.append("MyNode:").append(myNode.toString());
    b.append(" ElementType:").append(elementType);
    b.append(" ParentType:").append(parentType);

    b.append("\n Child1:");
    b.append(" Node1:").append(node1);
    b.append(" Type1:").append(type1);
    b.append(" FirstChildNode:").append(nodeNode1);
    b.append(" FirstChildType:").append(typeType1);

    b.append("\n Child2:");
    b.append(" Node2:").append(node2);
    b.append(" Type2:").append(type2);
    b.append(" FirstChildNode:").append(nodeNode2);
    b.append(" FirstChildType:").append(typeType2);

    return b.toString();
  }

  private String nodeText(Block child) {
    String name = null == child ? "<null child>" : (child instanceof HaxeBlock) ? ((HaxeBlock)child).getDebugName() : null;
    if (null == name && child instanceof AbstractBlock) name = ((AbstractBlock)child).getNode().getText();
    if (null == name) name = child.getClass().getName();
    return name;
  }

  private String composeSpacingData(Block child1, Block child2, Spacing spacing) {
    String sp = null != spacing ? spacing.toString() : "<null spacing>";
    return "Between " + nodeText(child1) + " and " + nodeText(child2) + ", spacing is " + sp;
  }

  public Spacing getSpacing(Block child1, Block child2) {
    if (log.isTraceEnabled()) {
      log.trace(composeSpacingBlockData(child1, child2));
    }
    Spacing spacing = getSpacingInternal(child1, child2);
    if (log.isDebugEnabled()) {
      log.debug(composeSpacingData(child1, child2, spacing));
    }
    return spacing;
  }

  public Spacing getSpacingInternal(Block child1, Block child2) {
    if (!(child1 instanceof AbstractBlock) || !(child2 instanceof AbstractBlock)) {
      return null;
    }

    final IElementType elementType = myNode.getElementType();
    final IElementType parentType = myNode.getTreeParent() == null ? null : myNode.getTreeParent().getElementType();
    final ASTNode node1 = ((AbstractBlock)child1).getNode();
    final IElementType type1 = node1.getElementType();
    final ASTNode node2 = ((AbstractBlock)child2).getNode();
    IElementType type2 = node2.getElementType();
    final ASTNode nodeNode1 = node1 == null ? null : node1.getFirstChildNode();
    final IElementType typeType1 = nodeNode1 == null ? null : nodeNode1.getElementType();
    final ASTNode nodeNode2 = node2 == null ? null : node2.getFirstChildNode();
    final IElementType typeType2 = nodeNode2 == null ? null : nodeNode2.getElementType();

    // TODO: Add Metadata spacing rules AND associated UI.
    //  (When looking for examples, Java code uses the word "Annotations".)

    // TODO: Do this for comments, too??
    // If type2 is metadata, then camouflage it as the type that follows it.

    if (type2 == METADATA_DECLARATION) {
      PsiElement element = HaxeMetadataUtils.getAssociatedElement(node2.getPsi());
      if (null != element) {
        type2 = element.getNode().getElementType();
      }
    }

    if (type1 == IMPORT_STATEMENT ||
        type1 == PACKAGE_STATEMENT ||
        type1 == USING_STATEMENT) {
      return addSingleSpaceIf(false, true);
    }

    if (elementType.equals(IMPORT_WILDCARD)) {
      return addSingleSpaceIf(false);
    }

    if (isClassDeclaration(elementType) && isClassBodyType(type2)) {
      return setBraceSpace(mySettings.SPACE_BEFORE_CLASS_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
    }

    if (isClassDeclaration(type1)) {
      return Spacing.createSpacing(0, 0, mySettings.BLANK_LINES_AROUND_CLASS, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (isClassBodyType(type1)) {  // End of the class body. (After the right brace.)
      return Spacing.createSpacing(0, 0, 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (type1 == ENCLOSURE_CURLY_BRACKET_LEFT && isClassBodyType(elementType) && isFirstChild(child1)) {
      int lineFeeds = max(1, (isFieldDeclaration(type2) ? mySettings.BLANK_LINES_AROUND_FIELD : mySettings.BLANK_LINES_AROUND_METHOD));
      return Spacing.createSpacing(0, 0, lineFeeds, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (type2 == ENCLOSURE_CURLY_BRACKET_RIGHT && isClassBodyType(elementType) && isLastChild(child2)) {
      return Spacing.createSpacing(0, 0, max(1, mySettings.BLANK_LINES_BEFORE_CLASS_END), mySettings.KEEP_LINE_BREAKS,
                                   mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (isMethodDeclaration(type1) && isMethodDeclaration(type2)) {
      return Spacing.createSpacing(0, 0, 1 + mySettings.BLANK_LINES_AROUND_METHOD, mySettings.KEEP_LINE_BREAKS,
                                   mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (isMethodDeclaration(type1) && isFieldDeclaration(type2)) {
      return Spacing.createSpacing(0, 0, 1 + mySettings.BLANK_LINES_AROUND_METHOD, mySettings.KEEP_LINE_BREAKS,
                                   mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (isFieldDeclaration(type1) && isFieldDeclaration(type2)) {
      return Spacing.createSpacing(0, 0, 1 + mySettings.BLANK_LINES_AROUND_FIELD, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (isFieldDeclaration(type1)&& isMethodDeclaration(type2)) {
      return Spacing.createSpacing(0, 0, 1 + mySettings.BLANK_LINES_AROUND_METHOD, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (HaxeTokenTypeSets.DOC_COMMENT == type1) {
      return Spacing.createSpacing(0,0,1,false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (HaxeTokenTypeSets.ONLY_COMMENTS.contains(type1) && (isMethodDeclaration(type2) || isFieldDeclaration(type2))) { // prevent excess linefeed between doctype and function
      return Spacing.createSpacing(0, 0, 1, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (type2 == ENCLOSURE_PARENTHESIS_LEFT) {
      if (elementType == GUARD) { // IF_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_IF_PARENTHESES);
      }
      else if (elementType == WHILE_STATEMENT || elementType == DO_WHILE_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_WHILE_PARENTHESES);
      }
      else if (elementType == FOR_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_FOR_PARENTHESES);
      }
      else if (elementType == SWITCH_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_SWITCH_PARENTHESES);
      }
      else if (elementType == TRY_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_TRY_PARENTHESES);
      }
      else if (elementType == CATCH_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_CATCH_PARENTHESES);
      }
      else if (HaxeTokenTypeSets.FUNCTION_DEFINITION.contains(elementType)) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_METHOD_PARENTHESES);
      }
      else if (elementType == CALL_EXPRESSION) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES);
      }
    }

    if (type1 == OPERATOR_GREATER && type2 == OPERATOR_ASSIGN) {
      return addSingleSpaceIf(false);
    }

    //
    //Spacing before left braces
    //
    // NOTE: BLOCK_STATEMENTs that are a single sub-element of an enclosing block,
    //       such as GUARDED_STATEMENT or DO_WHILE_BODY are presented as the enclosing
    //       statement type and are NOT presented as a separate BLOCK_STATEMENT sub-element.
    //
    if (elementType == IF_STATEMENT) {
      if (type2 == GUARDED_STATEMENT && typeType2 == BLOCK_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_IF_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
    }
    if (type2 == DO_WHILE_BODY && typeType2 == BLOCK_STATEMENT) {
      if (elementType == WHILE_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_WHILE_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
      else if(elementType == DO_WHILE_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_DO_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
    }
    if (type2 == BLOCK_STATEMENT) {
      if (elementType == ELSE_STATEMENT) { // else if (elementType == IF_STATEMENT && type1 == KELSE) {
        return setBraceSpace(mySettings.SPACE_BEFORE_ELSE_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
      else if (elementType == FOR_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_FOR_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
      else if (elementType == SWITCH_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_SWITCH_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
      else if (elementType == TRY_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_TRY_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
      else if (elementType == CATCH_STATEMENT) {
        return setBraceSpace(mySettings.SPACE_BEFORE_CATCH_LBRACE, mySettings.BRACE_STYLE, child1.getTextRange());
      }
      else if (HaxeTokenTypeSets.FUNCTION_DEFINITION.contains(elementType)) {
        return setBraceSpace(mySettings.SPACE_BEFORE_METHOD_LBRACE, mySettings.METHOD_BRACE_STYLE, child1.getTextRange());
      }
    }

    if (type1 == ENCLOSURE_PARENTHESIS_LEFT || type2 == ENCLOSURE_PARENTHESIS_RIGHT) {
      if (elementType == GUARD) { // if (elementType == IF_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_IF_PARENTHESES);
      }
      else if (elementType == WHILE_STATEMENT || elementType == DO_WHILE_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_WHILE_PARENTHESES);
      }
      else if (elementType == FOR_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_FOR_PARENTHESES);
      }
      else if (elementType == SWITCH_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_SWITCH_PARENTHESES);
      }
      else if (elementType == TRY_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_TRY_PARENTHESES);
      }
      else if (elementType == CATCH_STATEMENT) {
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_CATCH_PARENTHESES);
      }
      else if (HaxeTokenTypeSets.FUNCTION_DEFINITION.contains(elementType)) {
        final boolean newLineNeeded = type1 == ENCLOSURE_PARENTHESIS_LEFT ?
                                      mySettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE :
                                      mySettings.METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE;
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_METHOD_PARENTHESES, newLineNeeded);
      }
      else if (elementType == CALL_EXPRESSION) {
        final boolean newLineNeeded = type1 == ENCLOSURE_PARENTHESIS_LEFT ?
                                      mySettings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE :
                                      mySettings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
        return addSingleSpaceIf(mySettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES, newLineNeeded);
      }
      else if (mySettings.BINARY_OPERATION_WRAP != CommonCodeStyleSettings.DO_NOT_WRAP && elementType == PARENTHESIZED_EXPRESSION) {
        final boolean newLineNeeded = type1 == ENCLOSURE_PARENTHESIS_LEFT ?
                                      mySettings.PARENTHESES_EXPRESSION_LPAREN_WRAP :
                                      mySettings.PARENTHESES_EXPRESSION_RPAREN_WRAP;
        return addSingleSpaceIf(false, newLineNeeded);
      }
    }

    if (elementType == TERNARY_EXPRESSION) {
      if (type2 == OPERATOR_TERNARY) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_QUEST);
      }
      else if (type2 == OPERATOR_COLON) {
        return addSingleSpaceIf(mySettings.SPACE_BEFORE_COLON);
      }
      else if (type1 == OPERATOR_TERNARY) {
        return addSingleSpaceIf(mySettings.SPACE_AFTER_QUEST);
      }
      else if (type1 == OPERATOR_COLON) {
        return addSingleSpaceIf(mySettings.SPACE_AFTER_COLON);
      }
    }

    //
    // Spacing around assignment operators (=, -=, etc.)
    //

    if (HaxeTokenTypeSets.ASSIGN_OPERATORS.contains(type1) || HaxeTokenTypeSets.ASSIGN_OPERATORS.contains(type2) ||
        type2 == VAR_INIT) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_ASSIGNMENT_OPERATORS);
    }

    //
    // Spacing around  logical operators (&&, OR, etc.)
    //
    if (HaxeTokenTypeSets.LOGIC_OPERATORS.contains(type1) || HaxeTokenTypeSets.LOGIC_OPERATORS.contains(type2)) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_LOGICAL_OPERATORS);
    }
    //
    // Spacing around  equality operators (==, != etc.)
    //
    if ((type1 == COMPARE_OPERATION && HaxeTokenTypeSets.EQUALITY_OPERATORS.contains(typeType1)) ||
        (type2 == COMPARE_OPERATION && HaxeTokenTypeSets.EQUALITY_OPERATORS.contains(typeType2))) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_EQUALITY_OPERATORS);
    }
    //
    // Spacing around  relational operators (<, <= etc.)
    //
    if ((type1 == COMPARE_OPERATION && HaxeTokenTypeSets.RELATIONAL_OPERATORS.contains(typeType1)) ||
        (type2 == COMPARE_OPERATION && HaxeTokenTypeSets.RELATIONAL_OPERATORS.contains(typeType2))) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_RELATIONAL_OPERATORS);
    }
    //
    // Spacing around  additive operators ( &, |, ^, etc.)
    //
    if (HaxeTokenTypeSets.BITWISE_OPERATORS.contains(type1) || HaxeTokenTypeSets.BITWISE_OPERATORS.contains(type2)) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_BITWISE_OPERATORS);
    }
    //
    // Spacing around  additive operators ( +, -, etc.)
    //
    if ((HaxeTokenTypeSets.ADDITIVE_OPERATORS.contains(type1) || HaxeTokenTypeSets.ADDITIVE_OPERATORS.contains(type2)) &&
        elementType != PREFIX_EXPRESSION) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_ADDITIVE_OPERATORS);
    }
    //
    // Spacing around  multiplicative operators ( *, /, %, etc.)
    //
    if ((HaxeTokenTypeSets.MULTIPLICATIVE_OPERATORS.contains(type1) || HaxeTokenTypeSets.MULTIPLICATIVE_OPERATORS.contains(type2))) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_MULTIPLICATIVE_OPERATORS);
    }
    //
    // Spacing around  unary operators ( NOT, ++, etc.)
    //
    if ((HaxeTokenTypeSets.UNARY_OPERATORS.contains(type1) || HaxeTokenTypeSets.UNARY_OPERATORS.contains(type2)) &&
        elementType == PREFIX_EXPRESSION) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_UNARY_OPERATOR);
    }
    //
    // Spacing around  shift operators ( <<, >>, >>>, etc.)
    //
    if (HaxeTokenTypeSets.SHIFT_OPERATORS.contains(type1) || HaxeTokenTypeSets.SHIFT_OPERATORS.contains(type2)) {
      return addSingleSpaceIf(mySettings.SPACE_AROUND_SHIFT_OPERATORS);
    }

    //
    //Spacing before keyword (else, catch, etc)
    //
    if (type2 == ELSE_STATEMENT) {
      return addSingleSpaceIf(mySettings.SPACE_BEFORE_ELSE_KEYWORD, mySettings.ELSE_ON_NEW_LINE);
    }
    if (type2 == KEYWORD_WHILE) {
      return addSingleSpaceIf(mySettings.SPACE_BEFORE_WHILE_KEYWORD, mySettings.WHILE_ON_NEW_LINE);
    }
    if (type2 == CATCH_STATEMENT) {
      return addSingleSpaceIf(mySettings.SPACE_BEFORE_CATCH_KEYWORD, mySettings.CATCH_ON_NEW_LINE);
    }

    //
    //Other
    //

    if (type1 == KEYWORD_ELSE && type2 == IF_STATEMENT) {  // Inside of ELSE_STATEMENT
      return Spacing.createSpacing(1, 1, mySettings.SPECIAL_ELSE_IF_TREATMENT ? 0 : 1, false, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }

    if (type1 == OPERATOR_COMMA && (elementType == PARAMETER_LIST || elementType == EXPRESSION_LIST) &&
        (parentType == CALL_EXPRESSION ||
         parentType == NEW_EXPRESSION ||
         HaxeTokenTypeSets.FUNCTION_DEFINITION.contains(parentType))) {
      return addSingleSpaceIf(mySettings.SPACE_AFTER_COMMA_IN_TYPE_ARGUMENTS);
    }

    if (type1 == OPERATOR_COMMA) {
      return addSingleSpaceIf(mySettings.SPACE_AFTER_COMMA);
    }

    if (type2 == OPERATOR_COMMA) {
      return addSingleSpaceIf(mySettings.SPACE_BEFORE_COMMA);
    }

    if (type1 == OPERATOR_COLON && elementType == TYPE_TAG) {
      return addSingleSpaceIf(myHaxeCodeStyleSettings.SPACE_AFTER_TYPE_REFERENCE_COLON);
    }

    if (type2 == TYPE_TAG) {
      return addSingleSpaceIf(myHaxeCodeStyleSettings.SPACE_BEFORE_TYPE_REFERENCE_COLON);
    }

    if (type1 == OPERATOR_ARROW || type2 == OPERATOR_ARROW) {
      return addSingleSpaceIf(myHaxeCodeStyleSettings.SPACE_AROUND_ARROW);
    }

    return Spacing.createSpacing(0, 1, 0, true, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private Spacing addSingleSpaceIf(boolean condition) {
    return addSingleSpaceIf(condition, false);
  }

  private Spacing addSingleSpaceIf(boolean condition, boolean linesFeed) {
    final int spaces = condition ? 1 : 0;
    final int lines = linesFeed ? 1 : 0;
    return Spacing.createSpacing(spaces, spaces, lines, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
  }

  private Spacing setBraceSpace(boolean needSpaceSetting,
                                @CommonCodeStyleSettings.BraceStyleConstant int braceStyleSetting,
                                TextRange textRange) {
    final int spaces = needSpaceSetting ? 1 : 0;
    if (braceStyleSetting == CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED && textRange != null) {
      return Spacing.createDependentLFSpacing(spaces, spaces, textRange, mySettings.KEEP_LINE_BREAKS, mySettings.KEEP_BLANK_LINES_IN_CODE);
    }
    else {
      final int lineBreaks = braceStyleSetting == CommonCodeStyleSettings.END_OF_LINE ||
                             braceStyleSetting == CommonCodeStyleSettings.NEXT_LINE_IF_WRAPPED ? 0 : 1;
      return Spacing.createSpacing(spaces, spaces, lineBreaks, false, 0);
    }
  }

  private Spacing setStatementSpacing(int minSpaces, int maxSpaces, int minLineFeeds, boolean keepLineBreaks, int keepBlankLines) {
    int lineFeeds = 1 +  minLineFeeds;
    return Spacing.createSpacing(minSpaces, maxSpaces, lineFeeds, keepLineBreaks, keepBlankLines);
  }

  private boolean isClassBodyType(IElementType type) {
    return HaxeTokenTypeSets.CLASS_BODY_TYPES.contains(type);
  }

  private boolean isClassDeclaration(IElementType type) {
    return HaxeTokenTypeSets.CLASS_TYPES.contains(type);
  }

  private boolean blockBeginsWith(Block block, IElementType type) {
    if (null == block && null == type) return false;
    List<Block> subBlocks = block.getSubBlocks();
    if (!subBlocks.isEmpty()) {
      Block first = subBlocks.get(0);
      final ASTNode node = ((AbstractBlock)first).getNode();
      return node.getElementType() == type;
    }
    return false;
  }

  private boolean isFirstChild(Block block) {
    return ((AbstractBlock)block).getNode() == myNode.getFirstChildNode();
  }

  private boolean isLastChild(Block block) {
    return ((AbstractBlock)block).getNode() == myNode.getLastChildNode();
  }

  private boolean isFieldDeclaration(IElementType type) {
    // Sometimes, the field declaration rule gets matched as a LOCAL_VAR_DECLARATION_LIST (in its minimal form)
    // during an incremental reparse, because the parser doesn't have the class vs. method context at that point.

    return type == FIELD_DECLARATION
        || type == LOCAL_VAR_DECLARATION_LIST;
  }

  private boolean isMethodDeclaration(IElementType type) {
    return type == METHOD_DECLARATION;
  }
}
