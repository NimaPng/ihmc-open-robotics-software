package us.ihmc.avatar.networkProcessor.kinematicsStreamingToolboxModule;

import controller_msgs.msg.dds.RobotConfigurationData;
import us.ihmc.avatar.MultiRobotTestInterface;
import us.ihmc.avatar.initialSetup.OffsetAndYawRobotInitialSetup;
import us.ihmc.avatar.networkProcessor.kinemtaticsStreamingToolboxModule.KinematicsStreamingToolboxMessageReplay;
import us.ihmc.avatar.networkProcessor.kinemtaticsStreamingToolboxModule.KinematicsStreamingToolboxModule;
import us.ihmc.avatar.testTools.DRCSimulationTestHelper;
import us.ihmc.commons.ContinuousIntegrationTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.humanoidRobotics.frames.HumanoidReferenceFrames;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.simulationConstructionSetTools.util.environments.FlatGroundEnvironment;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.util.simulationRunner.BlockingSimulationRunner.SimulationExceededMaximumTimeException;
import us.ihmc.simulationconstructionset.util.simulationTesting.SimulationTestingParameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class KinematicsStreamingToolboxEndToEndTest implements MultiRobotTestInterface
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromSystemProperties();

   private DRCSimulationTestHelper drcSimulationTestHelper;
   private FullHumanoidRobotModel fullRobotModel;
   private HumanoidReferenceFrames humanoidReferenceFrames;
   private SimulationConstructionSet scs;

   protected void runTest(InputStream inputStream) throws IOException, SimulationExceededMaximumTimeException
   {
      String robotName = getSimpleRobotName();
      KinematicsStreamingToolboxMessageReplay kinematicsStreamingToolboxMessageReplay = new KinematicsStreamingToolboxMessageReplay(robotName, inputStream, PubSubImplementation.INTRAPROCESS);

      if(!ContinuousIntegrationTools.isRunningOnContinuousIntegrationServer())
         simulationTestingParameters.setKeepSCSUp(true);

      RobotConfigurationData initialRobotConfigurationData = kinematicsStreamingToolboxMessageReplay.getInitialConfiguration();
      OffsetAndYawRobotInitialSetup initialSimulationSetup = new OffsetAndYawRobotInitialSetup(initialRobotConfigurationData.getRootTranslation().getX(),
                                                                                               initialRobotConfigurationData.getRootTranslation().getY(),
                                                                                               0.0,
                                                                                               initialRobotConfigurationData.getRootOrientation().getYaw());

      FlatGroundEnvironment environment = new FlatGroundEnvironment();
      drcSimulationTestHelper = new DRCSimulationTestHelper(simulationTestingParameters, getRobotModel());
      drcSimulationTestHelper.setTestEnvironment(environment);
      drcSimulationTestHelper.setStartingLocation(initialSimulationSetup);
      drcSimulationTestHelper.createSimulation(getClass().getSimpleName());

      Point3D cameraFix = new Point3D(initialRobotConfigurationData.getRootTranslation());
      Point3D cameraPosition = new Point3D(cameraFix);
      cameraPosition.add(-7.0, -9.0, 4.0);
      drcSimulationTestHelper.setupCameraForUnitTest(cameraFix, cameraPosition);

      ThreadTools.sleep(1000);
      assertTrue(drcSimulationTestHelper.simulateAndBlockAndCatchExceptions(0.5));

      fullRobotModel = drcSimulationTestHelper.getControllerFullRobotModel();
      humanoidReferenceFrames = new HumanoidReferenceFrames(fullRobotModel);
      scs = drcSimulationTestHelper.getSimulationConstructionSet();

      humanoidReferenceFrames.updateFrames();

      new KinematicsStreamingToolboxModule(getRobotModel(), false, PubSubImplementation.INTRAPROCESS);

      kinematicsStreamingToolboxMessageReplay.initialize(scs.getTime());

      AtomicBoolean doneWithReplay = new AtomicBoolean(false);
      scs.addScript(time ->
                    {
                       boolean done = !kinematicsStreamingToolboxMessageReplay.update(time);

                       if(done && !doneWithReplay.get())
                       {
                          kinematicsStreamingToolboxMessageReplay.conclude();
                       }

                       doneWithReplay.set(done);
                    });
      scs.setSimulateDoneCriterion(doneWithReplay::get);
      scs.simulate();

      if(simulationTestingParameters.getKeepSCSUp())
         ThreadTools.sleepForever();
   }
}
