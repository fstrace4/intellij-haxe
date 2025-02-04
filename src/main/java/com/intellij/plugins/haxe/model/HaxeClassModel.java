/*
 * Copyright 2000-2013 JetBrains s.r.o.
 * Copyright 2014-2015 AS3Boyan
 * Copyright 2014-2014 Elias Ku
 * Copyright 2017-2018 Ilya Malanin
 * Copyright 2018-2020 Eric Bishton
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
package com.intellij.plugins.haxe.model;

import com.intellij.plugins.haxe.lang.psi.*;
import com.intellij.plugins.haxe.lang.psi.impl.AbstractHaxePsiClass;
import com.intellij.plugins.haxe.lang.psi.impl.HaxeObjectLiteralImpl;
import com.intellij.plugins.haxe.metadata.HaxeMetadataList;
import com.intellij.plugins.haxe.metadata.psi.HaxeMeta;
import com.intellij.plugins.haxe.metadata.psi.HaxeMetadataCompileTimeMeta;
import com.intellij.plugins.haxe.metadata.psi.impl.HaxeMetadataTypeName;
import com.intellij.plugins.haxe.model.type.*;
import com.intellij.plugins.haxe.model.type.resolver.ResolveSource;
import com.intellij.plugins.haxe.util.HaxeResolveUtil;
import com.intellij.plugins.haxe.util.UsefulPsiTreeUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.plugins.haxe.HaxeComponentType.*;
import static com.intellij.plugins.haxe.model.type.HaxeTypeCompatible.canAssignToFrom;
import static com.intellij.plugins.haxe.model.type.HaxeTypeCompatible.getUnderlyingClassIfAbstractNull;
import static com.intellij.plugins.haxe.model.type.HaxeTypeResolver.getTypeFromFunctionType;
import static com.intellij.plugins.haxe.util.HaxeGenericUtil.*;
import static com.intellij.plugins.haxe.util.HaxeMetadataUtil.getMethodsWithMetadata;

public class HaxeClassModel implements HaxeCommonMembersModel {
  public final HaxeClass haxeClass;

  private HaxeModifiersModel _modifiers;

  private SpecificHaxeClassReference reference;

  public HaxeClassModel(@NotNull HaxeClass haxeClass) {
    this.haxeClass = haxeClass;
  }

  public HaxeClassModel getParentClass() {
    // TODO: Anonymous structures can extend several structs.  Need to be able to find/check/use all of them.
    List<HaxeType> list = getExtendsList();
    if (!list.isEmpty()) {
      PsiElement haxeClass = list.get(0).getReferenceExpression().resolve();
      if (haxeClass instanceof HaxeClass) {
        return ((HaxeClass)haxeClass).getModel();
      }
    }
    return null;
  }

  static public boolean isValidClassName(String name) {
    return name.substring(0, 1).equals(name.substring(0, 1).toUpperCase());
  }

  @NotNull
  public HaxeClassReference getReference() {
    return new HaxeClassReference(this, this.getPsi());
  }

  @NotNull
  public ResultHolder getInstanceType() {
    return getInstanceReference().createHolder();
  }
  public SpecificHaxeClassReference getInstanceReference() {
    if (reference  == null) {
      HaxeGenericResolver genericResolver = getGenericResolver(null);
      if (genericResolver.isEmpty()) {
        reference = SpecificHaxeClassReference.withoutGenerics(getReference());
      }else {
        reference = SpecificHaxeClassReference.withGenerics(getReference(),genericResolver.getSpecifics());

      }
    }
    return reference;
  }

  public List<HaxeClassReferenceModel> getExtendingTypes() {
    List<HaxeType> list = getExtendsList();
    List<HaxeClassReferenceModel> out = new ArrayList<HaxeClassReferenceModel>();
    for (HaxeType type : list) {
      out.add(new HaxeClassReferenceModel(type));
    }
    return out;
  }

  public List<HaxeClassReferenceModel> getImplementingInterfaces() {
    List<HaxeType> list = getImplementsList();
    List<HaxeClassReferenceModel> out = new ArrayList<HaxeClassReferenceModel>();
    for (HaxeType type : list) {
      out.add(new HaxeClassReferenceModel(type));
    }
    return out;
  }

  public boolean isExtern() {
    return haxeClass.isExtern();
  }

  public boolean isClass() {
    return !this.isAbstractType() && (typeOf(haxeClass) == CLASS);
  }

  public boolean isInterface() {
    return typeOf(haxeClass) == INTERFACE;
  }

  public boolean isEnum() {
    return haxeClass.isEnum();
  }

  public boolean isTypedef() {
    return typeOf(haxeClass) == TYPEDEF;
  }

  public boolean isAbstractType() {
    return haxeClass instanceof HaxeAbstractTypeDeclaration;
  }
  public boolean isAnonymous() {
    return haxeClass instanceof HaxeAnonymousType;
  }
  public boolean isObjectLiteral() {
    return haxeClass instanceof HaxeObjectLiteralImpl;
  }

  public boolean isCoreType() {
    return hasCompileTimeMeta(HaxeMeta.CORE_TYPE);
  }

  public boolean hasCompileTimeMeta(@NotNull HaxeMetadataTypeName name) {
    return haxeClass.hasCompileTimeMeta(name);
  }
  public boolean isCallable() {
    return haxeClass.hasCompileTimeMeta(HaxeMeta.CALLABLE);
  }

  @Nullable
  public HaxeModifiersModel getModifiers() {

    if (haxeClass instanceof HaxeClassDeclaration classDeclaration) {
      // haxe declaration might be without any modifier
      HaxeClassModifierList list = classDeclaration.getClassModifierList();
      _modifiers = new HaxeModifiersModel(list != null ? list : classDeclaration);
    }

    if (haxeClass instanceof HaxeEnumDeclaration enumDeclaration) {
      PsiModifierList list = enumDeclaration.getModifierList();
      _modifiers = new HaxeModifiersModel(list != null ? list : enumDeclaration);
    }

    if (haxeClass instanceof HaxeInterfaceDeclaration  interfaceDeclaration) {
      PsiModifierList list = interfaceDeclaration.getModifierList();
      _modifiers = new HaxeModifiersModel(list != null ? list : interfaceDeclaration);
    }

    if (haxeClass instanceof HaxeExternInterfaceDeclaration  interfaceDeclaration) {
      PsiModifierList list = interfaceDeclaration.getModifierList();
      _modifiers = new HaxeModifiersModel(list != null ? list : interfaceDeclaration);
    }

    if (haxeClass instanceof HaxeExternClassDeclaration  externClassDeclaration) {
      _modifiers = new HaxeModifiersModel(externClassDeclaration.getExternClassModifierList());
    }
    return _modifiers;
  }

  @Nullable
  public HaxeClassModifierList getModifiersList() {
    // TODO: This should really be returning a HaxeModifiersModel, and that class needs to be updated
    //       to use HaxeClassModifierLists.  Right now, it's using the PsiModifiers from the IntelliJ Java implementation.

    if (haxeClass instanceof HaxeClassDeclaration) {
      HaxeClassDeclaration clazz = (HaxeClassDeclaration)haxeClass;
      return clazz.getClassModifierList();
    }
    if (haxeClass instanceof HaxeExternClassDeclaration) {
      HaxeExternClassDeclaration clazz = (HaxeExternClassDeclaration)haxeClass;
      return clazz.getExternClassModifierList();
    }
    return null;
  }

  public List<HaxeReferenceExpression> getUsingMetaReferences() {
    HaxeMetadataList meta = haxeClass.getCompileTimeMeta(HaxeMeta.USING);
    if(meta != null) {
      List<HaxeMetadataCompileTimeMeta> compileTimeMeta = meta.getCompileTimeMeta();

      return compileTimeMeta.stream().map(HaxeMetadataCompileTimeMeta::getContent)
        .filter(Objects::nonNull)
        .map(PsiElement::getFirstChild)// Metadata content is lazy so we access it be sure its parsed
        .map(content ->  Arrays.asList(content.getChildren()))
        .flatMap(Collection::stream)
        .map(e -> PsiTreeUtil.getChildOfType(e, HaxeReferenceExpression.class))
        .filter(Objects::nonNull)
        .toList();
    }
    return List.of();
  }



  @Nullable
  public HaxeTypeOrAnonymous getUnderlyingTypeOrAnonymous() {
    if (isAbstractType()) {
      HaxeAbstractTypeDeclaration abstractDeclaration = (HaxeAbstractTypeDeclaration)haxeClass;
      HaxeUnderlyingType underlyingType = abstractDeclaration.getUnderlyingType();
      if (underlyingType != null) {
        return underlyingType.getTypeOrAnonymous();
      }
    } else if(isTypedef()) {
      HaxeTypedefDeclaration typedef = (HaxeTypedefDeclaration)haxeClass;
      return typedef.getTypeOrAnonymous();
    }

    // TODO: What about function types?
    return null;
  }
  @Nullable
  public SpecificTypeReference getUnderlyingType() {
    if (!isAbstractType() && !isTypedef()) return null;
    HaxeTypeOrAnonymous typeOrAnon = getUnderlyingTypeOrAnonymous();
    if (typeOrAnon != null) {
      HaxeType type = typeOrAnon.getType();
      if (type != null) {
        ResultHolder resultHolder = HaxeTypeResolver.getTypeFromType(type);
        if (!resultHolder.isUnknown()) return resultHolder.getType();
      }
    }
    HaxeFunctionType type = getUnderlyingFunctionType();
    if (type != null){
      return getTypeFromFunctionType(type).getFunctionType();
    }
    return null;
  }

  private @Nullable HaxeFunctionType getUnderlyingFunctionType() {
    if (isAbstractType() &&  haxeClass instanceof  HaxeAbstractTypeDeclaration abstractDeclaration) {
      HaxeUnderlyingType underlyingType = abstractDeclaration.getUnderlyingType();
      if (underlyingType != null) {
        return underlyingType.getFunctionType();
      }
    }
    return null;
  }

  @Nullable
  public SpecificHaxeClassReference getUnderlyingClassReference(@NotNull HaxeGenericResolver resolver) {
    if (!isAbstractType() && !isTypedef()) return null;

    PsiElement element = getBasePsi();
    HaxeTypeOrAnonymous typeOrAnon = getUnderlyingTypeOrAnonymous();
    if (typeOrAnon != null) {
      HaxeType type = typeOrAnon.getType();
      if (type != null) {
        //HaxeClass aClass = HaxeResolveUtil.tryResolveClassByQName(type);
        ResultHolder resolved = HaxeTypeResolver.getTypeFromType(type);
        SpecificHaxeClassReference classType = resolved.getClassType();
        if (!resolved.isUnknown() && classType != null) {
          HaxeGenericResolver localResolver = new HaxeGenericResolver();
          localResolver.addAll(classType.getGenericResolver());
          localResolver.addAll(resolver);
          HaxeClass aClass = classType.getHaxeClass();
          if (aClass != null) {
            ResultHolder[] specifics = HaxeTypeResolver.resolveDeclarationParametersToTypes(aClass, localResolver);
            return SpecificHaxeClassReference.withGenerics(new HaxeClassReference(aClass.getModel(), aClass.getModel().haxeClass), specifics, element);
          }
        }
      } else { // Anonymous type
        HaxeAnonymousType anon = typeOrAnon.getAnonymousType();
        if (anon != null) {
          // Anonymous types don't have parameters of their own, but when they are part of a typedef, they use the parameters from it.
          if(element instanceof  HaxeTypedefDeclaration typedefDeclaration && typedefDeclaration.isGeneric()) {
            HaxeGenericResolver memberResolver = typedefDeclaration.getMemberResolver(null);
            if(memberResolver != null) {
              HaxeClassReference classReference = new HaxeClassReference(anon.getModel(), element);
              return SpecificHaxeClassReference.withGenerics(classReference, memberResolver.getSpecificsFor(classReference), element);
            }
          }
          return SpecificHaxeClassReference.withGenerics(new HaxeClassReference(anon.getModel(), element), resolver.getSpecifics(), element);
        }
      }
    } else {
      // No typeOrAnon.  This must be Null<T>?
      if ("Null".equals(getName())) {
        List<HaxeGenericParamModel> typeParams = getGenericParams();
        if (typeParams.size() == 1) {
          HaxeGenericParamModel param = typeParams.get(0);
          ResultHolder result = resolver.resolve(param.getName());
          if (result != null) {
            return result.getClassType();
          }
        }
      }
    }
    return null;
  }
  @Nullable
  public SpecificFunctionReference getUnderlyingFunctionReference(HaxeGenericResolver resolver) {
    if (!isAbstractType() && !isTypedef()) return null;
    PsiElement element = getBasePsi();
    HaxeTypeOrAnonymous typeOrAnon = getUnderlyingTypeOrAnonymous();
    if (typeOrAnon != null) {
      // TODO mlo handle abstracts with functions as type ?
    } else {
      // No typeOrAnon.  This must be Null<T>?
      if ("Null".equals(getName())) {
        List<HaxeGenericParamModel> typeParams = getGenericParams();
        if (typeParams.size() == 1) {
          HaxeGenericParamModel param = typeParams.get(0);
          ResultHolder result = resolver.resolve(param.getName());
          if (result != null) {
            return result.getFunctionType();
          }
        }
      }
    }
    return null;
  }

  public List<HaxeType> getAbstractToList() {
    if (!isAbstractType() ) return Collections.emptyList();

    List<HaxeType> types = new LinkedList<HaxeType>();
    if (haxeClass instanceof HaxeAbstractTypeDeclaration abstractClass) {
      List<HaxeAbstractToType> list = abstractClass.getAbstractToTypeList();
      for (HaxeAbstractToType toType : list) {
        if (toType.getTypeOrAnonymous() != null) {
          types.add(toType.getTypeOrAnonymous().getType());
        }
      }
    }
    return types;
  }

  public List<SpecificHaxeClassReference> getImplicitCastToTypesListClassOnly(SpecificHaxeClassReference sourceType ) {
    List<SpecificHaxeClassReference> list = new ArrayList<>();
    for (SpecificTypeReference reference : getImplicitCastToTypesList(sourceType)) {
      if (reference instanceof SpecificHaxeClassReference classReference) {
        list.add(classReference);
      }
    }
    return list;

  }
  public List<SpecificTypeReference> getImplicitCastToTypesList(SpecificHaxeClassReference sourceType ) {
    if (!isAbstractType()) return Collections.emptyList();
    List<HaxeMethodModel> methodsWithMetadata = getCastToMethods();

    List<SpecificTypeReference> list = new ArrayList<>();
    for (HaxeMethodModel methodModel : methodsWithMetadata) {
      if (castMethodAcceptsSource(sourceType, methodModel)) {
        SpecificTypeReference returnType = getReturnType(methodModel);
        if (returnType.isTypeParameter()) {
          ResultHolder resolve = sourceType.getGenericResolver().resolve(((SpecificHaxeClassReference)returnType).getClassName());
          if (resolve!= null && !resolve.isUnknown()) {
            returnType = resolve.getType();
          }
        }
        SpecificTypeReference reference = setSpecificsConstraints(methodModel, returnType, sourceType.getGenericResolver());
        list.add(reference);

        if (reference.isNullType()) {
          SpecificHaxeClassReference underlyingClass = getUnderlyingClassIfAbstractNull((SpecificHaxeClassReference)reference);
          list.add(underlyingClass);
        }
      }
    }
    return list;
  }


  private boolean castMethodAcceptsSource(@NotNull SpecificHaxeClassReference reference, @NotNull HaxeMethodModel methodModel) {
    SpecificTypeReference parameter = getTypeOfFirstParameter(methodModel);
    //implicit cast methods seems to accept both parameter-less methods and single parameter methods
    if (parameter == null) return true; // if no param then "this" is  the  input  and will always be compatible.
    HaxeGenericResolver resolver = reference.getGenericResolver().withoutUnknowns();
    SpecificTypeReference parameterWithRealRealSpecifics = setSpecificsConstraints(methodModel, parameter, resolver);

    if(reference.isAbstractType()) {
      SpecificHaxeClassReference underlying = reference.getHaxeClassModel().getUnderlyingClassReference(reference.getGenericResolver());
      if(canAssignToFrom(parameterWithRealRealSpecifics, underlying, false, null)) return true;
    }

    return canAssignToFrom(parameterWithRealRealSpecifics, reference, false, null);
  }

  public List<SpecificHaxeClassReference> getImplicitCastFromTypesListClassOnly(SpecificHaxeClassReference targetType) {
    List<SpecificHaxeClassReference> list = new ArrayList<>();
    for (SpecificTypeReference reference : getImplicitCastFromTypesList(targetType)) {
      if (reference instanceof SpecificHaxeClassReference classReference) {
        list.add(classReference);
      }
    }
    return list;
  }

  public List<SpecificTypeReference> getImplicitCastFromTypesList(SpecificHaxeClassReference targetType) {
    if (!isAbstractType()) return Collections.emptyList();
    List<HaxeMethodModel> methodsWithMetadata = getCastFromMethods();

    HaxeGenericResolver resolver = this.getGenericResolver(null);

    // if return types can not be assign to target then skip this castMethod
    List<SpecificTypeReference> list = new ArrayList<>();
    for (HaxeMethodModel m : methodsWithMetadata) {
      // TODO consider applying generics from targetType to be more strict about what methods are supported ?
      if (canAssignToFrom(targetType, setSpecificsConstraints(m, getReturnType(m), targetType.getGenericResolver().withoutUnknowns()), false,  null)) {
        SpecificTypeReference type = getImplicitCastFromType(m, resolver);
        if (type != null) {
          list.add(type);
        }
      }
    }
    return list;
  }

  @Nullable
  private SpecificTypeReference getImplicitCastFromType(@NotNull HaxeMethodModel methodModel, @Nullable HaxeGenericResolver resolver) {
    SpecificTypeReference parameter = getTypeOfFirstParameter(methodModel);
    if (parameter == null) return null;
    return setSpecificsConstraints(methodModel, parameter, resolver);
  }

  @NotNull
  private SpecificTypeReference setSpecificsConstraints(@NotNull HaxeMethodModel methodModel, @NotNull SpecificTypeReference classReference,
                                                             @Nullable HaxeGenericResolver resolver) {
    if (classReference instanceof  SpecificHaxeClassReference haxeClassReference) {
      ResultHolder[] specifics = haxeClassReference.getGenericResolver().getSpecifics();
      ResultHolder[] newSpecifics = applyConstraintsToSpecifics(methodModel, specifics);

      SpecificHaxeClassReference reference = replaceTypeIfGenericParameterName(methodModel, haxeClassReference);

      if (resolver != null) {
        for (int i = 0; i < specifics.length; i++) {
          ResultHolder specific = specifics[i];
          ResultHolder resolved = resolver.resolve(specific);
          if (!resolved.isUnknown() && canAssignToFrom(newSpecifics[i], resolved)) {
            newSpecifics[i] = resolved;
          }
        }
      }
      return SpecificHaxeClassReference.withGenerics(reference.getHaxeClassReference(), newSpecifics);
    }else {
      return classReference;
    }
  }

  //caching  implicit cast  method lookup results
  @NotNull
  private List<HaxeMethodModel> getCastToMethods() {
    return  CachedValuesManager.getProjectPsiDependentCache(haxeClass, HaxeClassModel::getCastToMethodsCached);
  }

  private static List<HaxeMethodModel> getCastToMethodsCached(@NotNull HaxeClass haxeClass) {
    List<HaxeMethodModel> castToMethods = getMethodsWithMetadata(haxeClass.getModel(), "to", HaxeMeta.COMPILE_TIME, null);
    return  List.copyOf(castToMethods);
  }

  //caching implicit cast method lookup  results
  @NotNull
  private List<HaxeMethodModel> getCastFromMethods() {
    return  CachedValuesManager.getProjectPsiDependentCache(haxeClass, HaxeClassModel::getCastFromMethodsCached);
  }
  private static List<HaxeMethodModel> getCastFromMethodsCached(@NotNull HaxeClass haxeClass) {
    List<HaxeMethodModel> castFromMethods = getMethodsWithMetadata(haxeClass.getModel(), "from", HaxeMeta.COMPILE_TIME, null);
    return  List.copyOf(castFromMethods);
  }

  @NotNull
  private SpecificHaxeClassReference getReturnType(@NotNull HaxeMethodModel model) {
    SpecificHaxeClassReference type = model.getFunctionType().getReturnType().getClassType();
    return type != null ? type :  SpecificTypeReference.getUnknown(model.getFunctionType().getReturnType().getElementContext());
  }

  @Nullable
  private SpecificTypeReference getTypeOfFirstParameter(@NotNull HaxeMethodModel model) {
    List<SpecificFunctionReference.Argument> arguments = model.getFunctionType().getArguments();
    if (arguments.isEmpty()) return null;

    return arguments.get(0).getType().getType();
  }



  public List<HaxeType> getAbstractFromList() {
    if (!isAbstractType() ) return Collections.emptyList();
    List<HaxeType> types = new LinkedList<HaxeType>();
    if (haxeClass instanceof  HaxeAbstractTypeDeclaration abstractClass) {
      List<HaxeAbstractFromType> list = abstractClass.getAbstractFromTypeList();
      for (HaxeAbstractFromType fromType : list) {
        if (fromType.getTypeOrAnonymous() != null) {
          types.add(fromType.getTypeOrAnonymous().getType());
        }
      }
    }
    return types;
  }

  public boolean hasMethod(String name, @Nullable HaxeGenericResolver resolver) {
    return getMethod(name, resolver) != null;
  }

  public boolean hasMethodSelf(String name) {
    HaxeMethodModel method = getMethod(name, null);
    if (method == null) return false;
    return (method.getDeclaringClass() == this);
  }

  public HaxeMethodModel getMethodSelf(String name) {
    HaxeMethodModel method = getMethod(name, null);
    if (method == null) return null;
    return (method.getDeclaringClass() == this) ? method : null;
  }

  public HaxeMethodModel getConstructorSelf() {
    return getMethodSelf("new");
  }

  public HaxeMethodModel getConstructor(@Nullable HaxeGenericResolver resolver) {
    return getMethod("new", resolver);
  }

  public boolean hasConstructor(@Nullable HaxeGenericResolver resolver) {
    return getConstructor(resolver) != null;
  }

  public HaxeMethodModel getParentConstructor(@Nullable HaxeGenericResolver resolver) {
    HaxeClassModel parentClass = getParentClass();
    while (parentClass != null) {
      HaxeMethodModel constructorMethod = parentClass.getConstructor(resolver);
      if (constructorMethod != null) {
        return constructorMethod;
      }
      parentClass = parentClass.getParentClass();
    }
    return null;
  }

  @Nullable
  public HaxeBaseMemberModel getMember(String name, @Nullable HaxeGenericResolver resolver) {
    if (name == null) return null;
    HaxeNamedComponent component = haxeClass.findHaxeMemberByName(name, resolver);
    if (component != null) {
      return HaxeMemberModel.fromPsi(component);
    }
    return null;
  }

  public List<HaxeBaseMemberModel> getMembers(@Nullable HaxeGenericResolver resolver) {
    final List<HaxeBaseMemberModel> members = new ArrayList<>();
    members.addAll(getMethods(resolver));
    members.addAll(getFields());
    return members;
  }
  public List<HaxeBaseMemberModel> getAllMembers(@Nullable HaxeGenericResolver resolver) {
    final List<HaxeBaseMemberModel> members = new ArrayList<>();
    members.addAll(getAllMethods(resolver));
    members.addAll(getAllFields(resolver));
    return members;
  }

  private List<HaxeFieldModel> getAllFields(@Nullable HaxeGenericResolver resolver) {
    List<HaxeFieldModel> models =  getAncestorFields(resolver);
    models.addAll( getFields());

    return models;
  }

  @NotNull
  public List<HaxeBaseMemberModel> getMembersSelf() {
    final List<HaxeBaseMemberModel> members = new ArrayList<>();
    HaxePsiCompositeElement body = getBodyPsi();
    if (body != null) {
      for (PsiElement element : body.getChildren()) {
        if (element instanceof HaxeMethod || element instanceof HaxeFieldDeclaration) {
          HaxeMemberModel model = HaxeMemberModel.fromPsi(element);
          if (model != null) {
            members.add(model);
          }
        }
      }
    }
    return members;
  }

  @Nullable
  public HaxeBaseMemberModel getMemberSelf(String name, @Nullable HaxeGenericResolver resolver) {
    return getMembersSelf().stream().filter(model -> model.getNamePsi().getIdentifier().textMatches(name)).findFirst().orElse(null);
  }

  public HaxeFieldModel getField(String name, @Nullable HaxeGenericResolver resolver) {
    HaxePsiField field = (HaxePsiField)haxeClass.findHaxeFieldByName(name, resolver);
    if (field instanceof HaxeFieldDeclaration || field instanceof HaxeAnonymousTypeField || field instanceof HaxeEnumValueDeclaration) {
      return (HaxeFieldModel)field.getModel();
    }
    return null;
  }

  public HaxeMethodModel getMethod(String name, @Nullable HaxeGenericResolver resolver) {
    HaxeMethodPsiMixin method = (HaxeMethodPsiMixin)haxeClass.findHaxeMethodByName(name, resolver);
    return method != null ? method.getModel() : null;
  }

  public List<HaxeMethodModel> getMethods(@Nullable HaxeGenericResolver resolver) {
    List<HaxeMethodModel> models = new ArrayList<HaxeMethodModel>();
    for (HaxeMethod method : haxeClass.getHaxeMethodsSelf(resolver)) {
      models.add(method.getModel());
    }
    return models;
  }
  public List<HaxeMethodModel> getAllMethods(@Nullable HaxeGenericResolver resolver) {
    List<HaxeMethodModel> models =  getAncestorMethods(resolver);
    for (HaxeMethod method : haxeClass.getHaxeMethodsSelf(resolver)) {
      models.add(method.getModel());
    }

    return models;
  }

  public List<HaxeMethodModel> getMethodsSelf(@Nullable HaxeGenericResolver resolver) {
    List<HaxeMethodModel> models = new ArrayList<HaxeMethodModel>();
    for (HaxeMethod method : haxeClass.getHaxeMethodsSelf(resolver)) {
      if (method.getContainingClass() == this.haxeClass) models.add(method.getModel());
    }
    return models;
  }

  public List<HaxeMethodModel> getAncestorMethods(@Nullable HaxeGenericResolver resolver) {
    List<HaxeMethodModel> models = new ArrayList<HaxeMethodModel>();
    for (HaxeMethod method : haxeClass.getHaxeMethodsAncestor(true)) {
        models.add(method.getModel());
    }
    return models;
  }
  public List<HaxeFieldModel> getAncestorFields(@Nullable HaxeGenericResolver resolver) {
    List<HaxeFieldModel> models = new ArrayList<>();
    for (HaxePsiField field : haxeClass.getHaxeFieldsAncestor(true)) {
        models.add((HaxeFieldModel)field.getModel());
    }
    return models;
  }
  public HaxeMethodModel getAncestorMethod(String name, @Nullable HaxeGenericResolver resolver) {
    for (HaxeMethod method : haxeClass.getHaxeMethodsAncestor(true)) {
          HaxeMethodModel methodModel = method.getModel();
          if (name.equals(methodModel.getName())) return  methodModel;
        }
    return null;
  }

  @NotNull
  public HaxeClass getPsi() {
    return haxeClass;
  }

  @Nullable
  public HaxePsiCompositeElement getBodyPsi() {
    return (haxeClass instanceof HaxeClassDeclaration) ? ((HaxeClassDeclaration)haxeClass).getClassBody() : null;
  }

  @Nullable
  public PsiIdentifier getNamePsi() {
    return haxeClass.getNameIdentifier();
  }
  @NotNull
  public List<HaxeType> getExtendsList() {
    return CachedValuesManager.getProjectPsiDependentCache(haxeClass, HaxeClassModel::getHaxeExtendsListCached);
  }
  private static List<HaxeType> getHaxeExtendsListCached(@NotNull HaxeClass haxeClass) {
    List<HaxeType> list = haxeClass.getHaxeExtendsList();
    return  List.copyOf(list);
  }

  @NotNull
  public List<HaxeType> getImplementsList() {
    return CachedValuesManager.getProjectPsiDependentCache(haxeClass, HaxeClassModel::getHaxeImplementsListCached);
  }
  private static List<HaxeType> getHaxeImplementsListCached(@NotNull HaxeClass haxeClass) {
    List<HaxeType> list = haxeClass.getHaxeImplementsList();
    return  List.copyOf(list);
  }

  @NotNull
  public HaxeDocumentModel getDocument() {
    return new HaxeDocumentModel(haxeClass);
  }

  public String getName() {
    return haxeClass.getName();
  }

  @Override
  public PsiElement getBasePsi() {
    return this.haxeClass;
  }

  @Nullable
  @Override
  public HaxeExposableModel getExhibitor() {
    return HaxeFileModel.fromElement(haxeClass.getContainingFile());
  }

  @Nullable
  @Override
  public FullyQualifiedInfo getQualifiedInfo() {
      HaxeExposableModel exhibitor = getExhibitor();
      if (exhibitor != null) {
        FullyQualifiedInfo containerInfo = exhibitor.getQualifiedInfo();
        if (containerInfo != null) {
          return new FullyQualifiedInfo(containerInfo.packagePath, containerInfo.fileName, getName(), null);
        }
      }
    return null;
  }

  public void addMethodsFromPrototype(List<HaxeMethodModel> methods) {
    throw new NotImplementedException("Not implemented HaxeClassMethod.addMethodsFromPrototype() : check HaxeImplementMethodHandler");
  }

  public List<HaxeFieldModel> getFields() {
    // TODO: Figure out if this needs to deal with forwarded fields in abstracts.
    HaxePsiCompositeElement body = PsiTreeUtil.getChildOfAnyType(haxeClass, isEnum() ? HaxeEnumBody.class : HaxeClassBody.class, HaxeInterfaceBody.class, HaxeExternClassDeclarationBody.class);

    if (body != null) {
      List<HaxeFieldModel> list = new ArrayList<>();
      List<HaxePsiField> children = PsiTreeUtil.getChildrenOfAnyType(body, HaxeFieldDeclaration.class, HaxeAnonymousTypeField.class, HaxeEnumValueDeclarationField.class);

      for (HaxePsiField field : children) {
        HaxeFieldModel model = (HaxeFieldModel)field.getModel();
        list.add(model);
      }
      return list;
    } else {
      return Collections.emptyList();
    }
  }

  public Set<HaxeClassModel> getCompatibleTypes() {
    final Set<HaxeClassModel> output = new LinkedHashSet<HaxeClassModel>();
    writeCompatibleTypes(output);
    return output;
  }

  public void writeCompatibleTypes(Set<HaxeClassModel> output) {
    // Own
    output.add(this);

    final HaxeClassModel parentClass = this.getParentClass();

    // Parent classes
    if (parentClass != null) {
      if (!output.contains(parentClass)) {
        parentClass.writeCompatibleTypes(output);
      }
    }

    // Interfaces
    for (HaxeClassReferenceModel model : this.getImplementingInterfaces()) {
      if (model == null) continue;
      final HaxeClassModel aInterface = model.getHaxeClassModel();
      if (aInterface == null) continue;
      if (!output.contains(aInterface)) {
        aInterface.writeCompatibleTypes(output);
      }
    }

    // @CHECK abstract FROM
    for (HaxeType type : getAbstractFromList()) {
      final ResultHolder aTypeRef = HaxeTypeResolver.getTypeFromType(type);
      SpecificHaxeClassReference classType = aTypeRef.getClassType();
      if (classType != null) {
        HaxeClassModel model = classType.getHaxeClassModel();
        if (model != null) {
          model.writeCompatibleTypes(output);
        }
      }
    }

    // @CHECK abstract TO
    for (HaxeType type : getAbstractToList()) {
      final ResultHolder aTypeRef = HaxeTypeResolver.getTypeFromType(type);
      SpecificHaxeClassReference classType = aTypeRef.getClassType();
      if (classType != null) {
        HaxeClassModel model = classType.getHaxeClassModel();
        if (model != null) {
          model.writeCompatibleTypes(output);
        }
      }
    }

    // TODO: Add types from @:from and @:to methods, including inferred method types.
  }

  public boolean hasGenericParams() {
    return getGenericParamPsi() != null;
  }

  @NotNull
  public List<HaxeGenericParamModel> getGenericParams() {
    final List<HaxeGenericParamModel> out = new ArrayList<>();
    // anonymous structures does not have TypeParameters on their own, but their parent may declar them.
      HaxeGenericParam genericParam = getGenericParamPsi();
      if (genericParam != null) {
        int index = 0;
        for (HaxeGenericListPart part : genericParam.getGenericListPartList()) {
          // TODO try to avoid recreating this  model every time
          out.add(new HaxeGenericParamModel(part, index));
          index++;
        }
      }
    return out;
  }

  @Nullable
  private HaxeGenericParam getGenericParamPsi() {
    return CachedValuesManager.getProjectPsiDependentCache(haxeClass, HaxeClassModel::getGenericParamPsiCached);
  }

  private static HaxeGenericParam getGenericParamPsiCached(@NotNull HaxeClass haxeClass) {
    boolean isAnonymous = haxeClass instanceof HaxeAnonymousType;
    HaxeGenericParam param = isAnonymous ? getGenericParamFromParent(haxeClass) : haxeClass.getGenericParam();
    return  param;
  }

  /**
   * only intended for typedefs with anonymous structures
   */
  private static HaxeGenericParam getGenericParamFromParent(HaxeClass haxeClass) {
    HaxeTypedefDeclaration type = PsiTreeUtil.getParentOfType(haxeClass, HaxeTypedefDeclaration.class);
    if (type == null) return null;
    return type.getGenericParam();
  }

  /**
   * @return a generic resolver with Unknown or constrained types.
   */
  @NotNull
  public HaxeGenericResolver getGenericResolver(@Nullable HaxeGenericResolver parentResolver) {
    HaxeGenericParam param = getGenericParamPsi();
    if (param != null) {

      HaxeGenericResolver resolver = new HaxeGenericResolver();
      for (HaxeGenericListPart part : param.getGenericListPartList()) {
        HaxeGenericParamModel model = new HaxeGenericParamModel(part, 0);
        ResultHolder constraint = model.getConstraint(parentResolver);
        if (null == constraint) {
          constraint = new ResultHolder(SpecificTypeReference.getUnknown(getBasePsi()));
        }
        resolver.addConstraint(model.getName(), constraint, ResolveSource.CLASS_TYPE_PARAMETER);
      }

      return resolver;
    }
    return new HaxeGenericResolver();
  }

  public void addField(String name, SpecificTypeReference type) {
    this.getDocument().addTextAfterElement(getBodyPsi(), "\npublic var " + name + ":" + type.toStringWithoutConstant() + ";\n");
  }

  public void addMethod(String name) {
    this.getDocument().addTextAfterElement(getBodyPsi(), "\npublic function " + name + "() {\n}\n");
  }

  public void addImplements(String name) {
    if ( haxeClass instanceof AbstractHaxePsiClass psiClass) {
      HaxeInheritList implementsListPsi = psiClass.getHaxeImplementsListPsi();
      if (implementsListPsi != null) {
        List<HaxeImplementsDeclaration> list = implementsListPsi.getImplementsDeclarationList();
        String insertText = " implements " + name + " ";
        if (list.isEmpty()) {
          this.getDocument().addTextAfterElement(implementsListPsi, insertText);
        }else {
          HaxeImplementsDeclaration declaration = list.get(list.size() - 1);
          this.getDocument().addTextAfterElement(declaration, insertText);
        }
      }
    }
  }
  public void changeToInterface(String name) {
    if ( haxeClass instanceof AbstractHaxePsiClass psiClass) {
      HaxeInheritList implementsListPsi = psiClass.getHaxeImplementsListPsi();
      if (implementsListPsi != null) {
        List<HaxeExtendsDeclaration> list = implementsListPsi.getExtendsDeclarationList();
        Optional<HaxeExtendsDeclaration> first = list.stream().filter(declaration -> Objects.equals(declaration.getType().getText(), name)).findFirst();
        String replacementText = "implements " + name;
        if (first.isPresent()) {
          HaxeExtendsDeclaration declaration = first.get();
          this.getDocument().replaceElementText(declaration, replacementText);
        }
      }
    }
  }
  public void changeToExtends(String name) {
    if ( haxeClass instanceof AbstractHaxePsiClass psiClass) {
      HaxeInheritList implementsListPsi = psiClass.getHaxeImplementsListPsi();
      if (implementsListPsi != null) {
        List<HaxeImplementsDeclaration> list = implementsListPsi.getImplementsDeclarationList();
        Optional<HaxeImplementsDeclaration> first = list.stream().filter(declaration -> Objects.equals(declaration.getType().getText(), name)).findFirst();
        String replacementText = "extends " + name;
        if (first.isPresent()) {
          HaxeImplementsDeclaration declaration = first.get();
          this.getDocument().replaceElementText(declaration, replacementText);
        }
      }
    }
  }
  public void addExtends(String name) {
    if ( haxeClass instanceof AbstractHaxePsiClass psiClass) {
      HaxeInheritList implementsListPsi = psiClass.getHaxeImplementsListPsi();
      if (implementsListPsi != null) {
        List<HaxeExtendsDeclaration> list = implementsListPsi.getExtendsDeclarationList();
        String insertText = " extends " + name + " ";
        if (list.isEmpty()) {
          this.getDocument().addTextAfterElement(implementsListPsi, insertText);
        }else {
          HaxeExtendsDeclaration declaration = list.get(list.size() - 1);
          this.getDocument().addTextAfterElement(declaration, insertText);
        }
      }
    }
  }
  public void removeImplements(String name) {
    if ( haxeClass instanceof AbstractHaxePsiClass psiClass) {
      HaxeInheritList implementsListPsi = psiClass.getHaxeImplementsListPsi();
      if (implementsListPsi != null) {

        List<HaxeImplementsDeclaration> list = implementsListPsi.getImplementsDeclarationList();
        Optional<HaxeImplementsDeclaration> first = list.stream().filter(declaration -> Objects.equals(declaration.getType().getText(), name)).findFirst();

        if (first.isPresent()) {
          HaxeImplementsDeclaration declaration = first.get();
          this.getDocument().replaceElementText(declaration, "");
        }
      }
    }
  }
  public void removeExtends(String name) {
    if ( haxeClass instanceof AbstractHaxePsiClass psiClass) {
      HaxeInheritList implementsListPsi = psiClass.getHaxeImplementsListPsi();
      if (implementsListPsi != null) {
        List<HaxeExtendsDeclaration> list = implementsListPsi.getExtendsDeclarationList();
        Optional<HaxeExtendsDeclaration> first = list.stream().filter(declaration -> Objects.equals(declaration.getType().getText(), name)).findFirst();

        if (first.isPresent()) {
          HaxeExtendsDeclaration declaration = first.get();
          this.getDocument().replaceElementText(declaration, "");
        }
      }
    }
  }

  @Override
  public List<HaxeModel> getExposedMembers() {
    // TODO ClassModel concept should be reviewed. We need to separate logic of abstracts, regular classes, enums, etc. Right now this class a bunch of if-else conditions. It looks dirty.
    ArrayList<HaxeModel> out = new ArrayList<>();
    if (isClass()) {
      HaxeClassBody body = UsefulPsiTreeUtil.getChild(haxeClass, HaxeClassBody.class);
      if (body != null) {
        for (HaxeNamedComponent declaration : PsiTreeUtil.getChildrenOfAnyType(body, HaxeFieldDeclaration.class, HaxeMethod.class)) {
          if (!(declaration instanceof PsiMember)) continue;
          if (declaration instanceof HaxeFieldDeclaration varDeclaration) {
            if (varDeclaration.isPublic() && varDeclaration.isStatic()) {
              out.add(varDeclaration.getModel());
            }
          } else {
            HaxeMethodDeclaration method = (HaxeMethodDeclaration)declaration;
            if (method.isStatic() && method.isPublic()) {
              out.add(method.getModel());
            }
          }
        }
      }
    } else if (isEnum()) {
      HaxeEnumBody body = UsefulPsiTreeUtil.getChild(haxeClass, HaxeEnumBody.class);
      if (body != null) {
        for (HaxeEnumValueDeclarationField field : body.getEnumValueDeclarationFieldList()) {
          out.add(field.getModel());
        }
        for (HaxeEnumValueDeclarationConstructor constructor : body.getEnumValueDeclarationConstructorList()) {
          out.add(constructor.getModel());
        }
      }
    }
    return out;
  }

  public static HaxeClassModel fromElement(PsiElement element) {
    if (element == null) return null;

    HaxeClass haxeClass = element instanceof HaxeClass
                          ? (HaxeClass) element
                          : PsiTreeUtil.getParentOfType(element, HaxeClass.class);

    //TODO  cache in element ?
    if (haxeClass != null) {
      return new HaxeClassModel(haxeClass);
    }
    return null;
  }

  public boolean isPublic() {
    return haxeClass.isPublic();
  }

  public boolean isAbstractClass() {
    HaxeModifiersModel modifiers = getModifiers();
    if (modifiers == null) return false;
    return modifiers.hasModifier(HaxePsiModifier.ABSTRACT);
  }

  public boolean isFinal() {
    HaxeModifiersModel modifiers = getModifiers();
    if (modifiers == null) return false;
    return modifiers.hasModifier(HaxePsiModifier.FINAL);
  }

  public List<HaxeMethodModel>  getExtensionMethodsFromMeta() {
    List<HaxeReferenceExpression> referenceExpressions = getUsingMetaReferences();
    if(referenceExpressions.isEmpty()) return List.of();

    HaxeGenericResolver genericResolver = getGenericResolver(null);
    SpecificHaxeClassReference classReference = SpecificHaxeClassReference.withoutGenerics(getReference());
    ResultHolder classRef = classReference.createHolder();

    // NOTE!: DO NOT USE "PsiReference::resolve", on referenceExpressions here.
    // it will in some cases fail to resolve, probably due to recursion guards and other "active" resolves in same package
    // use findClassByQName instead to be sure we try to find the class directly
    List<HaxeClassModel> classModels = referenceExpressions.stream().map(PsiElement::getText)
      .map(qname -> HaxeResolveUtil.findClassByQName(qname, haxeClass))
      .filter(Objects::nonNull)
      .map(HaxeClass::getModel)
      .toList();

    return  classModels.stream()
      .map(classModel -> classModel.getMethods(genericResolver))
      .flatMap(Collection::stream)
      .filter(method -> !method.isConstructor() && method.isStatic() && method.isPublic())
      .filter(method ->  canAssignToFirstParam(method, classRef, genericResolver))
      .toList();

  }

  private boolean canAssignToFirstParam(HaxeMethodModel method, ResultHolder classRef, HaxeGenericResolver resolver) {
    List<HaxeParameterModel> parameters = method.getParameters();
    if (!parameters.isEmpty()) {
      HaxeParameterModel paramModel = parameters.get(0);
      ResultHolder paramResult = paramModel.getType(resolver);
      boolean b = paramResult.canAssign(classRef);
      return b;
    }
    return false;
  }

  public boolean isStructInit() {
    return hasCompileTimeMeta(HaxeMeta.STRUCT_INIT);
  }
}
