package com.intellij.plugins.haxe.model.type;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.plugins.haxe.model.type.HaxeExpressionEvaluator._handle;

/**
 * To avoid unnecessary re-evaluation of elements used by  other expressions (ex. functions without type tags etc)
 * We cache the evaluation result until a Psi change happens, we dont want to cache for longer as ResultHolder
 * contains SpecificTypeReference elements both as the type and as generics and these contain PsiElements
 * that might become invalid
 */
public class HaxeExpressionEvaluatorCacheService  {

  private volatile  Map<EvaluationKey, ResultHolder> cacheMap = new ConcurrentHashMap<>();
  public boolean skipCaching = false;

  public @NotNull ResultHolder handleWithResultCaching(final PsiElement element,
                                                       final HaxeExpressionEvaluatorContext context,
                                                       final HaxeGenericResolver resolver) {

    if(skipCaching) return  _handle(element, context, resolver);

    EvaluationKey key = new EvaluationKey(element, resolver == null ? "NO_RESOLVER" : resolver.toCacheString());
    if (cacheMap.containsKey(key)) {
      return cacheMap.get(key);
    }
    else {
      ResultHolder holder = _handle(element, context, resolver);
      if (!holder.isUnknown()) {
        cacheMap.put(key, holder);
      }
      return holder;
    }
  }


  public void clearCaches() {
    cacheMap.clear();
  }
}

record EvaluationKey( PsiElement element, String evalParamString) {
}