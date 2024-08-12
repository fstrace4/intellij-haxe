package com.intellij.plugins.haxe.model.type;

import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.PsiElement;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
public class HaxeAssignContext {

  @Nullable PsiElement toOrigin;
  @Nullable PsiElement fromOrigin;

  //NOTE: bit of a hack maybe.
  // helper flag to tell  canAssign check that target is a constraint. this to allow enumTypeParameters to be assigned to EnumValue
  private boolean constraintCheck = false;

  public HaxeAssignContext(@Nullable PsiElement toOrigin, @Nullable PsiElement fromOrigin) {
    this.toOrigin = toOrigin;
    this.fromOrigin = fromOrigin;
  }

  final List<String> missingMembers = new ArrayList<>();
  final Map<String, String> wrongTypeMembers = new HashMap<>();

  public void addMissingMember(String name) {
    missingMembers.add(name);
  }
  public void addWrongTypeMember(String have, String wants) {
    wrongTypeMembers.put(have, wants);
  }

  public boolean hasMissingMembers() {
    return !missingMembers.isEmpty();
  }
  public boolean hasWrongTypeMembers() {
    return !wrongTypeMembers.isEmpty();
  }

  public String getMissingMembersString() {
    return Strings.join(getMissingMembers(), ", ");
  }
  public String geWrongTypeMembersString() {
    return  getWrongTypeMembers().entrySet().stream()
      .map(entry -> "have '" + entry.getKey() + "' wants '" + entry.getValue() + "'")
      .collect(Collectors.joining(", "));
  }

  public void setConstraintCheck(boolean constraintCheck) {
    this.constraintCheck = constraintCheck;
  }
  public boolean isConstraintCheck() {
    return constraintCheck;
  }
}
