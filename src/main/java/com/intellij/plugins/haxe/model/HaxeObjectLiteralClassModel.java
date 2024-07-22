package com.intellij.plugins.haxe.model;

import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.lang.psi.impl.HaxeObjectLiteralImpl;
import com.intellij.plugins.haxe.model.type.HaxeGenericResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// when we dont have a type describing an object literal we need to create one,
// we could try to make  object literals into types, but thats a lot of work so for now we just wrap it in a haxeClass subtype
public class HaxeObjectLiteralClassModel extends HaxeClassModel {


  private final HaxeObjectLiteralImpl myLiteral;

  public HaxeObjectLiteralClassModel(@NotNull HaxeObjectLiteralImpl objectLiteral) {
    super(objectLiteral);
    myLiteral = objectLiteral;
  }

  @Nullable
  @Override
  public HaxePsiCompositeElement getBodyPsi() {
    return haxeClass;
  }

  @Override
  public String getName() {
    return "{...}";
  }

  @Override
  public @Nullable HaxeBaseMemberModel getMember(String name, @Nullable HaxeGenericResolver resolver) {
    List<HaxeBaseMemberModel> members = getMembers(resolver);
    for (HaxeBaseMemberModel model : members) {
      if(model.getNamePsi().getIdentifier().textMatches(name)) {
      return model;
      }

    }
    return null;
  }

  @Override
  public List<HaxeBaseMemberModel> getMembers(@Nullable HaxeGenericResolver resolver) {
    List<HaxeObjectLiteralElement> list = myLiteral.getObjectLiteralElementList();
    //filter members that do not have getComponentName (happens when  object literal has members with string identifiers that are not "valid")
    return list.stream().filter(member -> member.getComponentName() != null)
      .map(HaxeObjectLiteralMemberModel::new)
      .map(HaxeBaseMemberModel.class::cast)
      .toList();
  }

  @Override
  public List<HaxeBaseMemberModel> getAllMembers(@Nullable HaxeGenericResolver resolver) {
    return getMembers(resolver);
  }

  @Override
  public @NotNull List<HaxeBaseMemberModel> getMembersSelf() {
    return getMembers(null);
  }

  @Override
  public List<HaxeModel> getExposedMembers() {
    return new ArrayList<>(getMembers(null));
  }

  @Override
  public HaxeFieldModel getField(String name, @Nullable HaxeGenericResolver resolver) {
    return super.getField(name, resolver);
  }
}
