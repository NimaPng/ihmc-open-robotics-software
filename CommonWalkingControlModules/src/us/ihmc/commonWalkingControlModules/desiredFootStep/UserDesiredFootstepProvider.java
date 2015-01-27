package us.ihmc.commonWalkingControlModules.desiredFootStep;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2d;

import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.utilities.math.geometry.FrameOrientation;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FramePoint2d;
import us.ihmc.utilities.math.geometry.FramePose;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.PoseReferenceFrame;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.robotSide.RobotSide;
import us.ihmc.utilities.robotSide.SideDependentList;
import us.ihmc.yoUtilities.dataStructure.listener.VariableChangedListener;
import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;
import us.ihmc.yoUtilities.dataStructure.variable.BooleanYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.DoubleYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.EnumYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.IntegerYoVariable;
import us.ihmc.yoUtilities.dataStructure.variable.YoVariable;
import us.ihmc.yoUtilities.humanoidRobot.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.yoUtilities.humanoidRobot.footstep.Footstep;

public class UserDesiredFootstepProvider implements FootstepProvider
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final SideDependentList<ContactablePlaneBody> bipedFeet;
   private final SideDependentList<ReferenceFrame> ankleZUpReferenceFrames;

   private final IntegerYoVariable userStepsToTake = new IntegerYoVariable("userStepsToTake", registry);
   private final EnumYoVariable<RobotSide> userStepFirstSide = new EnumYoVariable<RobotSide>("userStepFirstSide", registry, RobotSide.class);
   private final DoubleYoVariable userStepLength = new DoubleYoVariable("userStepLength", registry);
   private final DoubleYoVariable userStepWidth = new DoubleYoVariable("userStepWidth", registry);
   private final DoubleYoVariable userStepSideways = new DoubleYoVariable("userStepSideways", registry);
   private final DoubleYoVariable userStepMinWidth = new DoubleYoVariable("userStepMinWidth", registry);
   private final DoubleYoVariable userStepHeight = new DoubleYoVariable("userStepHeight", registry);
   private final DoubleYoVariable userStepYaw = new DoubleYoVariable("userStepYaw", registry);
   private final BooleanYoVariable userStepsTakeEm = new BooleanYoVariable("userStepsTakeEm", registry);
   private final IntegerYoVariable userStepsNotifyCompleteCount = new IntegerYoVariable("userStepsNotifyCompleteCount", registry);

   private final DoubleYoVariable userStepHeelPercentage = new DoubleYoVariable("userStepHeelPercentage", registry);
   private final DoubleYoVariable userStepToePercentage = new DoubleYoVariable("userStepToePercentage", registry);

   private final ArrayList<Footstep> footstepList = new ArrayList<Footstep>();

   public UserDesiredFootstepProvider(SideDependentList<ContactablePlaneBody> bipedFeet, SideDependentList<ReferenceFrame> ankleZUpReferenceFrames,
         final WalkingControllerParameters walkingControllerParameters, YoVariableRegistry parentRegistry)
   {
      parentRegistry.addChild(registry);
      this.bipedFeet = bipedFeet;
      this.ankleZUpReferenceFrames = ankleZUpReferenceFrames;

      userStepWidth.set((walkingControllerParameters.getMaxStepWidth() + walkingControllerParameters.getMinStepWidth()) / 2);
      userStepMinWidth.set(walkingControllerParameters.getMinStepWidth());
      userStepFirstSide.set(RobotSide.LEFT);

      userStepHeelPercentage.set(1.0);
      userStepToePercentage.set(1.0);

      userStepLength.addVariableChangedListener(new VariableChangedListener()
      {

         @Override
         public void variableChanged(YoVariable<?> v)
         {
            if (v.getValueAsDouble() > walkingControllerParameters.getMaxStepLength())
            {
               v.setValueFromDouble(walkingControllerParameters.getMaxStepLength());
            }
         }
      });
   }

   @Override
   public Footstep poll()
   {
      if (userStepsTakeEm.getBooleanValue())
      {
         footstepList.clear();
         userStepsTakeEm.set(false);

         RobotSide stepSide = userStepFirstSide.getEnumValue();
         if (stepSide == null)
            stepSide = RobotSide.LEFT;

         Footstep previousFootstep = null;

         for (int i = 0; i < userStepsToTake.getIntegerValue(); i++)
         {
            Footstep footstep;

            if (i == 0)
            {
               footstep = createFirstFootstep(stepSide);
            }
            else
            {
               footstep = createNextFootstep(previousFootstep, stepSide);
            }

            footstepList.add(footstep);
            previousFootstep = footstep;
            stepSide = stepSide.getOppositeSide();
         }
      }

      if (footstepList.isEmpty())
         return null;

      Footstep ret = footstepList.get(0);
      footstepList.remove(0);

      return ret;
   }

   private Footstep createFirstFootstep(RobotSide swingLegSide)
   {
      RobotSide supportLegSide = swingLegSide.getOppositeSide();

      // Footstep Frame
      ReferenceFrame supportAnkleZUpFrame = ankleZUpReferenceFrames.get(supportLegSide);

      Footstep footstep = createFootstep(supportAnkleZUpFrame, swingLegSide);

      return footstep;
   }

   private Footstep createNextFootstep(Footstep previousFootstep, RobotSide swingLegSide)
   {
      FramePose pose = new FramePose();
      previousFootstep.getPose(pose);
      PoseReferenceFrame referenceFrame = new PoseReferenceFrame("step" + userStepsNotifyCompleteCount.getIntegerValue(), pose);

      return createFootstep(referenceFrame, swingLegSide);
   }

   private Footstep createFootstep(ReferenceFrame previousFootFrame, RobotSide swingLegSide)
   {
      RobotSide supportLegSide = swingLegSide.getOppositeSide();

      // Footstep Position
      FramePoint footstepPosition = new FramePoint(previousFootFrame);
      double stepYOffset = supportLegSide.negateIfLeftSide(userStepWidth.getDoubleValue()) + userStepSideways.getDoubleValue();
      if ((supportLegSide == RobotSide.LEFT) && (stepYOffset > -userStepMinWidth.getDoubleValue()))
      {
         stepYOffset = -userStepMinWidth.getDoubleValue();
      }
      if ((supportLegSide == RobotSide.RIGHT) && (stepYOffset < userStepMinWidth.getDoubleValue()))
      {
         stepYOffset = userStepMinWidth.getDoubleValue();
      }

      FrameVector footstepOffset = new FrameVector(previousFootFrame, userStepLength.getDoubleValue(), stepYOffset, userStepHeight.getDoubleValue());

      footstepPosition.add(footstepOffset);

      // Footstep Orientation
      FrameOrientation footstepOrientation = new FrameOrientation(previousFootFrame);
      footstepOrientation.setYawPitchRoll(userStepYaw.getDoubleValue(), 0.0, 0.0);

      // Create a foot Step Pose from Position and Orientation
      FramePose footstepPose = new FramePose(footstepPosition, footstepOrientation);
      footstepPose.changeFrame(ReferenceFrame.getWorldFrame());
      PoseReferenceFrame footstepPoseFrame = new PoseReferenceFrame("footstepPoseFrame", footstepPose);

      ContactablePlaneBody foot = bipedFeet.get(swingLegSide);

      boolean trustHeight = false;
      Footstep desiredFootstep = new Footstep(foot.getRigidBody(), swingLegSide, foot.getSoleFrame(), footstepPoseFrame, trustHeight);

      List<FramePoint2d> contactFramePoints = foot.getContactPoints2d();
      ArrayList<Point2d> contactPoints = new ArrayList<Point2d>();

      for (FramePoint2d contactFramePoint : contactFramePoints)
      {
         Point2d contactPoint = contactFramePoint.getPointCopy();

         if (contactFramePoint.getX() > 0.0)
         {
            contactPoint.setX(contactPoint.getX() * userStepToePercentage.getDoubleValue());
         }
         else
         {
            contactPoint.setX(contactPoint.getX() * userStepHeelPercentage.getDoubleValue());
         }

         contactPoints.add(contactPoint);
      }

      desiredFootstep.setPredictedContactPointsFromPoint2ds(contactPoints);

      return desiredFootstep;
   }

   @Override
   public Footstep peek()
   {
      if (footstepList.isEmpty())
         return null;

      Footstep ret = footstepList.get(0);

      return ret;
   }

   @Override
   public Footstep peekPeek()
   {
      if (footstepList.size() < 2)
         return null;

      Footstep ret = footstepList.get(1);

      return ret;
   }

   @Override
   public boolean isEmpty()
   {
      return footstepList.isEmpty();
   }

   @Override
   public void notifyComplete(FramePose actualFootPoseInWorld)
   {
      userStepsNotifyCompleteCount.increment();
   }

   @Override
   public void notifyWalkingComplete()
   {
   }

   @Override
   public int getNumberOfFootstepsToProvide()
   {
      return footstepList.size();
   }

   @Override
   public boolean isBlindWalking()
   {
      return true;
   }
}