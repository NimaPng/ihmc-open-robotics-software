package us.ihmc.commonWalkingControlModules.generatedTestSuites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import us.ihmc.utilities.code.unitTesting.runner.JUnitTestSuiteRunner;

/** WARNING: AUTO-GENERATED FILE. DO NOT MAKE MANUAL CHANGES TO THIS FILE. **/
@RunWith(Suite.class)
@Suite.SuiteClasses
({
   us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.NewInstantaneousCapturePointPlannerWithTimeFreezerAndFootSlipCompensationTest.class,
   us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.smoothICPGenerator.SmoothICPComputerTest.class
})

public class CommonWalkingControlModulesAUITestSuite
{
   public static void main(String[] args)
   {
      new JUnitTestSuiteRunner(CommonWalkingControlModulesAUITestSuite.class);
   }
}

