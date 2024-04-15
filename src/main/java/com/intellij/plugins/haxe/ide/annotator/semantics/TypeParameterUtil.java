package com.intellij.plugins.haxe.ide.annotator.semantics;

import com.intellij.plugins.haxe.lang.psi.HaxeMethod;
import com.intellij.plugins.haxe.model.HaxeClassModel;
import com.intellij.plugins.haxe.model.HaxeGenericParamModel;
import com.intellij.plugins.haxe.model.HaxeMethodModel;
import com.intellij.plugins.haxe.model.HaxeParameterModel;
import com.intellij.plugins.haxe.model.type.HaxeGenericResolver;
import com.intellij.plugins.haxe.model.type.HaxeTypeResolver;
import com.intellij.plugins.haxe.model.type.ResultHolder;
import com.intellij.plugins.haxe.model.type.SpecificFunctionReference;
import com.intellij.plugins.haxe.model.type.resolver.ResolveSource;
import org.jetbrains.annotations.NotNull;

import java.util.*;


public class TypeParameterUtil {

  @NotNull
  public static TypeParameterTable createTypeParameterConstraintTable(HaxeMethod method, HaxeGenericResolver resolver) {

    TypeParameterTable typeParamTable = new TypeParameterTable();

    HaxeMethodModel methodModel = method.getModel();
    if (methodModel != null) {
      List<HaxeGenericParamModel> params = methodModel.getGenericParams();
      for (HaxeGenericParamModel model : params) {
        ResultHolder constraint = model.getConstraint(resolver);
        typeParamTable.put(model.getName(), constraint, ResolveSource.METHOD_TYPE_PARAMETER);
      }
      if (method.isConstructor()) {
        HaxeClassModel declaringClass = method.getModel().getDeclaringClass();
        if (declaringClass != null) {
          params = declaringClass.getGenericParams();
          for (HaxeGenericParamModel model : params) {
            ResultHolder constraint = model.getConstraint(resolver);
            typeParamTable.put(model.getName(), constraint, ResolveSource.CLASS_TYPE_PARAMETER);
          }
        }
      }

      HaxeClassModel declaringClass = methodModel.getDeclaringClass();
      if (declaringClass != null) {
        List<HaxeGenericParamModel> classParams = declaringClass.getGenericParams();
        for (HaxeGenericParamModel model : classParams) {
          ResultHolder constraint = model.getConstraint(resolver);
          // make sure we do not add if method type parameter with the same name is present
          if(!typeParamTable.contains(model.getName(), ResolveSource.METHOD_TYPE_PARAMETER)) {
            typeParamTable.put(model.getName(), constraint, ResolveSource.CLASS_TYPE_PARAMETER);
          }
        }
      }
    }
    return typeParamTable;
  }

  public static void applyCallieConstraints(TypeParameterTable table, HaxeGenericResolver callieResolver) {
    HaxeGenericResolver resolver = new HaxeGenericResolver();
    resolver.addAll(callieResolver, ResolveSource.CLASS_TYPE_PARAMETER);

    for (String name : resolver.names()) {
      if(table.contains(name, ResolveSource.CLASS_TYPE_PARAMETER)) {
        ResultHolder resolve = resolver.resolve(name);
        table.put(name, resolve, ResolveSource.CLASS_TYPE_PARAMETER);
      }
    }
  }


  @NotNull
  static TypeParameterTable  createTypeParameterConstraintTable(List<HaxeGenericParamModel> modelList,
                                                                           HaxeGenericResolver resolver) {
    TypeParameterTable typeParamTable = new TypeParameterTable();
    for (HaxeGenericParamModel model : modelList) {
      ResultHolder constraint = model.getConstraint(resolver);
      if (constraint != null && constraint.isUnknown()) {
        typeParamTable.put(model.getName(), constraint, ResolveSource.CLASS_TYPE_PARAMETER);
      }
    }
    return typeParamTable;
  }

  static boolean containsTypeParameter(@NotNull ResultHolder parameterType, @NotNull TypeParameterTable typeParamTable) {
    if (parameterType.getClassType() != null) {
      if (parameterType.getClassType().isTypeParameter()) return true;

      ResultHolder[] specifics = parameterType.getClassType().getSpecifics();
      if (specifics.length == 0) {
        return typeParamTable.contains(parameterType.getClassType().getClassName());
      }
      List<ResultHolder> recursionGuard = new ArrayList<>();

      for (ResultHolder specific : specifics) {
        if (specific.isClassType()) {
          recursionGuard.add(specific);
          List<ResultHolder> list = getSpecificsIfClass(specific, recursionGuard);
          if (list.stream()
            .map(holder -> holder.getClassType().getClassName())
            .anyMatch(typeParamTable::contains)) {
            return true;
          }
        }
      }
    }else if (parameterType.getFunctionType() != null) {
      SpecificFunctionReference fn = parameterType.getFunctionType();
      List<SpecificFunctionReference.Argument> arguments = fn.getArguments();
      if (arguments != null) {
        boolean anyMatch = arguments.stream().anyMatch(argument -> containsTypeParameter(argument.getType(), typeParamTable));
        if(anyMatch) return true;
      }
      return containsTypeParameter(fn.getReturnType(), typeParamTable);
    }

    return false;
  }
  static Optional<ResultHolder> findConstraintForTypeParameter(HaxeParameterModel parameter, @NotNull ResultHolder parameterType, @NotNull TypeParameterTable typeParamTable) {
    if (!parameterType.isClassType()) return Optional.empty();

    ResultHolder[] specifics = parameterType.getClassType().getSpecifics();
    if (specifics.length == 0){
      String className = parameterType.getClassType().getClassName();
      return typeParamTable.contains(className) ? Optional.ofNullable(typeParamTable.get(className)) : Optional.empty();
    }
    List<ResultHolder> result = new ArrayList<>();
    List<ResultHolder> recursionGuard = new ArrayList<>();
    HaxeGenericResolver resolver = new HaxeGenericResolver();
    for (ResultHolder specific : specifics) {
      if (specific.isClassType()) {
        recursionGuard.add(specific);
        result.addAll(getSpecificsIfClass(specific, recursionGuard));
      }
    }



    result.stream().map(holder -> holder.getClassType().getClassName())
      .filter(typeParamTable::contains)
      .filter( s ->  typeParamTable.get(s) != null)
      .forEach( s ->  resolver.addConstraint(s, typeParamTable.get(s), ResolveSource.CLASS_TYPE_PARAMETER));

    ResultHolder type = HaxeTypeResolver.getTypeFromTypeTag(parameter.getTypeTagPsi(), parameter.getNamePsi());
    if (type.isTypeParameter()) {
      return Optional.ofNullable(resolver.resolve(type));
    }

    return Optional.ofNullable(resolver.resolve(parameterType));

  }

  private static List<ResultHolder> getSpecificsIfClass(@NotNull ResultHolder holder, List<ResultHolder> recursionGuard) {
    @NotNull ResultHolder[] specifics = holder.getClassType().getSpecifics();
    if (specifics.length == 0) return List.of(holder);
    List<ResultHolder> result = new ArrayList<>();
    for (ResultHolder specific : specifics) {
      if (specific.isClassType() && !recursionGuard.contains(specific)) {
        recursionGuard.add(specific);
        result.addAll(getSpecificsIfClass(specific, recursionGuard));
      }
    }
  return result;
  }
}
