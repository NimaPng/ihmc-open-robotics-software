package us.ihmc.humanoidBehaviors.lookAndStep.parts;

import controller_msgs.msg.dds.PlanarRegionsListMessage;
import us.ihmc.communication.packets.PlanarRegionMessageConverter;
import us.ihmc.communication.util.Timer;
import us.ihmc.communication.util.TimerSnapshotWithExpiration;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.geometry.interfaces.Pose3DReadOnly;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.footstepPlanning.graphSearch.VisibilityGraphPathPlanner;
import us.ihmc.humanoidBehaviors.lookAndStep.*;
import us.ihmc.humanoidBehaviors.tools.RemoteSyncedRobotModel;
import us.ihmc.humanoidBehaviors.tools.interfaces.StatusLogger;
import us.ihmc.humanoidBehaviors.tools.interfaces.UIPublisher;
import us.ihmc.humanoidBehaviors.tools.walking.WalkingFootstepTracker;
import us.ihmc.log.LogTools;
import us.ihmc.pathPlanning.visibilityGraphs.parameters.VisibilityGraphsParametersReadOnly;
import us.ihmc.pathPlanning.visibilityGraphs.postProcessing.BodyPathPostProcessor;
import us.ihmc.pathPlanning.visibilityGraphs.postProcessing.ObstacleAvoidanceProcessor;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.yoVariables.registry.YoVariableRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static us.ihmc.humanoidBehaviors.lookAndStep.LookAndStepBehaviorAPI.BodyPathPlanForUI;
import static us.ihmc.humanoidBehaviors.lookAndStep.LookAndStepBehaviorAPI.MapRegionsForUI;

public class LookAndStepBodyPathPlanningTask
{
   protected StatusLogger statusLogger;
   protected UIPublisher uiPublisher;
   protected VisibilityGraphsParametersReadOnly visibilityGraphParameters;
   protected LookAndStepBehaviorParametersReadOnly lookAndStepBehaviorParameters;
   protected Supplier<Boolean> operatorReviewEnabled;
   protected BehaviorStateReference<LookAndStepBehavior.State> behaviorStateReference;
   protected Supplier<Boolean> robotConnectedSupplier;

   protected Consumer<ArrayList<Pose3D>> autonomousOutput;
   protected Consumer<List<? extends Pose3DReadOnly>> initiateReviewOutput;
   protected Supplier<Boolean> isBeingReviewed;

   protected final Timer planningFailedTimer = new Timer();

   public static class LookAndStepBodyPathPlanning extends LookAndStepBodyPathPlanningTask
   {
      private final Supplier<RemoteSyncedRobotModel> robotStateSupplier;
      private final WalkingFootstepTracker walkingFootstepTracker;

      private final TypedInput<PlanarRegionsList> mapRegionsInput = new TypedInput<>();
      private final TypedInput<Pose3D> goalInput = new TypedInput<>();
      private final Timer mapRegionsExpirationTimer = new Timer();

      public LookAndStepBodyPathPlanning(StatusLogger statusLogger,
                                         UIPublisher uiPublisher,
                                         VisibilityGraphsParametersReadOnly visibilityGraphParameters,
                                         LookAndStepBehaviorParametersReadOnly lookAndStepBehaviorParameters,
                                         Supplier<Boolean> operatorReviewEnabled,
                                         RemoteSyncedRobotModel syncedRobot,
                                         BehaviorStateReference<LookAndStepBehavior.State> behaviorStateReference,
                                         Supplier<Boolean> robotConnectedSupplier,
                                         WalkingFootstepTracker walkingFootstepTracker)
      {

         this.statusLogger = statusLogger;
         this.uiPublisher = uiPublisher;
         this.visibilityGraphParameters = visibilityGraphParameters;
         this.lookAndStepBehaviorParameters = lookAndStepBehaviorParameters;
         this.operatorReviewEnabled = operatorReviewEnabled;
         this.behaviorStateReference = behaviorStateReference;
         this.robotConnectedSupplier = robotConnectedSupplier;

         this.robotStateSupplier = () ->
         {
            syncedRobot.update();
            return syncedRobot;
         };
         this.walkingFootstepTracker = walkingFootstepTracker;

         // don't run two body path plans at the same time
         SingleThreadSizeOneQueueExecutor executor = new SingleThreadSizeOneQueueExecutor(getClass().getSimpleName());

         mapRegionsInput.addCallback(data -> executor.execute(this::evaluateAndRun));
         goalInput.addCallback(data -> executor.execute(this::evaluateAndRun));
      }

      public void laterSetup(Supplier<Boolean> isBeingReviewed,
                             Consumer<ArrayList<Pose3D>> autonomousOutput,
                             Consumer<List<? extends Pose3DReadOnly>> reviewInitiation)
      {
         this.isBeingReviewed = isBeingReviewed;
         this.autonomousOutput = autonomousOutput;
         this.initiateReviewOutput = reviewInitiation;
      }

      public void acceptMapRegions(PlanarRegionsListMessage planarRegionsListMessage)
      {
         mapRegionsInput.set(PlanarRegionMessageConverter.convertToPlanarRegionsList(planarRegionsListMessage));
         mapRegionsExpirationTimer.reset();
      }

      public void acceptGoal(Pose3D goal)
      {
         goalInput.set(goal);
         LogTools.info("Body path goal received: {}", goal);
      }

      private void evaluateAndRun()
      {
         mapRegions = mapRegionsInput.get();
         goal = goalInput.get();
         syncedRobot = robotStateSupplier.get();
         mapRegionsReceptionTimerSnapshot = mapRegionsExpirationTimer.createSnapshot(lookAndStepBehaviorParameters.getPlanarRegionsExpiration());
         planningFailureTimerSnapshot = planningFailedTimer.createSnapshot(lookAndStepBehaviorParameters.getWaitTimeAfterPlanFailed());
         behaviorState = behaviorStateReference.get();
         numberOfIncompleteFootsteps = walkingFootstepTracker.getNumberOfIncompleteFootsteps();

         if (evaluateEntry())
         {
            performTask();
         }
      }
   }

   protected PlanarRegionsList mapRegions;
   protected Pose3D goal;
   protected RemoteSyncedRobotModel syncedRobot;
   protected TimerSnapshotWithExpiration mapRegionsReceptionTimerSnapshot;
   protected TimerSnapshotWithExpiration planningFailureTimerSnapshot;
   protected LookAndStepBehavior.State behaviorState;
   protected int numberOfIncompleteFootsteps;

   protected boolean evaluateEntry()
   {
      boolean proceed = true;

      //      if (!needNewPlan.get().get())
      //      {
      //         LogTools.warn("Body path planning supressed: New plan not needed");
      //         proceed = false;
      //      }
      if (!behaviorState.equals(LookAndStepBehavior.State.BODY_PATH_PLANNING))
      {
         statusLogger.debug("Body path planning suppressed: Not in body path planning state");
         proceed = false;
      }
      else if (!hasGoal())
      {
         statusLogger.debug("Body path planning suppressed: No goal specified");
         uiPublisher.publishToUI(MapRegionsForUI, mapRegions);
         proceed = false;
      }
      else if (!regionsOK())
      {
         statusLogger.debug("Body path planning suppressed: Regions not OK: {}, timePassed: {}, isEmpty: {}",
                            mapRegions,
                            mapRegionsReceptionTimerSnapshot.getTimePassedSinceReset(),
                            mapRegions == null ? null : mapRegions.isEmpty());
         proceed = false;
      }
      else if (planningFailureTimerSnapshot.isRunning()) // TODO: This could be "run recently" instead of failed recently
      {
         statusLogger.debug("Body path planning suppressed: Failed recently");
         proceed = false;
      }
      else if (isBeingReviewed.get())
      {
         statusLogger.debug("Body path planning suppressed: Is being reviewed");
         proceed = false;
      }
      else if (!robotConnectedSupplier.get())
      {
         statusLogger.debug("Body path planning suppressed: Robot disconnected");
         proceed = false;
      }
      else if (numberOfIncompleteFootsteps > lookAndStepBehaviorParameters.getAcceptableIncompleteFootsteps())
      {
         statusLogger.debug("Body path planning suppressed: numberOfIncompleteFootsteps {} > {}",
                            numberOfIncompleteFootsteps,
                            lookAndStepBehaviorParameters.getAcceptableIncompleteFootsteps());
         proceed = false;
      }

      return proceed;
   }

   private boolean hasGoal()
   {
      return goal != null && !goal.containsNaN();
   }

   private boolean regionsOK()
   {
      return mapRegions != null && !mapRegions.isEmpty() && mapRegionsReceptionTimerSnapshot.isRunning();
   }

   protected void performTask()
   {
      statusLogger.info("Body path planning...");
      // TODO: Add robot standing still for 20s for real robot?
      uiPublisher.publishToUI(MapRegionsForUI, mapRegions);

      // calculate and send body path plan
      BodyPathPostProcessor pathPostProcessor = new ObstacleAvoidanceProcessor(visibilityGraphParameters);
      YoVariableRegistry parentRegistry = new YoVariableRegistry(getClass().getSimpleName());
      VisibilityGraphPathPlanner bodyPathPlanner = new VisibilityGraphPathPlanner(visibilityGraphParameters, pathPostProcessor, parentRegistry);

      bodyPathPlanner.setGoal(goal);
      bodyPathPlanner.setPlanarRegionsList(mapRegions);
      FramePose3D leftFootPoseTemp = new FramePose3D();
      leftFootPoseTemp.setToZero(syncedRobot.getReferenceFrames().getSoleFrame(RobotSide.LEFT));
      FramePose3D rightFootPoseTemp = new FramePose3D();
      rightFootPoseTemp.setToZero(syncedRobot.getReferenceFrames().getSoleFrame(RobotSide.RIGHT));
      leftFootPoseTemp.changeFrame(ReferenceFrame.getWorldFrame());
      rightFootPoseTemp.changeFrame(ReferenceFrame.getWorldFrame());
      bodyPathPlanner.setStanceFootPoses(leftFootPoseTemp, rightFootPoseTemp);
      final ArrayList<Pose3D> bodyPathPlanForReview = new ArrayList<>(); // TODO Review making this final
      bodyPathPlanner.planWaypoints(); // takes about 0.1s
      statusLogger.info("Body path plan completed with {} waypoint(s)", bodyPathPlanner.getWaypoints().size());
      //      bodyPathPlan = bodyPathPlanner.getWaypoints();
      if (bodyPathPlanner.getWaypoints() != null)
      {
         for (Pose3DReadOnly poseWaypoint : bodyPathPlanner.getWaypoints())
         {
            bodyPathPlanForReview.add(new Pose3D(poseWaypoint));
         }
         uiPublisher.publishToUI(BodyPathPlanForUI, bodyPathPlanForReview);
      }

      if (bodyPathPlanForReview.size() >= 2)
      {
         if (operatorReviewEnabled.get())
         {
            initiateReviewOutput.accept(bodyPathPlanForReview);
         }
         else
         {
            behaviorStateReference.set(LookAndStepBehavior.State.FOOTSTEP_PLANNING);
            autonomousOutput.accept(bodyPathPlanForReview);
         }
      }
      else
      {
         planningFailedTimer.reset();
      }
   }
}