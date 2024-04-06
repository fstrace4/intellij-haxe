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
  public static Map<String, ResultHolder> createTypeParameterConstraintMap(HaxeMethod method, HaxeGenericResolver resolver) {
    Map<String, ResultHolder> typeParamMap = new HashMap<>();

    HaxeMethodModel methodModel = method.getModel();
    if (methodModel != null) {
      List<HaxeGenericParamModel> params = methodModel.getGenericParams();
      for (HaxeGenericParamModel model : params) {
        ResultHolder constraint = model.getConstraint(resolver);
        typeParamMap.put(model.getName(), constraint);
      }
      if(method.isConstructor()) {
        params = method.getModel().getDeclaringClass().getGenericParams();
        for (HaxeGenericParamModel model : params) {
          ResultHolder constraint = model.getConstraint(resolver);
          typeParamMap.put(model.getName(), constraint);
        }
      }

      HaxeClassModel declaringClass = methodModel.getDeclaringClass();
      if (declaringClass != null) {
        List<HaxeGenericParamModel> classParams = declaringClass.getGenericParams();
        for (HaxeGenericParamModel model : classParams) {
          ResultHolder constraint = model.getConstraint(resolver);
          typeParamMap.put(model.getName(), constraint);
        }
      }
    }
    return typeParamMap;
  }

  public static void applyCallieConstraints(Map<String, ResultHolder> map, HaxeGenericResolver callieResolver) {
    HaxeGenericResolver resolver = new HaxeGenericResolver();
    resolver.addAll(callieResolver, ResolveSource.CLASS_TYPE_PARAMETER);

    for (String name : resolver.names()) {
      ResultHolder resolve = resolver.resolve(name);
      map.put(name, resolve);
    }
  }


  @NotNull
  static Map<String, ResultHolder> createTypeParameterConstraintMap(List<HaxeGenericParamModel> modelList,
                                                                           HaxeGenericResolver resolver) {
    Map<String, ResultHolder> typeParamMap = new HashMap<>();
    for (HaxeGenericParamModel model : modelList) {
      ResultHolder constraint = model.getConstraint(resolver);
      if (constraint != null && constraint.isUnknown()) {
        typeParamMap.put(model.getName(), constraint);
      }
    }
    return typeParamMap;
  }

  static boolean containsTypeParameter(@NotNull ResultHolder parameterType, @NotNull Map<String, ResultHolder> typeParamMap) {
    if (parameterType.getClassType() != null) {
      if (parameterType.getClassType().isTypeParameter()) return true;

      ResultHolder[] specifics = parameterType.getClassType().getSpecifics();
      if (specifics.length == 0) {
        return typeParamMap.containsKey(parameterType.getClassType().getClassName());
      }
      List<ResultHolder> recursionGuard = new ArrayList<>();

      for (ResultHolder specific : specifics) {
        if (specific.isClassType()) {
          recursionGuard.add(specific);
          List<ResultHolder> list = getSpecificsIfClass(specific, recursionGuard);
          if (list.stream()
            .map(holder -> holder.getClassType().getClassName())
            .anyMatch(typeParamMap::containsKey)) {
            return true;
          }
        }
      }
    }else if (parameterType.getFunctionType() != null) {
      SpecificFunctionReference fn = parameterType.getFunctionType();
      List<SpecificFunctionReference.Argument> arguments = fn.getArguments();
      if (arguments != null) {
        boolean anyMatch = arguments.stream().anyMatch(argument -> containsTypeParameter(argument.getType(), typeParamMap));
        if(anyMatch) return true;
      }
      return containsTypeParameter(fn.getReturnType(), typeParamMap);
    }

    return false;
  }
  static Optional<ResultHolder> findConstraintForTypeParameter(HaxeParameterModel parameter, @NotNull ResultHolder parameterType, @NotNull Map<String, ResultHolder> typeParamMap) {
    if (!parameterType.isClassType()) return Optional.empty();

    ResultHolder[] specifics = parameterType.getClassType().getSpecifics();
    if (specifics.length == 0){
      String className = parameterType.getClassType().getClassName();
      return typeParamMap.containsKey(className) ? Optional.ofNullable(typeParamMap.get(className)) : Optional.empty();
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
      .filter(typeParamMap::containsKey)
      .filter( s ->  typeParamMap.get(s) != null)
      .forEach( s ->  resolver.addConstraint(s, typeParamMap.get(s), ResolveSource.CLASS_TYPE_PARAMETER));

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
  //private static Stream<ResultHolder> getSpecificsIfClass(@NotNull ResultHolder holder) {
  //  List<ResultHolder> proccesed = new ArrayList<>();
  //  @NotNull ResultHolder[] specifics = holder.getClassType().getSpecifics();
  //  if (specifics.length == 0) return Stream.of(holder);
  //  return Arrays.stream(specifics)
  //    .filter(ResultHolder::isClassType)
  //    .filter(h -> !proccesed.contains(h) )// avoid recursive loop (classes can have itself as type parameter)
  //    .peek(proccesed::add)
  //    .flatMap(TypeParameterUtil::getSpecificsIfClass);
  //}
}
