package us.ihmc.robotiq.simulatedHand;

import java.util.EnumMap;

import us.ihmc.robotiq.model.RobotiqHandModel.RobotiqHandJointNameMinimal;
import us.ihmc.utilities.robotSide.RobotSide;
import us.ihmc.utilities.robotSide.SideDependentList;

public class RobotiqHandsDesiredConfigurations
{
   private static final SideDependentList<EnumMap<RobotiqHandJointNameMinimal, Double>> closedHandsBasicGripDesiredConfigurations = SideDependentList.createListOfEnumMaps(RobotiqHandJointNameMinimal.class);
   private static final SideDependentList<EnumMap<RobotiqHandJointNameMinimal, Double>> openHandsBasicGripDesiredConfigurations = SideDependentList.createListOfEnumMaps(RobotiqHandJointNameMinimal.class);
   private static final SideDependentList<EnumMap<RobotiqHandJointNameMinimal, Double>> openHandsPinchGripDesiredConfigurations = SideDependentList.createListOfEnumMaps(RobotiqHandJointNameMinimal.class);
   private static final SideDependentList<EnumMap<RobotiqHandJointNameMinimal, Double>> closedHandsPinchGripDesiredConfigurations = SideDependentList.createListOfEnumMaps(RobotiqHandJointNameMinimal.class);
   
   static
   {
      createCloseHandBasicGripConfiguration();
      createOpenHandBasicGripConfiguration();
      createOpenHandPinchGripConfiguration();
      createClosedHandPinchGripConfiguration();
   }

   private static void createCloseHandBasicGripConfiguration()
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         EnumMap<RobotiqHandJointNameMinimal, Double> handDesiredConfigurations = closedHandsBasicGripDesiredConfigurations.get(robotSide);
         
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_1_JOINT, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_1, 1.12);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_2, 1.57);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_3, 0.75);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_2_JOINT, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_1, 1.12);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_2, 1.57);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_3, 0.75);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_1, 1.22);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_2, 1.57);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_3, 1.04);
      }
   }

   private static void createOpenHandBasicGripConfiguration()
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         EnumMap<RobotiqHandJointNameMinimal, Double> handDesiredConfigurations = openHandsBasicGripDesiredConfigurations.get(robotSide);
         
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_1_JOINT, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_1, -0.09);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_3, -0.95);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_2_JOINT, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_1, -0.09);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_3, -0.95);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_1, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_3, -0.66);
      }
   }
   
   private static void createOpenHandPinchGripConfiguration()
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         EnumMap<RobotiqHandJointNameMinimal, Double> handDesiredConfigurations = closedHandsPinchGripDesiredConfigurations.get(robotSide);
         
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_1_JOINT, -0.2);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_1, 0.5);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_3, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_2_JOINT, 0.2);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_1, 0.5);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_3, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_1, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_3, 0.5);
      }
   }
   
   private static void createClosedHandPinchGripConfiguration()
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         EnumMap<RobotiqHandJointNameMinimal, Double> handDesiredConfigurations = openHandsPinchGripDesiredConfigurations.get(robotSide);
         
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_1_JOINT, -0.2);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_1, -0.09);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_1_JOINT_3, -0.95);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.PALM_FINGER_2_JOINT, 0.2);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_1, -0.09);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_2_JOINT_3, -0.95);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_1, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_2, 0.0);
         handDesiredConfigurations.put(RobotiqHandJointNameMinimal.FINGER_MIDDLE_JOINT_3, -0.66);
      }
   }

   public static EnumMap<RobotiqHandJointNameMinimal, Double> getClosedBasicGripDesiredConfiguration(RobotSide robotSide)
   {
      return closedHandsBasicGripDesiredConfigurations.get(robotSide);
   }

   public static EnumMap<RobotiqHandJointNameMinimal, Double> getOpenBasicGripDesiredConfiguration(RobotSide robotSide)
   {
      return openHandsBasicGripDesiredConfigurations.get(robotSide);
   }
   
   public static EnumMap<RobotiqHandJointNameMinimal, Double> getOpenPinchGripDesiredConfiguration(RobotSide robotSide)
   {
      return openHandsPinchGripDesiredConfigurations.get(robotSide);
   }
   
   public static EnumMap<RobotiqHandJointNameMinimal, Double> getClosedPinchGripDesiredConfiguration(RobotSide robotiSide)
   {
      return closedHandsPinchGripDesiredConfigurations.get(robotiSide);
   }
}
