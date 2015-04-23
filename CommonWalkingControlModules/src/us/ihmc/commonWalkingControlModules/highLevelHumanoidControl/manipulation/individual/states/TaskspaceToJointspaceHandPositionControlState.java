package us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.states;

import java.util.LinkedHashMap;

import org.ejml.data.DenseMatrix64F;

import us.ihmc.commonWalkingControlModules.controlModules.RigidBodySpatialAccelerationControlModule;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.HandControlState;
import us.ihmc.commonWalkingControlModules.highLevelHumanoidControl.manipulation.individual.TaskspaceToJointspaceCalculator;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumBasedController;
import us.ihmc.utilities.FormattingTools;
import us.ihmc.utilities.math.MathTools;
import us.ihmc.utilities.math.geometry.FrameOrientation;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FramePose;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.OneDoFJoint;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.ScrewTools;
import us.ihmc.yoUtilities.controllers.PIDController;
import us.ihmc.yoUtilities.controllers.YoPIDGains;
import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;
import us.ihmc.yoUtilities.dataStructure.variable.BooleanYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.DoubleYoVariable;
import us.ihmc.yoUtilities.math.filters.RateLimitedYoVariable;
import us.ihmc.yoUtilities.math.trajectories.PoseTrajectoryGenerator;

public class TaskspaceToJointspaceHandPositionControlState extends TrajectoryBasedTaskspaceHandControlState
{
   private final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private PoseTrajectoryGenerator poseTrajectoryGenerator;

   private final FramePose desiredPose = new FramePose();
   private final FramePoint desiredPosition = new FramePoint(worldFrame);
   private final FrameVector desiredVelocity = new FrameVector(worldFrame);
   private final FrameVector desiredAcceleration = new FrameVector(worldFrame);

   private final FrameOrientation desiredOrientation = new FrameOrientation(worldFrame);
   private final FrameVector desiredAngularVelocity = new FrameVector(worldFrame);
   private final FrameVector desiredAngularAcceleration = new FrameVector(worldFrame);

   private TaskspaceToJointspaceCalculator taskspaceToJointspaceCalculator;
   private final DenseMatrix64F jointAngles;

   private final LinkedHashMap<OneDoFJoint, PIDController> pidControllers;
   private final LinkedHashMap<OneDoFJoint, RateLimitedYoVariable> rateLimitedAccelerations;
   private final DoubleYoVariable maxAcceleration;

   private final DoubleYoVariable doneTrajectoryTime;
   private final DoubleYoVariable holdPositionDuration;

   private final BooleanYoVariable doPositionControl;

   private final double dt;
   private final OneDoFJoint[] oneDoFJoints;
   private final boolean[] doIntegrateDesiredAccelerations;

   public TaskspaceToJointspaceHandPositionControlState(String namePrefix, HandControlState stateEnum, MomentumBasedController momentumBasedController,
         int jacobianId, RigidBody base, RigidBody endEffector, boolean doPositionControl, YoPIDGains gains, YoVariableRegistry parentRegistry)
   {
      super(namePrefix, stateEnum, momentumBasedController, jacobianId, base, endEffector, parentRegistry);

      doneTrajectoryTime = new DoubleYoVariable(namePrefix + "DoneTrajectoryTime", registry);
      holdPositionDuration = new DoubleYoVariable(namePrefix + "HoldPositionDuration", registry);

      this.doPositionControl = new BooleanYoVariable("doPositionControlForArmJointspaceController", registry);
      this.doPositionControl.set(doPositionControl);

      dt = momentumBasedController.getControlDT();

      oneDoFJoints = ScrewTools.createOneDoFJointPath(base, endEffector);

      doIntegrateDesiredAccelerations = new boolean[oneDoFJoints.length];

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         doIntegrateDesiredAccelerations[i] = joint.getIntegrateDesiredAccelerations();
      }

      maxAcceleration = gains.getYoMaximumAcceleration();

      pidControllers = new LinkedHashMap<OneDoFJoint, PIDController>();
      rateLimitedAccelerations = new LinkedHashMap<OneDoFJoint, RateLimitedYoVariable>();

      for (OneDoFJoint joint : oneDoFJoints)
      {
         String suffix = FormattingTools.lowerCaseFirstLetter(joint.getName());
         PIDController pidController = new PIDController(gains.getYoKp(), gains.getYoKi(), gains.getYoKd(), gains.getYoMaxIntegralError(), suffix, registry);
         pidControllers.put(joint, pidController);

         RateLimitedYoVariable rateLimitedAcceleration = new RateLimitedYoVariable(suffix + "Acceleration", registry, gains.getYoMaximumJerk(), dt);
         rateLimitedAccelerations.put(joint, rateLimitedAcceleration);
      }

      jointAngles = new DenseMatrix64F(oneDoFJoints.length, 1);
   }

   @Override
   public void doAction()
   {
      if (poseTrajectoryGenerator.isDone())
         recordDoneTrajectoryTime();

      poseTrajectoryGenerator.compute(getTimeInCurrentState());

      poseTrajectoryGenerator.get(desiredPose);
      poseTrajectoryGenerator.packLinearData(desiredPosition, desiredVelocity, desiredAcceleration);
      poseTrajectoryGenerator.packAngularData(desiredOrientation, desiredAngularVelocity, desiredAngularAcceleration);

      ReferenceFrame controlFrame = taskspaceToJointspaceCalculator.getControlFrame();
      desiredVelocity.changeFrame(controlFrame);
      desiredAngularVelocity.changeFrame(controlFrame);
      taskspaceToJointspaceCalculator.compute(desiredPosition, desiredOrientation, desiredVelocity, desiredAngularVelocity);
      taskspaceToJointspaceCalculator.packDesiredJointAnglesIntoOneDoFJoints(oneDoFJoints);
      taskspaceToJointspaceCalculator.packDesiredJointVelocitiesIntoOneDoFJoints(oneDoFJoints);

      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];

         double q = joint.getQ();
         double qd = joint.getQd();
         double qDesired = joint.getqDesired();
         double qdDesired = joint.getQdDesired();

         RateLimitedYoVariable rateLimitedAcceleration = rateLimitedAccelerations.get(joint);

         // TODO No feed-forward acceleration yet, needs to be implemented.
         double feedforwardAcceleration = 0.0;

         PIDController pidController = pidControllers.get(joint);
         double desiredAcceleration = feedforwardAcceleration + pidController.computeForAngles(q, qDesired, qd, qdDesired, dt);

         desiredAcceleration = MathTools.clipToMinMax(desiredAcceleration, maxAcceleration.getDoubleValue());

         rateLimitedAcceleration.update(desiredAcceleration);
         desiredAcceleration = rateLimitedAcceleration.getDoubleValue();

         momentumBasedController.setOneDoFJointAcceleration(joint, desiredAcceleration);
      }
   }

   @Override
   public void doTransitionIntoAction()
   {
      poseTrajectoryGenerator.showVisualization();
      poseTrajectoryGenerator.initialize();
      doneTrajectoryTime.set(Double.NaN);

      saveDoAccelerationIntegration();

      if (doPositionControl.getBooleanValue())
         enablePositionControl();

      ScrewTools.getJointPositions(oneDoFJoints, jointAngles);
      taskspaceToJointspaceCalculator.initialize(jointAngles);
   }

   @Override
   public void doTransitionOutOfAction()
   {
      holdPositionDuration.set(0.0);
      disablePositionControl();
   }

   private void saveDoAccelerationIntegration()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         doIntegrateDesiredAccelerations[i] = joint.getIntegrateDesiredAccelerations();
      }
   }

   private void enablePositionControl()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         joint.setIntegrateDesiredAccelerations(false);
         joint.setUnderPositionControl(true);
      }
   }

   private void disablePositionControl()
   {
      for (int i = 0; i < oneDoFJoints.length; i++)
      {
         OneDoFJoint joint = oneDoFJoints[i];
         joint.setIntegrateDesiredAccelerations(doIntegrateDesiredAccelerations[i]);
         joint.setUnderPositionControl(false);
      }
   }

   @Override
   public boolean isDone()
   {
      if (Double.isNaN(doneTrajectoryTime.getDoubleValue()))
         return false;

      return getTimeInCurrentState() > doneTrajectoryTime.getDoubleValue() + holdPositionDuration.getDoubleValue();
   }

   private void recordDoneTrajectoryTime()
   {
      if (Double.isNaN(doneTrajectoryTime.getDoubleValue()))
      {
         doneTrajectoryTime.set(getTimeInCurrentState());
      }
   }

   @Override
   public void setHoldPositionDuration(double holdPositionDuration)
   {
      this.holdPositionDuration.set(holdPositionDuration);
   }

   @Override
   public void setTrajectory(PoseTrajectoryGenerator poseTrajectoryGenerator)
   {
      this.poseTrajectoryGenerator = poseTrajectoryGenerator;
   }

   @Override
   public void setControlModuleForForceControl(RigidBodySpatialAccelerationControlModule handRigidBodySpatialAccelerationControlModule)
   {
   }

   @Override
   public void setControlModuleForPositionControl(TaskspaceToJointspaceCalculator taskspaceToJointspaceCalculator)
   {
      this.taskspaceToJointspaceCalculator = taskspaceToJointspaceCalculator;
   }

   @Override
   public FramePose getDesiredPose()
   {
      return desiredPose;
   }

   @Override
   public ReferenceFrame getReferenceFrame()
   {
      return desiredPose.getReferenceFrame();
   }
}
