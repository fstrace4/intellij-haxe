package com.intellij.plugins.haxe.ide.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.plugins.haxe.HaxeComponentType;
import com.intellij.plugins.haxe.lang.psi.HaxeClass;
import com.intellij.plugins.haxe.lang.psi.HaxeReference;
import com.intellij.plugins.haxe.lang.psi.HaxeResolver;
import com.intellij.plugins.haxe.model.HaxeMemberModel;
import com.intellij.plugins.haxe.util.HaxeAddImportHelper;
import com.intellij.plugins.haxe.util.HaxeElementGenerator;
import com.intellij.plugins.haxe.util.HaxeResolveUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import icons.HaxeIcons;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.intellij.plugins.haxe.ide.lookup.lookupItemImportUtil.*;

@EqualsAndHashCode
public class HaxeStaticMemberLookupElement extends LookupElement implements HaxePsiLookupElement {
  @Getter private final HaxeCompletionPriorityData priority = new HaxeCompletionPriorityData();
  @Getter private final HaxeComponentType type;
  @Getter private final String packageName;
  @Getter private final String className;
  @Getter private final String memberName;
  @Getter private final String typeValue;

  private final Icon icon = HaxeIcons.Method;
  private final String presentableText;
  private final String qname;

  // we need a psi element  when resolving qname (making sure we get data from the right project etc)
  private PsiElement helperPsi;


  public HaxeStaticMemberLookupElement(String packageName,
                                       String className,
                                       String memberName,
                                       HaxeComponentType type,
                                       String typeValue,
                                       PsiElement helperPsi) {
    this.qname = createQname( className, packageName);
    this.packageName = packageName;
    this.className = className;
    this.memberName = memberName;
    this.typeValue = typeValue;
    this.type = type;

    this.presentableText = getLookupString() + " ";

    this.helperPsi = helperPsi;

  }

  @NotNull
  @Override
  public String getLookupString() {
    return className + "." + memberName;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(presentableText);
    presentation.setTypeText(packageName);
    presentation.setIcon(icon);
  }


  @Override
  public void handleInsert(InsertionContext context) {
    PsiFile file = context.getFile();
    PsiElement element = file.findElementAt(context.getStartOffset());
    addImportIfNecessary(context, element, qname);
  }



  @Override
  public @Nullable PsiElement getPsiElement() {
    HaxeClass haxeClass = HaxeResolveUtil.findClassByQName(qname, helperPsi);
    if (haxeClass == null) return null;
    HaxeMemberModel member = haxeClass.getModel().getMember(memberName, null);
    if (member == null) return null;
    return member.getNameOrBasePsi();
  }



  @Override
  public PrioritizedLookupElement<LookupElement> toPrioritized() {
    return (PrioritizedLookupElement<LookupElement>)PrioritizedLookupElement.withPriority(this, priority.calculate());
  }

}
