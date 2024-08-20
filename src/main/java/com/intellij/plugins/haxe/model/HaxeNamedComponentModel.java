
package com.intellij.plugins.haxe.model;

import com.intellij.plugins.haxe.lang.psi.HaxeComponentName;

public interface HaxeNamedComponentModel extends HaxeModel {

  HaxeComponentName getNamePsi();

}
