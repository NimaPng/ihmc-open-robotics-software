package us.ihmc.commonWalkingControlModules.configurations;

public interface CapturePointPlannerParameters
{
   public abstract double getDoubleSupportInitialTransferDuration();

   @Deprecated
   //Not the right place to get this value from and it is not really used.
   public abstract double getDoubleSupportDuration();

   // FIXME That's a hack which makes the planner slower than the swing foot. Need to get rid of it.
   @Deprecated
   public abstract double getAdditionalTimeForSingleSupport();

   @Deprecated
   //Not the right place to get this value from and it is not really used.
   public abstract double getSingleSupportDuration();

   public abstract int getNumberOfFootstepsToConsider();

   public abstract int getNumberOfCoefficientsForDoubleSupportPolynomialTrajectory();

   @Deprecated
   // TODO This is not a parameter, where it is used it is supposed to be equal to 2.
   public abstract int getNumberOfFootstepsToStop();

   public abstract double getIsDoneTimeThreshold();

   public abstract double getDoubleSupportSplitFraction();

   public abstract double getFreezeTimeFactor();

   public abstract double getMaxInstantaneousCapturePointErrorForStartingSwing();

   public abstract double getMaxAllowedErrorWithoutPartialTimeFreeze();

   public abstract boolean getDoTimeFreezing();

   public abstract boolean getDoFootSlipCompensation();

   public abstract double getAlphaDeltaFootPositionForFootslipCompensation();

   public abstract double getReferenceCMPInsideOffset();

   public abstract double getReferenceCMPForwardOffset();

   public abstract boolean useNewICPPlanner();

   public abstract boolean useTwoCMPsPerSupport();

   public abstract double getTimeSpentOnExitCMPInPercentOfStepTime();

   public abstract double getMaxReferenceCMPForwardOffset();

   public abstract double getMinReferenceCMPForwardOffset();

   public abstract double getSafeDistanceForSupportEdges();

   public abstract boolean useTerribleHackToReduceICPVelocityAtTheEndOfTransfer();
}
