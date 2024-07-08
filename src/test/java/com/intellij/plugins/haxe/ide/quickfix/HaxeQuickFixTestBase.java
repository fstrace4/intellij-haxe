package com.intellij.plugins.haxe.ide.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.diagnostic.LogLevel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.plugins.haxe.ide.inspections.HaxeUnresolvedSymbolInspection;
import com.intellij.testFramework.InspectionTestUtil;
import com.intellij.testFramework.InspectionsKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

import static com.intellij.plugins.haxe.util.HaxeTestUtils.BASE_TEST_DATA_PATH;

public abstract class HaxeQuickFixTestBase extends LightQuickFixTestCase {

  public HaxeQuickFixTestBase() {
    super();
    Logger.setUnitTestMode();
    Logger.setFactory(category -> {
      DefaultLogger logger = new DefaultLogger(category);
      logger.setLevel(LogLevel.WARNING);
      return logger;
    });
  }

  @Override
  protected @NonNls @NotNull String getTestDataPath() {
    return  BASE_TEST_DATA_PATH + "/quickfix";
  }

  @Override
  protected void setUp() throws Exception {
    myTestDataPath = BASE_TEST_DATA_PATH;
    super.setUp();
  }


  @Override
  protected void beforeActionStarted(String testName, String contents) {
    List<InspectionProfileEntry> inspections = InspectionTestUtil.instantiateTools(Set.of(HaxeUnresolvedSymbolInspection.class));
    InspectionsKt.enableInspectionTools(this.getProject(), this.getTestRootDisposable(), inspections.toArray(new InspectionProfileEntry[0]));
  }
}
