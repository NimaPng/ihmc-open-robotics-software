package us.ihmc.darpaRoboticsChallenge.posePlayback;

import java.util.EnumMap;

import javax.vecmath.Quat4d;

import us.ihmc.commonWalkingControlModules.partNamesAndTorques.ArmJointName;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.LegJointName;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.SpineJointName;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.robotSide.SideDependentList;
import us.ihmc.utilities.math.geometry.RotationFunctions;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.SimulationConstructionSet;
import com.yobotics.simulationconstructionset.VariableChangedListener;
import com.yobotics.simulationconstructionset.YoVariable;
import com.yobotics.simulationconstructionset.YoVariableRegistry;
import com.yobotics.simulationconstructionset.util.inputdevices.MidiSliderBoard;

public class DRCRobotMidiSliderBoardPositionManipulation
{
   private final SideDependentList<String> sideString = new SideDependentList<String>("q_l_", "q_r_");
   private final EnumMap<SpineJointName, String> spineJointStringNames = new EnumMap<SpineJointName, String>(SpineJointName.class);
   private final EnumMap<SpineJointName, Double> spineJointLowerLimits = new EnumMap<SpineJointName, Double>(SpineJointName.class);
   private final EnumMap<SpineJointName, Double> spineJointUpperLimits = new EnumMap<SpineJointName, Double>(SpineJointName.class);
   private final EnumMap<LegJointName, String> legJointStringNames = new EnumMap<LegJointName, String>(LegJointName.class);
   private final EnumMap<LegJointName, Double> legJointLowerLimits = new EnumMap<LegJointName, Double>(LegJointName.class);
   private final EnumMap<LegJointName, Double> legJointUpperLimits = new EnumMap<LegJointName, Double>(LegJointName.class);
   private final EnumMap<ArmJointName, String> armJointStringNames = new EnumMap<ArmJointName, String>(ArmJointName.class);
   private final EnumMap<ArmJointName, Double> armJointLowerLimits = new EnumMap<ArmJointName, Double>(ArmJointName.class);
   private final EnumMap<ArmJointName, Double> armJointUpperLimits = new EnumMap<ArmJointName, Double>(ArmJointName.class);

   private final YoVariableRegistry registry = new YoVariableRegistry("SliderBoardRegistry");
   
   private final BooleanYoVariable isCaptureSnapshotRequested = new BooleanYoVariable("isCaptureSnapshotRequested", registry);
   private final BooleanYoVariable isSaveSequenceRequested = new BooleanYoVariable("isSaveSequenceRequested", registry);
   private final BooleanYoVariable isLoadSequenceRequested = new BooleanYoVariable("isLoadSequenceRequested", registry);

   private final BooleanYoVariable isPelvisControlRequested = new BooleanYoVariable("isPelvisControlRequested", registry);
   private final BooleanYoVariable isChestControlRequested = new BooleanYoVariable("isChestControlRequested", registry);
   
   private final BooleanYoVariable isLeftLegControlRequested = new BooleanYoVariable("isLeftLegControlRequested", registry);
   private final BooleanYoVariable isRightLegControlRequested = new BooleanYoVariable("isRightLegControlRequested", registry);
   private final BooleanYoVariable isLeftArmControlRequested = new BooleanYoVariable("isLeftArmControlRequested", registry);
   private final BooleanYoVariable isRightArmControlRequested = new BooleanYoVariable("isRightArmControlRequested", registry);
   private final SideDependentList<BooleanYoVariable> isLegControlRequested = new SideDependentList<BooleanYoVariable>(isLeftLegControlRequested, isRightLegControlRequested);
   private final SideDependentList<BooleanYoVariable> isArmControlRequested = new SideDependentList<BooleanYoVariable>(isLeftArmControlRequested, isRightArmControlRequested);
   private final SimulationConstructionSet scs;
   private final MidiSliderBoard sliderBoard;

   private final DoubleYoVariable q_yaw = new DoubleYoVariable("q_yaw", registry);
   private final DoubleYoVariable q_pitch = new DoubleYoVariable("q_pitch", registry);
   private final DoubleYoVariable q_roll = new DoubleYoVariable("q_roll", registry);

   private final DoubleYoVariable q_qs, q_qx, q_qy, q_qz;

   public DRCRobotMidiSliderBoardPositionManipulation(SimulationConstructionSet scs)
   {
      this.scs = scs;
      scs.addYoVariableRegistry(registry);
      sliderBoard = new MidiSliderBoard(scs);
      init();
//      setupSliderForLegs(RobotSide.LEFT);

      q_qs = (DoubleYoVariable) scs.getVariable("q_qs");
      q_qx = (DoubleYoVariable) scs.getVariable("q_qx");
      q_qy = (DoubleYoVariable) scs.getVariable("q_qy");
      q_qz = (DoubleYoVariable) scs.getVariable("q_qz");
      
      ChangeCurrentPartBeingControlledListener listener = new ChangeCurrentPartBeingControlledListener();

      for (RobotSide robotSide : RobotSide.values)
      {
         isLegControlRequested.get(robotSide).addVariableChangedListener(listener);
         isArmControlRequested.get(robotSide).addVariableChangedListener(listener);
      }
      isPelvisControlRequested.addVariableChangedListener(listener);
      isChestControlRequested.addVariableChangedListener(listener);
      
      PelvisRotationListener pelvisListener = new PelvisRotationListener();
      q_yaw.addVariableChangedListener(pelvisListener);
      q_pitch.addVariableChangedListener(pelvisListener);
      q_roll.addVariableChangedListener(pelvisListener);
      
      isLeftLegControlRequested.set(true);
   }
   
   public void addCaptureSnapshotListener(VariableChangedListener variableChangedListener)
   {
      isCaptureSnapshotRequested.addVariableChangedListener(variableChangedListener);
   }
   
   public void addSaveSequenceRequestedListener(VariableChangedListener variableChangedListener)
   {
      isSaveSequenceRequested.addVariableChangedListener(variableChangedListener);
   }
   
   public void addLoadSequenceRequestedListener(VariableChangedListener variableChangedListener)
   {
      isLoadSequenceRequested.addVariableChangedListener(variableChangedListener);
   }

   private void init()
   {
      String prefix = "q_back_";
      spineJointStringNames.put(SpineJointName.SPINE_YAW, prefix + "lbz");
      spineJointStringNames.put(SpineJointName.SPINE_PITCH, prefix + "mby");
      spineJointStringNames.put(SpineJointName.SPINE_ROLL, prefix + "ubx");

      spineJointLowerLimits.put(SpineJointName.SPINE_YAW, -0.610865);
      spineJointLowerLimits.put(SpineJointName.SPINE_PITCH, -1.2);
      spineJointLowerLimits.put(SpineJointName.SPINE_ROLL, -0.790809);

      spineJointUpperLimits.put(SpineJointName.SPINE_YAW, 0.610865);
      spineJointUpperLimits.put(SpineJointName.SPINE_PITCH, 1.28);
      spineJointUpperLimits.put(SpineJointName.SPINE_ROLL, 0.790809);
      
      String legPrefix = "leg_";
      legJointStringNames.put(LegJointName.HIP_YAW, legPrefix + "uhz");
      legJointStringNames.put(LegJointName.HIP_ROLL, legPrefix + "mhx");
      legJointStringNames.put(LegJointName.HIP_PITCH, legPrefix + "lhy");
      legJointStringNames.put(LegJointName.KNEE, legPrefix + "kny");
      legJointStringNames.put(LegJointName.ANKLE_PITCH, legPrefix + "uay");
      legJointStringNames.put(LegJointName.ANKLE_ROLL, legPrefix + "lax");

      legJointLowerLimits.put(LegJointName.HIP_YAW, -0.32);
      legJointLowerLimits.put(LegJointName.HIP_ROLL,-0.47 );
      legJointLowerLimits.put(LegJointName.HIP_PITCH, -1.75);
      legJointLowerLimits.put(LegJointName.KNEE, 0.0);
      legJointLowerLimits.put(LegJointName.ANKLE_PITCH, -0.698);
      legJointLowerLimits.put(LegJointName.ANKLE_ROLL, -0.436);

      legJointUpperLimits.put(LegJointName.HIP_YAW, 1.14);
      legJointUpperLimits.put(LegJointName.HIP_ROLL, 0.495);
      legJointUpperLimits.put(LegJointName.HIP_PITCH, 0.524);
      legJointUpperLimits.put(LegJointName.KNEE, 2.45);
      legJointUpperLimits.put(LegJointName.ANKLE_PITCH, 0.698);
      legJointUpperLimits.put(LegJointName.ANKLE_ROLL, 0.436);

      String armPrefix = "arm_";
      armJointStringNames.put(ArmJointName.SHOULDER_PITCH, armPrefix + "usy");
      armJointStringNames.put(ArmJointName.SHOULDER_ROLL, armPrefix + "shx");
      armJointStringNames.put(ArmJointName.ELBOW_PITCH, armPrefix + "ely");
      armJointStringNames.put(ArmJointName.ELBOW_ROLL, armPrefix + "elx");
      armJointStringNames.put(ArmJointName.WRIST_PITCH, armPrefix + "uwy");
      armJointStringNames.put(ArmJointName.WRIST_ROLL, armPrefix + "mwx");

      armJointLowerLimits.put(ArmJointName.SHOULDER_PITCH, -1.9635);
      armJointLowerLimits.put(ArmJointName.SHOULDER_ROLL, -1.39626);
      armJointLowerLimits.put(ArmJointName.ELBOW_PITCH, 0.0);
      armJointLowerLimits.put(ArmJointName.ELBOW_ROLL, -2.35619);
      armJointLowerLimits.put(ArmJointName.WRIST_PITCH, -1.571);
      armJointLowerLimits.put(ArmJointName.WRIST_ROLL, -0.436);

      armJointUpperLimits.put(ArmJointName.SHOULDER_PITCH, 1.9635);
      armJointUpperLimits.put(ArmJointName.SHOULDER_ROLL, 1.74533);
      armJointUpperLimits.put(ArmJointName.ELBOW_PITCH, 3.14159);
      armJointUpperLimits.put(ArmJointName.ELBOW_ROLL, 0.0);
      armJointUpperLimits.put(ArmJointName.WRIST_PITCH, 1.571);
      armJointUpperLimits.put(ArmJointName.WRIST_ROLL, 1.571);
   }

   private void resetSliderBoard()
   {
      EnumMap<SpineJointName, Double> spineJointPositions = new EnumMap<SpineJointName, Double>(SpineJointName.class);
      for (SpineJointName spineJointName : spineJointStringNames.keySet())
      {
         double q = ((DoubleYoVariable)scs.getVariable(spineJointStringNames.get(spineJointName))).getDoubleValue();
         spineJointPositions.put(spineJointName, q);
      }
      SideDependentList<EnumMap<ArmJointName, Double>> armJointPositions = SideDependentList.createListOfEnumMaps(ArmJointName.class);
      SideDependentList<EnumMap<LegJointName, Double>> legJointPositions = SideDependentList.createListOfEnumMaps(LegJointName.class);
      for (RobotSide robotSide : RobotSide.values)
      {
         String prefix = sideString.get(robotSide);
         EnumMap<ArmJointName, Double> armTemp = new EnumMap<ArmJointName, Double>(ArmJointName.class);
         for (ArmJointName armJointName : armJointStringNames.keySet())
         {
            double q = ((DoubleYoVariable)scs.getVariable(prefix + armJointStringNames.get(armJointName))).getDoubleValue();
            armTemp.put(armJointName, q);
         }
         armJointPositions.put(robotSide, armTemp);
         
         EnumMap<LegJointName, Double> legTemp = new EnumMap<LegJointName, Double>(LegJointName.class);
         for (LegJointName legJointName : legJointStringNames.keySet())
         {
            double q = ((DoubleYoVariable)scs.getVariable(prefix + legJointStringNames.get(legJointName))).getDoubleValue();
            legTemp.put(legJointName, q);
         }
         legJointPositions.put(robotSide, legTemp);
      }

      double q_x = ((DoubleYoVariable) scs.getVariable("q_x")).getDoubleValue();
      double q_y = ((DoubleYoVariable) scs.getVariable("q_y")).getDoubleValue();
      double q_z = ((DoubleYoVariable) scs.getVariable("q_z")).getDoubleValue();
      double q_qs = ((DoubleYoVariable) scs.getVariable("q_qs")).getDoubleValue();
      double q_qx = ((DoubleYoVariable) scs.getVariable("q_qx")).getDoubleValue();
      double q_qy = ((DoubleYoVariable) scs.getVariable("q_qy")).getDoubleValue();
      double q_qz = ((DoubleYoVariable) scs.getVariable("q_qz")).getDoubleValue();
      
      sliderBoard.reset();

      for (SpineJointName spineJointName : spineJointStringNames.keySet())
      {
         double q = spineJointPositions.get(spineJointName);
         ((DoubleYoVariable)scs.getVariable(spineJointStringNames.get(spineJointName))).set(q);
      }
      for (RobotSide robotSide : RobotSide.values)
      {
         String prefix = sideString.get(robotSide);
         for (ArmJointName armJointName : armJointStringNames.keySet())
         {
            double q = armJointPositions.get(robotSide).get(armJointName);
            ((DoubleYoVariable)scs.getVariable(prefix + armJointStringNames.get(armJointName))).set(q);
         }
         for (LegJointName legJointName : legJointStringNames.keySet())
         {
            double q = legJointPositions.get(robotSide).get(legJointName);
            ((DoubleYoVariable)scs.getVariable(prefix + legJointStringNames.get(legJointName))).set(q);
         }
      }
      
      ((DoubleYoVariable) scs.getVariable("q_x")).set(q_x);
      ((DoubleYoVariable) scs.getVariable("q_y")).set(q_y);
      ((DoubleYoVariable) scs.getVariable("q_z")).set(q_z);
      ((DoubleYoVariable) scs.getVariable("q_qs")).set(q_qs);
      ((DoubleYoVariable) scs.getVariable("q_qx")).set(q_qx);
      ((DoubleYoVariable) scs.getVariable("q_qy")).set(q_qy);
      ((DoubleYoVariable) scs.getVariable("q_qz")).set(q_qz);

      int buttonChannel = 1;
      sliderBoard.setButton(buttonChannel++, isCaptureSnapshotRequested.getName(), scs);
      sliderBoard.setButton(buttonChannel++, isSaveSequenceRequested.getName(), scs);
      sliderBoard.setButton(buttonChannel++, isLoadSequenceRequested.getName(), scs);
      buttonChannel = 9;
      sliderBoard.setButton(buttonChannel++, isPelvisControlRequested.getName(), scs);
      sliderBoard.setButton(buttonChannel++, isChestControlRequested.getName(), scs);

      buttonChannel = 17;
      sliderBoard.setButton(buttonChannel++, isLeftArmControlRequested.getName(), scs);
      sliderBoard.setButton(buttonChannel++, isRightArmControlRequested.getName(), scs);
      sliderBoard.setButton(buttonChannel++, isLeftLegControlRequested.getName(), scs);
      sliderBoard.setButton(buttonChannel++, isRightLegControlRequested.getName(), scs);

   }

   public void setupSliderForLegs(RobotSide robotSide)
   {
      resetSliderBoard();

      int sliderChannel = 1;
      String prefix = sideString.get(robotSide);

      for (LegJointName legJointName : legJointStringNames.keySet())
      {
         sliderBoard.setSlider(sliderChannel++, prefix + legJointStringNames.get(legJointName), scs, legJointLowerLimits.get(legJointName), legJointUpperLimits.get(legJointName));
      }
   }

   private void setupSliderForArms(RobotSide robotSide)
   {
      resetSliderBoard();

      int sliderChannel = 1;
      String prefix = sideString.get(robotSide);

      for (ArmJointName armJointName : armJointStringNames.keySet())
         sliderBoard.setSlider(sliderChannel++, prefix + armJointStringNames.get(armJointName), scs, armJointLowerLimits.get(armJointName), armJointUpperLimits.get(armJointName));
   }

   private void setupSliderForPelvis()
   {
      resetSliderBoard();

      int sliderChannel = 1;
      double angle_min = - Math.PI;
      double angle_max = + Math.PI;
      double qmin = -1.0;
      double qmax = 1.0;
      sliderBoard.setSlider(sliderChannel++, "q_x", scs, qmin, qmax);
      sliderBoard.setSlider(sliderChannel++, "q_y", scs, qmin, qmax);
      sliderBoard.setSlider(sliderChannel++, "q_z", scs, qmin, qmax);
      sliderBoard.setSlider(sliderChannel++, "q_yaw", scs, angle_min, angle_max);
      sliderBoard.setSlider(sliderChannel++, "q_pitch", scs, angle_min, angle_max);
      sliderBoard.setSlider(sliderChannel++, "q_roll", scs, angle_min, angle_max);

   }

   private void setupSliderForChest()
   {
      resetSliderBoard();

      int sliderChannel = 1;

      for (SpineJointName spineJointName : spineJointStringNames.keySet())
         sliderBoard.setSlider(sliderChannel++, spineJointStringNames.get(spineJointName), scs, spineJointLowerLimits.get(spineJointName), spineJointUpperLimits.get(spineJointName));
   }

   private class ChangeCurrentPartBeingControlledListener implements VariableChangedListener
   {
      public void variableChanged(YoVariable v)
      {
         if (!(v instanceof BooleanYoVariable))
            return;
         
         for (RobotSide robotSide : RobotSide.values)
         {
            if (v.equals(isLegControlRequested.get(robotSide)))
               setupSliderForLegs(robotSide);
            
            if (v.equals(isArmControlRequested.get(robotSide)))
               setupSliderForArms(robotSide);
         }

         if (v.equals(isPelvisControlRequested))
            setupSliderForPelvis();
         
         if (v.equals(isChestControlRequested))
            setupSliderForChest();
      }
   }
   
   private class PelvisRotationListener implements VariableChangedListener
   {
      public void variableChanged(YoVariable v)
      {
         if (!(v instanceof DoubleYoVariable))
            return;
         

         if (v.equals(q_yaw) || v.equals(q_pitch) || v.equals(q_roll))
            setYawPitchRoll();
      }
   }
   
   private void setYawPitchRoll()
   {
      Quat4d q = new Quat4d();
      RotationFunctions.setQuaternionBasedOnYawPitchRoll(q, q_yaw.getDoubleValue(), q_pitch.getDoubleValue(), q_roll.getDoubleValue());
      q_qs.set(q.w);
      q_qx.set(q.x);
      q_qy.set(q.y);
      q_qz.set(q.z);
   }
   
   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }
}
