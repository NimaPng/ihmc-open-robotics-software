package us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.icpOptimization;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.BipedSupportPolygons;
import us.ihmc.commonWalkingControlModules.configurations.CapturePointPlannerParameters;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.icpOptimization.multipliers.StateMultiplierCalculator;
import us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.smoothICPGenerator.CapturePointTools;
import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.ArtifactList;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.humanoidRobotics.footstep.FootstepTiming;
import us.ihmc.robotics.dataStructures.registry.YoVariableRegistry;
import us.ihmc.robotics.dataStructures.variable.BooleanYoVariable;
import us.ihmc.robotics.dataStructures.variable.DoubleYoVariable;
import us.ihmc.robotics.dataStructures.variable.EnumYoVariable;
import us.ihmc.robotics.dataStructures.variable.IntegerYoVariable;
import us.ihmc.robotics.geometry.FramePoint2d;
import us.ihmc.robotics.geometry.FrameVector2d;
import us.ihmc.robotics.math.frames.YoFramePoint2d;
import us.ihmc.robotics.math.frames.YoFrameVector2d;
import us.ihmc.robotics.referenceFrames.ReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.robotics.time.ExecutionTimer;
import us.ihmc.tools.exceptions.NoConvergenceException;

import java.util.ArrayList;
import java.util.List;

public class ICPTimingOptimizationController implements ICPOptimizationController
{
   private static final boolean VISUALIZE = false;
   private static final boolean COMPUTE_COST_TO_GO = false;
   private static final boolean RECONSTRUCT_CMP_FROM_UNCLIPPED = true;
   private static final boolean ALLOW_ADJUSTMENT_IN_TRANSFER = false;
   private static final boolean DEBUG = false;

   private boolean varyPositiveDirection = true;
   private static final double SWING_DURATION_VARIATION = 0.01;
   private static final double TIMING_ADJUSTMENT_COST = 100.0;
   private static final double QUADRATIC_COST_SCALE_FACTOR = 100.0;
   private static final double GRADIENT_THRESHOLD = 1.0;
   private static final double GRADIENT_GAIN = 0.5;
   private static final int NUMBER_OF_ITERATIONS = 30;

   private static final String yoNamePrefix = "controller";
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final IntegerYoVariable numberOfFootstepsToConsider = new IntegerYoVariable(yoNamePrefix + "NumberOfFootstepsToConsider", registry);

   private final boolean useTwoCMPs;
   private final boolean useFootstepRegularization;
   private final boolean useFeedbackRegularization;

   private final BooleanYoVariable useStepAdjustment = new BooleanYoVariable(yoNamePrefix + "UseStepAdjustment", registry);
   private final BooleanYoVariable useAngularMomentum = new BooleanYoVariable(yoNamePrefix + "UseAngularMomentum", registry);

   private final BooleanYoVariable scaleStepRegularizationWeightWithTime = new BooleanYoVariable(yoNamePrefix + "ScaleStepRegularizationWeightWithTime", registry);
   private final BooleanYoVariable scaleFeedbackWeightWithGain = new BooleanYoVariable(yoNamePrefix + "ScaleFeedbackWeightWithGain", registry);
   private final BooleanYoVariable scaleUpcomingStepWeights = new BooleanYoVariable(yoNamePrefix + "ScaleUpcomingStepWeights", registry);

   private final BooleanYoVariable swingSpeedUpEnabled = new BooleanYoVariable(yoNamePrefix + "SwingSpeedUpEnabled", registry);

   private final BooleanYoVariable isStanding = new BooleanYoVariable(yoNamePrefix + "IsStanding", registry);
   private final BooleanYoVariable isInTransfer = new BooleanYoVariable(yoNamePrefix + "IsInTransfer", registry);

   private final List<DoubleYoVariable> swingDurations = new ArrayList<>();
   private final List<DoubleYoVariable> transferDurations = new ArrayList<>();
   private final DoubleYoVariable finalTransferDuration = new DoubleYoVariable(yoNamePrefix + "FinalTransferDuration", registry);

   private final List<DoubleYoVariable> transferSplitFractions = new ArrayList<>();
   private final List<DoubleYoVariable> swingSplitFractions = new ArrayList<>();
   private final DoubleYoVariable defaultTransferSplitFraction = new DoubleYoVariable(yoNamePrefix + "DefaultTransferSplitFraction", registry);
   private final DoubleYoVariable defaultSwingSplitFraction = new DoubleYoVariable(yoNamePrefix + "DefaultSwingSplitFraction", registry);

   private final DoubleYoVariable transferSplitFractionUnderDisturbance;

   private final EnumYoVariable<RobotSide> transferToSide = new EnumYoVariable<>(yoNamePrefix + "TransferToSide", registry, RobotSide.class, true);
   private final EnumYoVariable<RobotSide> supportSide = new EnumYoVariable<>(yoNamePrefix + "SupportSide", registry, RobotSide.class, true);

   private final DoubleYoVariable initialTime = new DoubleYoVariable(yoNamePrefix + "InitialTime", registry);
   private final DoubleYoVariable timeInCurrentState = new DoubleYoVariable(yoNamePrefix + "TimeInCurrentState", registry);
   private final DoubleYoVariable timeRemainingInState = new DoubleYoVariable(yoNamePrefix + "TimeRemainingInState", registry);
   private final DoubleYoVariable speedUpTime = new DoubleYoVariable(yoNamePrefix + "SpeedUpTime", registry);
   private final DoubleYoVariable minimumTimeRemaining = new DoubleYoVariable(yoNamePrefix + "MinimumTimeRemaining", registry);

   private final FramePoint2d currentICP = new FramePoint2d();
   private final FramePoint2d desiredICP = new FramePoint2d();
   private final FrameVector2d desiredICPVelocity = new FrameVector2d();
   private final FramePoint2d referenceCMP = new FramePoint2d();

   private final YoFrameVector2d icpError = new YoFrameVector2d(yoNamePrefix + "IcpError", "", worldFrame, registry);
   private final YoFramePoint2d controllerFeedbackCMP = new YoFramePoint2d(yoNamePrefix + "FeedbackCMPSolution", worldFrame, registry);
   private final YoFrameVector2d controllerFeedbackCMPDelta = new YoFrameVector2d(yoNamePrefix + "FeedbackCMPDeltaSolution", worldFrame, registry);
   private final YoFramePoint2d dynamicRelaxation = new YoFramePoint2d(yoNamePrefix + "DynamicRelaxationSolution", "", worldFrame, registry);
   private final YoFramePoint2d angularMomentumSolution = new YoFramePoint2d(yoNamePrefix + "AngularMomentumSolution", "", worldFrame, registry);

   private final FramePoint2d finalICPRecursion = new FramePoint2d();
   private final FramePoint2d cmpConstantEffects = new FramePoint2d();

   private final YoFramePoint2d beginningOfStateICP = new YoFramePoint2d(yoNamePrefix + "BeginningOfStateICP", worldFrame, registry);
   private final YoFrameVector2d beginningOfStateICPVelocity = new YoFrameVector2d(yoNamePrefix + "BeginningOfStateICPVelocity", worldFrame, registry);

   private final ArrayList<Footstep> upcomingFootsteps = new ArrayList<>();
   private final ArrayList<YoFramePoint2d> upcomingFootstepLocations = new ArrayList<>();
   private final ArrayList<YoFramePoint2d> footstepSolutions = new ArrayList<>();
   private final ArrayList<FramePoint2d> unclippedFootstepSolutions = new ArrayList<>();

   private final DoubleYoVariable forwardFootstepWeight = new DoubleYoVariable(yoNamePrefix + "ForwardFootstepWeight", registry);
   private final DoubleYoVariable lateralFootstepWeight = new DoubleYoVariable(yoNamePrefix + "LateralFootstepWeight", registry);
   private final YoFramePoint2d scaledFootstepWeights = new YoFramePoint2d(yoNamePrefix + "ScaledFootstepWeights", worldFrame, registry);

   private final DoubleYoVariable feedbackForwardWeight = new DoubleYoVariable(yoNamePrefix + "FeedbackForwardWeight", registry);
   private final DoubleYoVariable feedbackLateralWeight = new DoubleYoVariable(yoNamePrefix + "FeedbackLateralWeight", registry);
   private final YoFramePoint2d scaledFeedbackWeight = new YoFramePoint2d(yoNamePrefix + "ScaledFeedbackWeight", worldFrame, registry);

   private final DoubleYoVariable footstepRegularizationWeight = new DoubleYoVariable(yoNamePrefix + "FootstepRegularizationWeight", registry);
   private final DoubleYoVariable feedbackRegularizationWeight = new DoubleYoVariable(yoNamePrefix + "FeedbackRegularizationWeight", registry);
   private final DoubleYoVariable scaledFootstepRegularizationWeight = new DoubleYoVariable(yoNamePrefix + "ScaledFootstepRegularizationWeight", registry);
   private final DoubleYoVariable dynamicRelaxationWeight = new DoubleYoVariable(yoNamePrefix + "DynamicRelaxationWeight", registry);
   private final DoubleYoVariable angularMomentumMinimizationWeight = new DoubleYoVariable(yoNamePrefix + "AngularMomentumMinimizationWeight", registry);

   private final DoubleYoVariable feedbackOrthogonalGain = new DoubleYoVariable(yoNamePrefix + "FeedbackOrthogonalGain", registry);
   private final DoubleYoVariable feedbackParallelGain = new DoubleYoVariable(yoNamePrefix + "FeedbackParallelGain", registry);

   private final IntegerYoVariable numberOfIterations = new IntegerYoVariable(yoNamePrefix + "NumberOfIterations", registry);
   private final BooleanYoVariable hasNotConvergedInPast = new BooleanYoVariable(yoNamePrefix + "HasNotConvergedInPast", registry);
   private final IntegerYoVariable hasNotConvergedCounts = new IntegerYoVariable(yoNamePrefix + "HasNotConvergedCounts", registry);

   private final BooleanYoVariable doingBigAdjustment = new BooleanYoVariable(yoNamePrefix + "DoingBigAdjustment", registry);

   private final DoubleYoVariable magnitudeForBigAdjustment;
   private final boolean useDifferentSplitRatioForBigAdjustment;
   private final double minimumTimeOnInitialCMPForBigAdjustment;

   private final ExecutionTimer qpSolverTimer = new ExecutionTimer("icpQPSolverTimer", 0.5, registry);
   private final ExecutionTimer controllerTimer = new ExecutionTimer("icpControllerTimer", 0.5, registry);
   private final ExecutionTimer conditionSettingTimer = new ExecutionTimer("icpConditionSettingTimer", 0.5, registry);
   private final ExecutionTimer solutionReadingTimer = new ExecutionTimer("icpSolutionReadingTimer", 0.5, registry);
   private final ICPQPOptimizationSolver solver;

   private final List<DoubleYoVariable> timingAdjustments = new ArrayList<>();
   private final List<DoubleYoVariable> costToGos = new ArrayList<>();
   private final List<DoubleYoVariable> costToGoGradients = new ArrayList<>();
   private final IntegerYoVariable numberOfGradientIterations = new IntegerYoVariable("numberOfGradientIterations", registry);

   private final StateMultiplierCalculator stateMultiplierCalculator;

   private final ICPOptimizationCoPConstraintHandler cmpConstraintHandler;
   private final ICPOptimizationReachabilityConstraintHandler reachabilityConstraintHandler;
   private final ICPOptimizationSolutionHandler solutionHandler;
   private final ICPOptimizationInputHandler inputHandler;

   private final SideDependentList<? extends ContactablePlaneBody> contactableFeet;

   private final double controlDT;
   private final double dynamicRelaxationDoubleSupportWeightModifier;
   private final int maximumNumberOfFootstepsToConsider;

   private final double mass;
   private final double gravityZ;

   private boolean localUseStepAdjustment;
   private boolean localScaleUpcomingStepWeights;

   private final SideDependentList<RigidBodyTransform> transformsFromAnkleToSole = new SideDependentList<>();

   private final FrameVector2d desiredCMPDelta = new FrameVector2d();
   private final FramePoint2d tempPoint2d = new FramePoint2d();
   private final FramePoint2d tmpFramePoint2d = new FramePoint2d();

   public ICPTimingOptimizationController(CapturePointPlannerParameters icpPlannerParameters, ICPOptimizationParameters icpOptimizationParameters,
         WalkingControllerParameters walkingControllerParameters, BipedSupportPolygons bipedSupportPolygons,
         SideDependentList<? extends ContactablePlaneBody> contactableFeet, double mass, double gravityZ, double controlDT, YoVariableRegistry parentRegistry,
         YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      this.contactableFeet = contactableFeet;
      this.controlDT = controlDT;
      this.mass = mass;
      this.gravityZ = gravityZ;
      
      for (RobotSide robotSide : RobotSide.values)
      {
         ContactablePlaneBody contactableFoot = contactableFeet.get(robotSide);
         ReferenceFrame ankleFrame = contactableFoot.getRigidBody().getParentJoint().getFrameAfterJoint();
         ReferenceFrame soleFrame = contactableFoot.getSoleFrame();
         RigidBodyTransform ankleToSole = new RigidBodyTransform();
         ankleFrame.getTransformToDesiredFrame(ankleToSole, soleFrame);
         transformsFromAnkleToSole.put(robotSide, ankleToSole);
      }

      maximumNumberOfFootstepsToConsider = icpOptimizationParameters.getMaximumNumberOfFootstepsToConsider();
      numberOfFootstepsToConsider.set(icpOptimizationParameters.numberOfFootstepsToConsider());

      int totalVertices = 0;
      for (RobotSide robotSide : RobotSide.values)
         totalVertices += contactableFeet.get(robotSide).getTotalNumberOfContactPoints();

      solver = new ICPQPOptimizationSolver(icpOptimizationParameters, totalVertices, true);

      useStepAdjustment.set(icpOptimizationParameters.useStepAdjustment());
      useAngularMomentum.set(icpOptimizationParameters.useAngularMomentum());

      useTwoCMPs = icpPlannerParameters.useTwoCMPsPerSupport();
      useFootstepRegularization = icpOptimizationParameters.useFootstepRegularization();
      useFeedbackRegularization = icpOptimizationParameters.useFeedbackRegularization();

      scaleStepRegularizationWeightWithTime.set(icpOptimizationParameters.scaleStepRegularizationWeightWithTime());
      scaleFeedbackWeightWithGain.set(icpOptimizationParameters.scaleFeedbackWeightWithGain());
      scaleUpcomingStepWeights.set(icpOptimizationParameters.scaleUpcomingStepWeights());

      forwardFootstepWeight.set(icpOptimizationParameters.getForwardFootstepWeight());
      lateralFootstepWeight.set(icpOptimizationParameters.getLateralFootstepWeight());
      footstepRegularizationWeight.set(icpOptimizationParameters.getFootstepRegularizationWeight());
      feedbackForwardWeight.set(icpOptimizationParameters.getFeedbackForwardWeight());
      feedbackLateralWeight.set(icpOptimizationParameters.getFeedbackLateralWeight());
      feedbackRegularizationWeight.set(icpOptimizationParameters.getFeedbackRegularizationWeight());
      feedbackOrthogonalGain.set(icpOptimizationParameters.getFeedbackOrthogonalGain());
      feedbackParallelGain.set(icpOptimizationParameters.getFeedbackParallelGain());
      dynamicRelaxationWeight.set(icpOptimizationParameters.getDynamicRelaxationWeight());
      angularMomentumMinimizationWeight.set(icpOptimizationParameters.getAngularMomentumMinimizationWeight());

      minimumTimeRemaining.set(icpOptimizationParameters.getMinimumTimeRemaining());

      if (walkingControllerParameters != null)
         swingSpeedUpEnabled.set(walkingControllerParameters.allowDisturbanceRecoveryBySpeedingUpSwing());
      else
         swingSpeedUpEnabled.set(false);

      dynamicRelaxationDoubleSupportWeightModifier = icpOptimizationParameters.getDynamicRelaxationDoubleSupportWeightModifier();

      defaultSwingSplitFraction.set(icpPlannerParameters.getSwingDurationAlpha());
      defaultTransferSplitFraction.set(icpPlannerParameters.getTransferDurationAlpha());

      transferSplitFractionUnderDisturbance = new DoubleYoVariable(yoNamePrefix + "DoubleSupportSplitFractionUnderDisturbance", registry);
      magnitudeForBigAdjustment = new DoubleYoVariable(yoNamePrefix + "MagnitudeForBigAdjustment", registry);

      transferSplitFractionUnderDisturbance.set(icpOptimizationParameters.getDoubleSupportSplitFractionForBigAdjustment());
      magnitudeForBigAdjustment.set(icpOptimizationParameters.getMagnitudeForBigAdjustment());
      useDifferentSplitRatioForBigAdjustment = icpOptimizationParameters.useDifferentSplitRatioForBigAdjustment();
      minimumTimeOnInitialCMPForBigAdjustment = icpOptimizationParameters.getMinimumTimeOnInitialCMPForBigAdjustment();

      stateMultiplierCalculator = new StateMultiplierCalculator(icpPlannerParameters, transferDurations, swingDurations, transferSplitFractions,
            swingSplitFractions, maximumNumberOfFootstepsToConsider, yoNamePrefix, registry);

      cmpConstraintHandler = new ICPOptimizationCoPConstraintHandler(bipedSupportPolygons, icpOptimizationParameters, yoNamePrefix, registry);
      reachabilityConstraintHandler = new ICPOptimizationReachabilityConstraintHandler(bipedSupportPolygons, icpOptimizationParameters, yoNamePrefix, registry);
      solutionHandler = new ICPOptimizationSolutionHandler(icpOptimizationParameters, stateMultiplierCalculator, transformsFromAnkleToSole, VISUALIZE, DEBUG,
                                                           yoNamePrefix, registry, yoGraphicsListRegistry);
      inputHandler = new ICPOptimizationInputHandler(icpPlannerParameters, bipedSupportPolygons, contactableFeet, maximumNumberOfFootstepsToConsider,
            stateMultiplierCalculator, transferDurations, swingDurations, transferSplitFractions, swingSplitFractions, VISUALIZE, yoNamePrefix, registry, yoGraphicsListRegistry);

      for (int i = 0; i < maximumNumberOfFootstepsToConsider; i++)
      {
         upcomingFootstepLocations.add(new YoFramePoint2d(yoNamePrefix + "UpcomingFootstepLocation" + i, worldFrame, registry));
         footstepSolutions.add(new YoFramePoint2d(yoNamePrefix + "FootstepSolutionLocation" + i, worldFrame, registry));
         unclippedFootstepSolutions.add(new FramePoint2d(worldFrame));

         DoubleYoVariable swingDuration = new DoubleYoVariable(yoNamePrefix + "SwingDuration" + i, registry);
         swingDuration.setToNaN();
         swingDurations.add(swingDuration);
         DoubleYoVariable transferDuration = new DoubleYoVariable(yoNamePrefix + "TransferDuration" + i, registry);
         transferDuration.setToNaN();
         transferDurations.add(transferDuration);

         DoubleYoVariable transferSplitFraction = new DoubleYoVariable(yoNamePrefix + "TransferSplitFraction" + i,
               "Repartition of the transfer duration around the entry corner point.", registry);
         transferSplitFraction.setToNaN();
         transferSplitFractions.add(transferSplitFraction);
         DoubleYoVariable swingSplitFraction = new DoubleYoVariable(yoNamePrefix + "SwingSplitFraction" + i,
               "Repartition of the transfer duration around the entry corner point.", registry);
         swingSplitFraction.setToNaN();
         swingSplitFractions.add(swingSplitFraction);
      }

      for (int i = 0; i < NUMBER_OF_ITERATIONS; i++)
      {
         DoubleYoVariable timingAdjustment = new DoubleYoVariable(yoNamePrefix + "TimingAdjustment" + i, registry);
         DoubleYoVariable costToGo = new DoubleYoVariable(yoNamePrefix + "CostToGo" + i, registry);
         DoubleYoVariable costToGoGradient = new DoubleYoVariable(yoNamePrefix + "CostToGoGradient" + i, registry);

         timingAdjustments.add(timingAdjustment);
         costToGos.add(costToGo);
         costToGoGradients.add(costToGoGradient);
      }

      if (yoGraphicsListRegistry != null)
         setupVisualizers(yoGraphicsListRegistry, VISUALIZE);

      parentRegistry.addChild(registry);
   }

   private void setupVisualizers(YoGraphicsListRegistry yoGraphicsListRegistry, boolean visualize)
   {
      ArtifactList artifactList = new ArtifactList(getClass().getSimpleName());

      YoGraphicPosition beginningOfStateICP = new YoGraphicPosition(yoNamePrefix + "BeginningOfStateICP`", this.beginningOfStateICP, 0.01, YoAppearance.MidnightBlue(),
            YoGraphicPosition.GraphicType.SOLID_BALL);

      artifactList.add(beginningOfStateICP.createArtifact());
      artifactList.setVisible(visualize);

      yoGraphicsListRegistry.registerArtifactList(artifactList);
   }


   @Override
   public void setFootstepWeights(double forwardWeight, double lateralWeight)
   {
      forwardFootstepWeight.set(forwardWeight);
      lateralFootstepWeight.set(lateralWeight);
   }

   @Override
   public void setFeedbackWeights(double forwardWeight, double lateralWeight)
   {
      feedbackForwardWeight.set(forwardWeight);
      feedbackLateralWeight.set(lateralWeight);
   }

   @Override
   public void setDynamicRelaxationWeight(double relaxationWeight)
   {
      dynamicRelaxationWeight.set(relaxationWeight);
   }

   @Override
   public void clearPlan()
   {
      upcomingFootsteps.clear();

      inputHandler.clearPlan();
      for (int i = 0; i < maximumNumberOfFootstepsToConsider; i++)
      {
         upcomingFootstepLocations.get(i).setToZero();

         swingDurations.get(i).setToNaN();
         transferDurations.get(i).setToNaN();

         swingSplitFractions.get(i).setToNaN();
         transferSplitFractions.get(i).setToNaN();
      }
   }

   @Override
   public void setFinalTransferDuration(double finalTransferDuration)
   {
      this.finalTransferDuration.set(finalTransferDuration);
   }

   @Override
   public void addFootstepToPlan(Footstep footstep, FootstepTiming timing)
   {
      if (footstep != null)
      {
         if (!footstep.getSoleReferenceFrame().getTransformToRoot().containsNaN())
         {
            upcomingFootsteps.add(footstep);
            RigidBodyTransform ankleToSole = transformsFromAnkleToSole.get(footstep.getRobotSide());
            footstep.getAnklePosition2d(tmpFramePoint2d, ankleToSole);
            upcomingFootstepLocations.get(upcomingFootsteps.size() - 1).set(tmpFramePoint2d);
            inputHandler.addFootstepToPlan(footstep);

            footstepSolutions.get(upcomingFootsteps.size() - 1).set(tmpFramePoint2d);
            unclippedFootstepSolutions.get(upcomingFootsteps.size() - 1).set(tmpFramePoint2d);

            int footstepIndex = upcomingFootsteps.size() - 1;
            swingDurations.get(footstepIndex).set(timing.getSwingTime());
            transferDurations.get(footstepIndex).set(timing.getTransferTime());
            swingSplitFractions.get(footstepIndex).set(defaultSwingSplitFraction.getDoubleValue());
            transferSplitFractions.get(footstepIndex).set(defaultTransferSplitFraction.getDoubleValue());
         }
         else
         {
            PrintTools.warn(this, "Received bad footstep: " + footstep);
         }
      }
   }

   public void submitRemainingTimeInSwingUnderDisturbance(double remainingTimeForSwing)
   {
      if (swingSpeedUpEnabled.getBooleanValue() && remainingTimeForSwing < timeRemainingInState.getDoubleValue())
      {
         double speedUpTime = timeRemainingInState.getDoubleValue() - remainingTimeForSwing;
         this.speedUpTime.add(speedUpTime);
      }
   }


   @Override
   public void initializeForStanding(double initialTime)
   {
      this.initialTime.set(initialTime);
      isStanding.set(true);
      isInTransfer.set(false);

      setProblemBooleans();

      cmpConstraintHandler.updateCoPConstraintForDoubleSupport(solver);
      reachabilityConstraintHandler.updateReachabilityConstraintForDoubleSupport(solver);

      speedUpTime.set(0.0);
      transferDurations.get(0).set(finalTransferDuration.getDoubleValue());
      transferSplitFractions.get(0).set(defaultTransferSplitFraction.getDoubleValue());
   }

   @Override
   public void initializeForTransfer(double initialTime, RobotSide transferToSide, double omega0)
   {
      this.transferToSide.set(transferToSide);
      if (transferToSide == null)
         transferToSide = RobotSide.LEFT;
      isInTransfer.set(true);

      int numberOfFootstepRegistered = upcomingFootsteps.size();
      transferDurations.get(numberOfFootstepRegistered).set(finalTransferDuration.getDoubleValue());
      transferSplitFractions.get(numberOfFootstepRegistered).set(defaultTransferSplitFraction.getDoubleValue());

      int numberOfFootstepsToConsider = initializeOnContactChange(initialTime);

      stateMultiplierCalculator.computeRecursionMultipliers(numberOfFootstepsToConsider, numberOfFootstepRegistered, isInTransfer.getBooleanValue(), useTwoCMPs, omega0);

      inputHandler.initializeForDoubleSupport(numberOfFootstepsToConsider, upcomingFootstepLocations, isStanding.getBooleanValue(), useTwoCMPs, transferToSide, omega0);

      cmpConstraintHandler.updateCoPConstraintForDoubleSupport(solver);
      reachabilityConstraintHandler.updateReachabilityConstraintForDoubleSupport(solver);
   }

   @Override
   public void initializeForSingleSupport(double initialTime, RobotSide supportSide, double omega0)
   {
      this.supportSide.set(supportSide);
      isStanding.set(false);
      isInTransfer.set(false);

      int numberOfFootstepRegistered = upcomingFootsteps.size();
      transferDurations.get(numberOfFootstepRegistered).set(finalTransferDuration.getDoubleValue());
      transferSplitFractions.get(numberOfFootstepRegistered).set(defaultTransferSplitFraction.getDoubleValue());

      int numberOfFootstepsToConsider = initializeOnContactChange(initialTime);

      stateMultiplierCalculator.computeRecursionMultipliers(numberOfFootstepsToConsider, numberOfFootstepRegistered, isInTransfer.getBooleanValue(), useTwoCMPs, omega0);

      inputHandler.initializeForSingleSupport(numberOfFootstepsToConsider, upcomingFootstepLocations, useTwoCMPs, supportSide, omega0);

      cmpConstraintHandler.updateCoPConstraintForSingleSupport(supportSide, solver);
      reachabilityConstraintHandler.updateReachabilityConstraintForSingleSupport(supportSide, solver);
   }

   private int initializeOnContactChange(double initialTime)
   {
      setProblemBooleans();

      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      this.initialTime.set(initialTime);
      speedUpTime.set(0.0);

      beginningOfStateICP.set(solutionHandler.getControllerReferenceICP());
      beginningOfStateICPVelocity.set(solutionHandler.getControllerReferenceICPVelocity());

      if (useFootstepRegularization)
         resetFootstepRegularizationTask();

      solver.resetOnContactChange();

      return numberOfFootstepsToConsider;
   }

   @Override
   public void setBeginningOfStateICP(FramePoint2d beginningOfStateICP, FrameVector2d beginningOfStateICPVelocity)
   {
      this.beginningOfStateICP.set(beginningOfStateICP);
      this.beginningOfStateICPVelocity.set(beginningOfStateICPVelocity);
   }

   private void setProblemBooleans()
   {
      localUseStepAdjustment = useStepAdjustment.getBooleanValue();
      localScaleUpcomingStepWeights = scaleUpcomingStepWeights.getBooleanValue();
      doingBigAdjustment.set(false);
   }


   @Override
   public void compute(double currentTime, FramePoint2d desiredICP, FrameVector2d desiredICPVelocity, FramePoint2d currentICP, double omega0)
   {
      controllerTimer.startMeasurement();

      desiredICP.changeFrame(worldFrame);
      desiredICPVelocity.changeFrame(worldFrame);
      currentICP.changeFrame(worldFrame);

      this.currentICP.set(currentICP);
      this.desiredICP.set(desiredICP);
      this.desiredICPVelocity.set(desiredICPVelocity);

      solutionHandler.getControllerReferenceCMP(referenceCMP);

      computeTimeInCurrentState(currentTime);
      computeTimeRemainingInState();

      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      scaleStepRegularizationWeightWithTime();
      scaleFeedbackWeightWithGain();

      NoConvergenceException noConvergenceException = solve(numberOfFootstepsToConsider, omega0);

      getSolutionsFromSolver(numberOfFootstepsToConsider, omega0, noConvergenceException);

      controllerTimer.stopMeasurement();
   }

   private void submitAllConditionsToSolver(int numberOfFootstepsToConsider, double omega0)
   {
      conditionSettingTimer.startMeasurement();
      if (isStanding.getBooleanValue())
         setConditionsForFeedbackOnlyControl();
      else
         setConditionsForSteppingControl(numberOfFootstepsToConsider, omega0);
      conditionSettingTimer.stopMeasurement();
   }

   private NoConvergenceException solve(int numberOfFootstepsToConsider, double omega0)
   {
      // submit the initial conditions
      submitAllConditionsToSolver(numberOfFootstepsToConsider, omega0);

      qpSolverTimer.startMeasurement();

      double originalSwingDuration = swingDurations.get(0).getDoubleValue();

      // Run first variation calculation
      NoConvergenceException noConvergenceException = solveQP();

      if (isInTransfer.getBooleanValue() || isStanding.getBooleanValue())
      {
         numberOfGradientIterations.set(0);
         return noConvergenceException;
      }

      double qpOriginalCostToGo = QUADRATIC_COST_SCALE_FACTOR * solver.getCostToGo();

      // modify current single support duration
      if (varyPositiveDirection)
      {
         swingDurations.get(0).add(SWING_DURATION_VARIATION);
         varyPositiveDirection = false;
      }
      else
      {
         swingDurations.get(0).sub(SWING_DURATION_VARIATION);
         varyPositiveDirection = true;
      }

      inputHandler.update(numberOfFootstepsToConsider, timeInCurrentState.getDoubleValue(), useTwoCMPs, isInTransfer.getBooleanValue(), omega0);
      inputHandler.computeFinalICPRecursion(finalICPRecursion);
      inputHandler.computeCMPConstantEffects(cmpConstantEffects, beginningOfStateICP.getFrameTuple2d(), beginningOfStateICPVelocity.getFrameTuple2d(), useTwoCMPs,
                  isInTransfer.getBooleanValue());

      if (localUseStepAdjustment && (!isInTransfer.getBooleanValue() || ALLOW_ADJUSTMENT_IN_TRANSFER))
      {
         double footstepRecursionMultiplier = stateMultiplierCalculator.getFootstepRecursionMultiplier(useTwoCMPs, 0);

         solver.setFootstepAdjustmentConditions(0, footstepRecursionMultiplier, scaledFootstepWeights.getX(), scaledFootstepWeights.getY(),
               upcomingFootstepLocations.get(0).getFrameTuple2d());
         submitFootstepConditionsToSolver(0);
      }

      noConvergenceException = solveQP();

      double variedCostToGo = TIMING_ADJUSTMENT_COST * Math.pow(SWING_DURATION_VARIATION, 2.0) + QUADRATIC_COST_SCALE_FACTOR * solver.getCostToGo();
      double costGradient = (variedCostToGo - qpOriginalCostToGo) / SWING_DURATION_VARIATION;

      costToGos.get(0).set(variedCostToGo);
      costToGoGradients.get(0).set(costGradient);
      timingAdjustments.get(0).set(SWING_DURATION_VARIATION);
      qpOriginalCostToGo = variedCostToGo;


      int iterationNumber = 0;
      while (costGradient > GRADIENT_THRESHOLD)
      {
         iterationNumber++;
         double variation = GRADIENT_GAIN * costGradient; // // FIXME: 4/29/17 do something fancy

         // modify current single support duration
         swingDurations.get(0).sub(SWING_DURATION_VARIATION);

         inputHandler.update(numberOfFootstepsToConsider, timeInCurrentState.getDoubleValue(), useTwoCMPs, isInTransfer.getBooleanValue(), omega0);
         inputHandler.computeFinalICPRecursion(finalICPRecursion);
         inputHandler.computeCMPConstantEffects(cmpConstantEffects, beginningOfStateICP.getFrameTuple2d(), beginningOfStateICPVelocity.getFrameTuple2d(), useTwoCMPs,
               isInTransfer.getBooleanValue());

         if (localUseStepAdjustment && (!isInTransfer.getBooleanValue() || ALLOW_ADJUSTMENT_IN_TRANSFER))
         {
            double footstepRecursionMultiplier = stateMultiplierCalculator.getFootstepRecursionMultiplier(useTwoCMPs, 0);

            solver.setFootstepAdjustmentConditions(0, footstepRecursionMultiplier, scaledFootstepWeights.getX(), scaledFootstepWeights.getY(),
                  upcomingFootstepLocations.get(0).getFrameTuple2d());
            submitFootstepConditionsToSolver(0);
         }

         noConvergenceException = solveQP();

         variedCostToGo = TIMING_ADJUSTMENT_COST * Math.pow(swingDurations.get(0).getDoubleValue() - originalSwingDuration, 2.0) + QUADRATIC_COST_SCALE_FACTOR * solver.getCostToGo();
         costGradient = (variedCostToGo - qpOriginalCostToGo) / variation;

         costToGos.get(iterationNumber).set(variedCostToGo);
         costToGoGradients.get(iterationNumber).set(costGradient);
         timingAdjustments.get(iterationNumber).set(SWING_DURATION_VARIATION);
         qpOriginalCostToGo = variedCostToGo;
      }

      numberOfGradientIterations.set(iterationNumber + 1);

      qpSolverTimer.stopMeasurement();

      return noConvergenceException;
   }

   private NoConvergenceException solveQP()
   {
      NoConvergenceException noConvergenceException = null;
      try
      {
         solver.compute(finalICPRecursion, cmpConstantEffects, currentICP, referenceCMP);

      }
      catch (NoConvergenceException e)
      {
         if (!hasNotConvergedInPast.getBooleanValue())
         {
            e.printStackTrace();
            PrintTools.warn(this, "Only showing the stack trace of the first " + e.getClass().getSimpleName() + ". This may be happening more than once.");
         }

         hasNotConvergedInPast.set(true);
         hasNotConvergedCounts.increment();

         noConvergenceException = e;
      }

      return noConvergenceException;
   }


   private void getSolutionsFromSolver(int numberOfFootstepsToConsider, double omega0, NoConvergenceException noConvergenceException)
   {
      solutionReadingTimer.startMeasurement();
      // don't pole the new solutions if there's a no convergence exception
      if (noConvergenceException == null)
      {
         numberOfIterations.set(solver.getNumberOfIterations());

         if (localUseStepAdjustment)
            solutionHandler.extractFootstepSolutions(footstepSolutions, unclippedFootstepSolutions, upcomingFootstepLocations, upcomingFootsteps, numberOfFootstepsToConsider, solver);

         solver.getCMPFeedbackDifference(desiredCMPDelta);

         if (COMPUTE_COST_TO_GO)
            solutionHandler.updateCostsToGo(solver);
      }

      if (isStanding.getBooleanValue())
      {
         solutionHandler.setValuesForFeedbackOnly(desiredICP, desiredICPVelocity, omega0);
      }
      else
      {
         if (RECONSTRUCT_CMP_FROM_UNCLIPPED)
            solutionHandler.computeReferenceFromSolutions(unclippedFootstepSolutions, inputHandler, beginningOfStateICP, beginningOfStateICPVelocity, omega0, numberOfFootstepsToConsider);
         else
            solutionHandler.yoComputeReferenceFromSolutions(footstepSolutions, inputHandler, beginningOfStateICP, beginningOfStateICPVelocity, omega0, numberOfFootstepsToConsider);

         if (DEBUG)
            solutionHandler.computeNominalValues(upcomingFootstepLocations, inputHandler, beginningOfStateICP, beginningOfStateICPVelocity, omega0, numberOfFootstepsToConsider);

         if (useDifferentSplitRatioForBigAdjustment && !isInTransfer.getBooleanValue())
            computeUpcomingDoubleSupportSplitFraction(numberOfFootstepsToConsider, omega0);
      }
      solutionReadingTimer.stopMeasurement();

      solver.getDynamicRelaxation(tempPoint2d);
      dynamicRelaxation.set(tempPoint2d);

      solver.getCMPDifferenceFromCoP(tempPoint2d);
      CapturePointTools.computeAngularMomentum(mass, gravityZ, tempPoint2d, tempPoint2d);
      angularMomentumSolution.set(tempPoint2d);

      icpError.set(currentICP);
      icpError.sub(solutionHandler.getControllerReferenceICP());

      solutionHandler.getControllerReferenceCMP(tempPoint2d);
      controllerFeedbackCMP.set(tempPoint2d);
      controllerFeedbackCMP.add(desiredCMPDelta);
      controllerFeedbackCMPDelta.set(desiredCMPDelta);
   }

   private void setConditionsForFeedbackOnlyControl()
   {
      solver.resetFootstepConditions();

      setFeedbackConditions();
      setAngularMomentumConditions();

      finalICPRecursion.set(desiredICP);
      cmpConstantEffects.setToZero();
   }

   private int setConditionsForSteppingControl(int numberOfFootstepsToConsider, double omega0)
   {
      inputHandler.update(numberOfFootstepsToConsider, timeInCurrentState.getDoubleValue(), useTwoCMPs, isInTransfer.getBooleanValue(), omega0);
      inputHandler.computeFinalICPRecursion(finalICPRecursion);
      inputHandler.computeCMPConstantEffects(cmpConstantEffects, beginningOfStateICP.getFrameTuple2d(), beginningOfStateICPVelocity.getFrameTuple2d(),
            useTwoCMPs, isInTransfer.getBooleanValue());

      if (isInTransfer.getBooleanValue())
      {
         cmpConstraintHandler.updateCoPConstraintForDoubleSupport(solver);
         reachabilityConstraintHandler.updateReachabilityConstraintForDoubleSupport(solver);
      }
      else
      {
         cmpConstraintHandler.updateCoPConstraintForSingleSupport(supportSide.getEnumValue(), solver);
         reachabilityConstraintHandler.updateReachabilityConstraintForSingleSupport(supportSide.getEnumValue(), solver);
      }

      solver.resetFootstepConditions();

      if (useFeedbackRegularization)
         solver.setFeedbackRegularizationWeight(feedbackRegularizationWeight.getDoubleValue() / controlDT);

      if (localUseStepAdjustment && (!isInTransfer.getBooleanValue() || ALLOW_ADJUSTMENT_IN_TRANSFER))
      {
         for (int footstepIndex = 0; footstepIndex < numberOfFootstepsToConsider; footstepIndex++)
            submitFootstepConditionsToSolver(footstepIndex);

         if (useFootstepRegularization)
            solver.setFootstepRegularizationWeight(scaledFootstepRegularizationWeight.getDoubleValue() / controlDT);
      }

      setFeedbackConditions();
      setAngularMomentumConditions();

      return numberOfFootstepsToConsider;
   }

   private final FrameVector2d feedbackGains = new FrameVector2d();
   private void setFeedbackConditions()
   {
      ICPOptimizationControllerHelper.transformFeedbackGains(feedbackGains, desiredICPVelocity, feedbackParallelGain, feedbackOrthogonalGain);

      double dynamicRelaxationWeight = this.dynamicRelaxationWeight.getDoubleValue();
      if (!localUseStepAdjustment)
         dynamicRelaxationWeight = dynamicRelaxationWeight / dynamicRelaxationDoubleSupportWeightModifier;

      solver.resetFeedbackConditions();
      solver.setFeedbackConditions(scaledFeedbackWeight.getX(), scaledFeedbackWeight.getY(), feedbackGains.getX(), feedbackGains.getY(), dynamicRelaxationWeight);
   }

   private void setAngularMomentumConditions()
   {
      double angularMomentumMinimizationWeight = this.angularMomentumMinimizationWeight.getDoubleValue();

      solver.resetAngularMomentumConditions();
      solver.setAngularMomentumConditions(angularMomentumMinimizationWeight, useAngularMomentum.getBooleanValue());
   }

   private void resetFootstepRegularizationTask()
   {
      int numberOfFootstepsToConsider = clipNumberOfFootstepsToConsiderToProblem(this.numberOfFootstepsToConsider.getIntegerValue());

      for (int i = 0; i < numberOfFootstepsToConsider; i++)
         solver.resetFootstepRegularization(i, upcomingFootstepLocations.get(i).getFrameTuple2d());
   }

   private int clipNumberOfFootstepsToConsiderToProblem(int numberOfFootstepsToConsider)
   {
      numberOfFootstepsToConsider = Math.min(numberOfFootstepsToConsider, upcomingFootsteps.size());
      numberOfFootstepsToConsider = Math.min(numberOfFootstepsToConsider, maximumNumberOfFootstepsToConsider);

      if (!localUseStepAdjustment || (isInTransfer.getBooleanValue() && !ALLOW_ADJUSTMENT_IN_TRANSFER) || isStanding.getBooleanValue())
         numberOfFootstepsToConsider = 0;

      return numberOfFootstepsToConsider;
   }

   private final FrameVector2d footstepWeights = new FrameVector2d();
   private void submitFootstepConditionsToSolver(int footstepIndex)
   {
      ReferenceFrame soleFrame = contactableFeet.get(supportSide.getEnumValue()).getSoleFrame();
      ICPOptimizationControllerHelper.transformWeightsToWorldFrame(footstepWeights, forwardFootstepWeight, lateralFootstepWeight, soleFrame);
      scaledFootstepWeights.set(footstepWeights);

      if (localScaleUpcomingStepWeights)
         scaledFootstepWeights.scale(1.0 / (footstepIndex + 1));

      double footstepRecursionMultiplier = stateMultiplierCalculator.getFootstepRecursionMultiplier(useTwoCMPs, footstepIndex);

      solver.setFootstepAdjustmentConditions(footstepIndex, footstepRecursionMultiplier, scaledFootstepWeights.getX(), scaledFootstepWeights.getY(),
            upcomingFootstepLocations.get(footstepIndex).getFrameTuple2d());
   }


   private void computeTimeInCurrentState(double currentTime)
   {
      timeInCurrentState.set(currentTime - initialTime.getDoubleValue() + speedUpTime.getDoubleValue());
   }

   private void computeTimeRemainingInState()
   {
      if (isStanding.getBooleanValue())
      {
         timeRemainingInState.set(0.0);
      }
      else
      {
         double remainingTime;
         if (isInTransfer.getBooleanValue())
            remainingTime = transferDurations.get(0).getDoubleValue() - timeInCurrentState.getDoubleValue();
         else
            remainingTime = swingDurations.get(0).getDoubleValue() - timeInCurrentState.getDoubleValue();

         timeRemainingInState.set(remainingTime);
      }
   }

   private void scaleStepRegularizationWeightWithTime()
   {
      if (scaleStepRegularizationWeightWithTime.getBooleanValue())
      {
         double alpha = Math.max(timeRemainingInState.getDoubleValue(), minimumTimeRemaining.getDoubleValue()) / swingDurations.get(0).getDoubleValue();
         scaledFootstepRegularizationWeight.set(footstepRegularizationWeight.getDoubleValue() / alpha);
      }
      else
      {
         scaledFootstepRegularizationWeight.set(footstepRegularizationWeight.getDoubleValue());
      }
   }

   private final FrameVector2d feedbackWeights = new FrameVector2d();
   private void scaleFeedbackWeightWithGain()
   {
      ReferenceFrame soleFrame = contactableFeet.get(supportSide.getEnumValue()).getSoleFrame();

      ICPOptimizationControllerHelper.transformWeightsToWorldFrame(feedbackWeights, feedbackForwardWeight, feedbackLateralWeight, soleFrame);

      scaledFeedbackWeight.set(feedbackWeights);

      if (scaleFeedbackWeightWithGain.getBooleanValue())
      {
         ICPOptimizationControllerHelper.transformFeedbackGains(feedbackGains, desiredICPVelocity, feedbackParallelGain, feedbackOrthogonalGain);

         double alpha = Math.sqrt(Math.pow(feedbackGains.getX(), 2) + Math.pow(feedbackGains.getY(), 2));
         scaledFeedbackWeight.scale(1.0 / alpha);
      }
   }

   private void computeUpcomingDoubleSupportSplitFraction(int numberOfFootstepsToConsider, double omega0)
   {
      double footstepAdjustmentSize = solutionHandler.getFootstepAdjustment().length();

      double minimumDoubleSupportSplitFraction = minimumTimeOnInitialCMPForBigAdjustment / transferDurations.get(0).getDoubleValue();
      minimumDoubleSupportSplitFraction = Math.max(minimumDoubleSupportSplitFraction, transferSplitFractionUnderDisturbance.getDoubleValue());

      if (footstepAdjustmentSize > magnitudeForBigAdjustment.getDoubleValue() && !doingBigAdjustment.getBooleanValue())
      {
         doingBigAdjustment.set(true);
         transferSplitFractions.get(1).set(minimumDoubleSupportSplitFraction);
         stateMultiplierCalculator.computeRecursionMultipliers(numberOfFootstepsToConsider, upcomingFootsteps.size(), isInTransfer.getBooleanValue(), useTwoCMPs, omega0);
      }
   }

   @Override
   public int getNumberOfFootstepsToConsider()
   {
      return numberOfFootstepsToConsider.getIntegerValue();
   }

   @Override
   public void getDesiredCMP(FramePoint2d desiredCMPToPack)
   {
      controllerFeedbackCMP.getFrameTuple2d(desiredCMPToPack);
   }

   @Override
   public void getFootstepSolution(int footstepIndex, FramePoint2d footstepSolutionToPack)
   {
      footstepSolutions.get(footstepIndex).getFrameTuple2d(footstepSolutionToPack);
   }

   @Override
   public boolean wasFootstepAdjusted()
   {
      return solutionHandler.wasFootstepAdjusted();
   }

   @Override
   public boolean useAngularMomentum()
   {
      return useAngularMomentum.getBooleanValue();
   }
}
