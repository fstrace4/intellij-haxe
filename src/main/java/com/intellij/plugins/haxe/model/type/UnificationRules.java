package com.intellij.plugins.haxe.model.type;

public   enum UnificationRules {
  DEFAULT,
  PREFER_VOID,// typically when finding method return types
  IGNORE_VOID, // typically when finding type for array and map literal (with guards)
}