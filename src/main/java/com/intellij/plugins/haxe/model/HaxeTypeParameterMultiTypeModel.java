package com.intellij.plugins.haxe.model;

import com.intellij.plugins.haxe.lang.psi.HaxeType;
import com.intellij.plugins.haxe.lang.psi.impl.HaxeTypeParameterMultiType;
import com.intellij.plugins.haxe.model.type.HaxeTypeResolver;
import com.intellij.plugins.haxe.model.type.ResultHolder;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HaxeTypeParameterMultiTypeModel extends HaxeAnonymousTypeModel{

  HaxeTypeParameterMultiType myMultiType;
  public HaxeTypeParameterMultiTypeModel(@NotNull HaxeTypeParameterMultiType multiType) {
    super(multiType);
    myMultiType = multiType;
  }

  public List<ResultHolder> getCompositeTypes() {
    List<HaxeType> list = myMultiType.getTypeList();
    // TODO cache
    return  list.stream().map(HaxeTypeResolver::getTypeFromType).toList();
  }


}
