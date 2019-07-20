package us.ihmc.commonWalkingControlModules.capturePoint.numerical;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.apache.commons.math3.util.Precision;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.BipedSupportPolygons;
import us.ihmc.commons.lists.RecyclingArrayList;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.geometry.interfaces.ConvexPolygon2DBasics;
import us.ihmc.euclid.geometry.interfaces.ConvexPolygon2DReadOnly;
import us.ihmc.euclid.geometry.tools.EuclidGeometryPolygonTools;
import us.ihmc.euclid.referenceFrame.FrameConvexPolygon2D;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.FrameQuaternion;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFramePose3DBasics;
import us.ihmc.euclid.referenceFrame.interfaces.FrameConvexPolygon2DReadOnly;
import us.ihmc.euclid.referenceFrame.interfaces.FramePose3DReadOnly;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.interfaces.Point2DReadOnly;
import us.ihmc.euclid.tuple2D.interfaces.Tuple2DReadOnly;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.YoArtifactPolygon;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.humanoidRobotics.footstep.FootstepTiming;
import us.ihmc.robotics.EuclidCoreMissingTools;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.robotics.trajectories.TrajectoryType;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoFrameConvexPolygon2D;

public class SupportSeqence
{
   private static final int INITIAL_CAPACITY = 50;
   private static final double UNSET_TIME = -1.0;

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final YoDouble transferPhaseEndTime = new YoDouble("TransferPhaseEndTime", registry);
   private final YoDouble swingPhaseEndTime = new YoDouble("SwingPhaseEndTime", registry);

   private final SideDependentList<? extends ReferenceFrame> soleFrames;

   private final RecyclingArrayList<ConvexPolygon2D> supportPolygons = new RecyclingArrayList<>(INITIAL_CAPACITY, ConvexPolygon2D.class);
   private final TDoubleArrayList supportInitialTimes = new TDoubleArrayList(INITIAL_CAPACITY, UNSET_TIME);

   private final ConvexPolygon2D defaultSupportPolygon = new ConvexPolygon2D();
   private final SideDependentList<ConvexPolygon2D> footPolygonsInSole = new SideDependentList<>(new ConvexPolygon2D(), new ConvexPolygon2D());
   private final SideDependentList<FramePose3D> footPoses = new SideDependentList<>(new FramePose3D(), new FramePose3D());
   private final BipedSupportPolygons bipedSupportPolygons;

   private final SideDependentList<ConvexPolygon2D> movingPolygonsInSole = new SideDependentList<>(new ConvexPolygon2D(), new ConvexPolygon2D());

   private final SideDependentList<RecyclingArrayList<FrameConvexPolygon2D>> footSupportSequences = new SideDependentList<>();
   private final SideDependentList<TDoubleArrayList> footSupportInitialTimes = new SideDependentList<>();

   private final List<YoDouble> polygonStartTimes = new ArrayList<>();
   private final List<ConvexPolygon2DBasics> vizPolygons = new ArrayList<>();

   /**
    * Each step gets one of these frames. It is located at the sole frame of the foot when it is in full support.
    */
   private final SideDependentList<RecyclingArrayList<PoseReferenceFrame>> stepFrames = new SideDependentList<>();

   public SupportSeqence(ConvexPolygon2DReadOnly defaultSupportPolygon, SideDependentList<? extends ReferenceFrame> soleFrames)
   {
      this(defaultSupportPolygon, soleFrames, null, null, null);
   }

   public SupportSeqence(ConvexPolygon2DReadOnly defaultSupportPolygon, SideDependentList<? extends ReferenceFrame> soleFrames,
                         BipedSupportPolygons bipedSupportPolygons, YoVariableRegistry parentRegistry, YoGraphicsListRegistry graphicRegistry)
   {
      if (graphicRegistry != null)
      {
         for (int i = 0; i < 10; i++)
         {
            YoFrameConvexPolygon2D yoPolygon = new YoFrameConvexPolygon2D("SupportPolygon" + i, ReferenceFrame.getWorldFrame(), 10, registry);
            graphicRegistry.registerArtifact(getClass().getSimpleName(), new YoArtifactPolygon("SupportPolygon" + i, yoPolygon, Color.GRAY, false));
            vizPolygons.add(yoPolygon);
            polygonStartTimes.add(new YoDouble("SupportPolygonStartTime" + i, registry));
         }
      }

      if (parentRegistry != null)
         parentRegistry.addChild(registry);

      for (RobotSide robotSide : RobotSide.values)
      {
         footSupportSequences.put(robotSide, new RecyclingArrayList<>(INITIAL_CAPACITY, FrameConvexPolygon2D.class));
         footSupportInitialTimes.put(robotSide, new TDoubleArrayList(INITIAL_CAPACITY, UNSET_TIME));

         stepFrames.put(robotSide, new RecyclingArrayList<PoseReferenceFrame>(3, new Supplier<PoseReferenceFrame>()
         {
            private int frameIndexCounter = 0;

            @Override
            public PoseReferenceFrame get()
            {
               return new PoseReferenceFrame(robotSide.getLowerCaseName() + "StepFrame" + frameIndexCounter++, ReferenceFrame.getWorldFrame());
            }
         }));
      }

      this.defaultSupportPolygon.set(defaultSupportPolygon);
      this.bipedSupportPolygons = bipedSupportPolygons;
      this.soleFrames = soleFrames;
   }

   /**
    * Get the list of upcoming support polygons with the first polygon in the list being the current support.
    *
    * @return the list of upcoming support polygons.
    */
   public List<? extends ConvexPolygon2DReadOnly> getSupportPolygons()
   {
      return supportPolygons;
   }

   /**
    * Get the times at which the respective support polygons obtained with {@link #getSupportPolygons()} will be active.
    * The first value in this list should always be 0.0. The time is relative to the start time of the support sequence
    * (see {@link #getTimeInSequence()}).
    *
    * @return the times at which the support polygon will change.
    */
   public TDoubleList getSupportTimes()
   {
      return supportInitialTimes;
   }

   /**
    * Indicates whether the first transfer in this sequence has finished. The first support phase can have several
    * support polygons. This happens if the robot uses toe off or heel strike as this changes the support polygon but
    * does not end the support phase (swing or transfer).
    * <p>
    * Note, that if the robot is standing this will always return {@code true}.
    *
    * @return whether the first transfer phase at the start of this sequence should be over based on time only.
    */
   public boolean isDoubleSupportPhaseOver(double timeInSequence)
   {
      if (transferPhaseEndTime.getValue() == UNSET_TIME)
         return true;
      return timeInSequence >= transferPhaseEndTime.getValue();
   }

   /**
    * Indicates whether the first single support in this sequence has finished.
    * <p>
    * Note, that if the robot is standing this will always return {@code true}.
    *
    * @return whether the first single support phase at the start of this sequence should be over based on time only.
    */
   public boolean isSingleSupportPhaseOver(double timeInSequence)
   {
      if (swingPhaseEndTime.getValue() == UNSET_TIME)
         return true;
      return timeInSequence >= swingPhaseEndTime.getValue();
   }

   /**
    * Obtains the time that is remaining until the first single support phase in this sequence should end according to
    * plan (see {@link #isSingleSupportPhaseOver()}).
    * <p>
    * Note, that if the robot is standing this will always return {@code 0.0}.
    *
    * @return the time that (according to plan) remains until first foot touchdown.
    */
   public double getTimeUntilTouchdown(double timeInSequence)
   {
      if (supportInitialTimes.size() == 1)
         return 0.0;
      return swingPhaseEndTime.getValue() - timeInSequence;
   }

   /**
    * Starts a support sequence that will not contain footsteps. This initializes the timing.
    */
   public void startSequence()
   {
      transferPhaseEndTime.set(UNSET_TIME);
      swingPhaseEndTime.set(UNSET_TIME);
      reset();
   }

   /**
    * Starts a support sequence that will contain footsteps. This initializes the timing.
    *
    * @param initialTiming the timing of the first footstep to do checks on when it should be completed.
    */
   public void startSequence(FootstepTiming initialTiming)
   {
      transferPhaseEndTime.set(initialTiming.getTransferTime());
      swingPhaseEndTime.set(initialTiming.getStepTime());
      reset();
   }

   /**
    * Starts a support sequence that will only perform a transfer without steps. This could be a final transfer for
    * example.
    *
    * @param initialTiming the timing of the first footstep to do checks on when it should be completed.
    */
   public void startSequence(double transferTime)
   {
      transferPhaseEndTime.set(transferTime);
      swingPhaseEndTime.set(UNSET_TIME);
      reset();
   }

   /**
    * Updates the current foot poses and the footholds of the robot to match the actual sole poses and footholds.
    */
   public void initializeStance()
   {
      for (RobotSide robotSide : RobotSide.values)
         initializeStance(robotSide);
   }

   /**
    * For the given side updates the current foot pose and the foothold of the robot to match the actual sole pose and
    * foothold.
    *
    * @param robotSide to be updated.
    */
   public void initializeStance(RobotSide robotSide)
   {
      if (bipedSupportPolygons == null)
         footPolygonsInSole.get(robotSide).set(defaultSupportPolygon);
      else
         footPolygonsInSole.get(robotSide).set(bipedSupportPolygons.getFootPolygonInSoleFrame(robotSide));
      footPoses.get(robotSide).setFromReferenceFrame(soleFrames.get(robotSide));
   }

   /**
    * Updates the support sequence. This can be called every tick to prevent the support state from becoming invalid in
    * case the robot would drift.
    */
   public void update()
   {
      update(Collections.emptyList(), Collections.emptyList(), 0.0);
   }

   /**
    * Updates the support sequence with the given footstep parameters. This can be called every tick. This method will
    * retain all contact state since the sequence was started and update all contact switches in the future.
    *
    * @param footsteps to be added to the sequence.
    * @param footstepTimings respective timings.
    */
   public void update(List<Footstep> footsteps, List<FootstepTiming> footstepTimings, double timeInSequence)
   {
      update(footsteps, footstepTimings, null, null, timeInSequence);
   }

   /**
    * Updates the support sequence with the given footstep parameters. This can be called every tick. This method will
    * retain all contact state since the sequence was started and update all contact switches in the future.
    *
    * @param footsteps to be added to the sequence.
    * @param footstepTimings respective timings.
    * @param lastFootstep the last executed footstep in case it contained a touchdown.
    * @param lastFootstepTiming respective timing.
    */
   public void update(List<Footstep> footsteps, List<FootstepTiming> footstepTimings, Footstep lastFootstep, FootstepTiming lastFootstepTiming,
                      double timeInSequence)
   {
      if (!footsteps.isEmpty() && transferPhaseEndTime.getValue() == UNSET_TIME)
         throw new RuntimeException("If updating with footsteps the sequence must be started with step timings.");

      initializeStance();
      resetFuture(timeInSequence);

      // Add initial support states of the feet and set the moving polygons
      for (RobotSide robotSide : RobotSide.values)
      {
         // Record the initial step frames. In case there is a step touching down this frame will be updated.
         PoseReferenceFrame stepFrame = stepFrames.get(robotSide).add();
         stepFrame.setPoseAndUpdate(soleFrames.get(robotSide).getTransformToRoot());

         movingPolygonsInSole.get(robotSide).set(footPolygonsInSole.get(robotSide));
         FrameConvexPolygon2D initialFootSupport = footSupportSequences.get(robotSide).add();
         initialFootSupport.setIncludingFrame(stepFrame, movingPolygonsInSole.get(robotSide));
         footSupportInitialTimes.get(robotSide).add(0.0);
      }

      // In case there is a last footstep we might still be finishing up its touchdown.
      if (lastFootstep != null && checkForTouchdown(lastFootstep, lastFootstepTiming))
      {
         RobotSide stepSide = lastFootstep.getRobotSide();
         PoseReferenceFrame stepFrame = last(stepFrames.get(stepSide));

         computeAdjustedSole(footPoses.get(stepSide), lastFootstep.getFootstepPose(), movingPolygonsInSole.get(stepSide).getCentroid());
         stepFrame.setPoseAndUpdate(footPoses.get(stepSide));
         extractSupportPolygon(lastFootstep, movingPolygonsInSole.get(stepSide), defaultSupportPolygon);

         FrameConvexPolygon2D fullSupport = footSupportSequences.get(stepSide).add();
         fullSupport.setIncludingFrame(stepFrame, movingPolygonsInSole.get(stepSide));
         footSupportInitialTimes.get(stepSide).add(lastFootstepTiming.getTouchdownDuration());
      }

      // Assemble the individual foot support trajectories for regular walking
      double stepStartTime = 0.0;
      for (int stepIndex = 0; stepIndex < footsteps.size(); stepIndex++)
      {
         FootstepTiming footstepTiming = footstepTimings.get(stepIndex);
         Footstep footstep = footsteps.get(stepIndex);
         RobotSide stepSide = footstep.getRobotSide();

         // Add swing - no support for foot
         addLiftOffPolygon(footstep, footstepTiming, stepStartTime);
         footSupportSequences.get(stepSide).add().clearAndUpdate();
         footSupportInitialTimes.get(stepSide).add(stepStartTime + footstepTiming.getTransferTime());

         // Update the moving polygon and sole frame to reflect that the step was taken.
         extractSupportPolygon(footstep, movingPolygonsInSole.get(stepSide), defaultSupportPolygon);

         // Add touchdown polygon
         addTouchdownPolygon(footstep, footstepTiming, stepStartTime);

         stepStartTime += footstepTiming.getStepTime();
      }

      // Clean up the list of foot supports: remove any contact states that where re-added but where in the past and therefore not cleared.
      for (RobotSide robotSide : RobotSide.values)
      {
         RecyclingArrayList<FrameConvexPolygon2D> footSupportSequence = footSupportSequences.get(robotSide);
         TDoubleArrayList footSupportTimes = footSupportInitialTimes.get(robotSide);
         for (int index = 1; index < footSupportTimes.size(); index++)
         {
            if (footSupportTimes.get(index - 1) >= footSupportTimes.get(index))
            {
               footSupportSequence.remove(index);
               footSupportTimes.removeAt(index);
               index--;
            }
         }
      }

      // Convert the foot support trajectories to a full support trajectory
      int lIndex = 0;
      int rIndex = 0;
      FrameConvexPolygon2D lPolygon = footSupportSequences.get(RobotSide.LEFT).get(lIndex);
      FrameConvexPolygon2D rPolygon = footSupportSequences.get(RobotSide.RIGHT).get(rIndex);
      combinePolygons(supportPolygons.add(), lPolygon, rPolygon);
      supportInitialTimes.add(0.0);

      while (true)
      {
         double lNextTime;
         double rNextTime;
         if (footSupportInitialTimes.get(RobotSide.LEFT).size() == lIndex + 1)
            lNextTime = Double.POSITIVE_INFINITY;
         else
            lNextTime = footSupportInitialTimes.get(RobotSide.LEFT).get(lIndex + 1);
         if (footSupportInitialTimes.get(RobotSide.RIGHT).size() == rIndex + 1)
            rNextTime = Double.POSITIVE_INFINITY;
         else
            rNextTime = footSupportInitialTimes.get(RobotSide.RIGHT).get(rIndex + 1);

         if (Double.isInfinite(rNextTime) && Double.isInfinite(lNextTime))
            break;

         if (Precision.equals(lNextTime, rNextTime))
         {
            rIndex++;
            lIndex++;
            rPolygon = footSupportSequences.get(RobotSide.RIGHT).get(rIndex);
            lPolygon = footSupportSequences.get(RobotSide.LEFT).get(lIndex);
            supportInitialTimes.add(footSupportInitialTimes.get(RobotSide.LEFT).get(lIndex));
         }
         else if (lNextTime > rNextTime)
         {
            rIndex++;
            rPolygon = footSupportSequences.get(RobotSide.RIGHT).get(rIndex);
            supportInitialTimes.add(footSupportInitialTimes.get(RobotSide.RIGHT).get(rIndex));
         }
         else
         {
            lIndex++;
            lPolygon = footSupportSequences.get(RobotSide.LEFT).get(lIndex);
            supportInitialTimes.add(footSupportInitialTimes.get(RobotSide.LEFT).get(lIndex));
         }

         combinePolygons(supportPolygons.add(), lPolygon, rPolygon);
      }

      updateViz();
   }

   private void addTouchdownPolygon(Footstep footstep, FootstepTiming footstepTiming, double stepStartTime)
   {
      RobotSide stepSide = footstep.getRobotSide();
      TDoubleArrayList footInitialTimes = footSupportInitialTimes.get(stepSide);
      RecyclingArrayList<FrameConvexPolygon2D> footSupports = footSupportSequences.get(stepSide);

      // Record the step frame as the sole frame when the foot is in full contact.
      PoseReferenceFrame stepFrame = stepFrames.get(stepSide).add();
      stepFrame.setPoseAndUpdate(footstep.getFootstepPose());

      // Check if the footstep contains a partial foothold touchdown
      boolean doPartialFootholdTouchdown = checkForTouchdown(footstep, footstepTiming);

      // Compute the touchdown polygon in step sole frame
      FrameConvexPolygon2D touchdownPolygon = footSupports.add();
      if (doPartialFootholdTouchdown)
      {
         if (computeTouchdownPitch(footstep) > 0.0)
            computeToePolygon(touchdownPolygon, movingPolygonsInSole.get(stepSide), stepFrame);
         else
            computeHeelPolygon(touchdownPolygon, movingPolygonsInSole.get(stepSide), stepFrame);
      }
      else
      {
         touchdownPolygon.setIncludingFrame(stepFrame, movingPolygonsInSole.get(stepSide));
      }
      footInitialTimes.add(stepStartTime + footstepTiming.getStepTime());

      // In case there was a partial touchdown polygon we need to add the full support after the touchdown is finished.
      if (doPartialFootholdTouchdown)
      {
         FrameConvexPolygon2D fullSupportPolygon = footSupports.add();
         fullSupportPolygon.setIncludingFrame(stepFrame, movingPolygonsInSole.get(stepSide));
         footInitialTimes.add(stepStartTime + footstepTiming.getStepTime() + footstepTiming.getTouchdownDuration());
      }
   }

   private void addLiftOffPolygon(Footstep footstep, FootstepTiming footstepTiming, double stepStartTime)
   {
      boolean doPartialFootholdLiftoff = checkForLiftoff(footstep, footstepTiming);

      RobotSide stepSide = footstep.getRobotSide();
      PoseReferenceFrame stepFrame = last(stepFrames.get(stepSide));

      if (doPartialFootholdLiftoff)
      {
         FrameConvexPolygon2D liftoffPolygon = footSupportSequences.get(stepSide).add();
         if (computeLiftoffPitch(footstep) > 0.0)
            computeToePolygon(liftoffPolygon, movingPolygonsInSole.get(stepSide), stepFrame);
         else
            computeHeelPolygon(liftoffPolygon, movingPolygonsInSole.get(stepSide), stepFrame);
         footSupportInitialTimes.get(stepSide).add(stepStartTime + footstepTiming.getTransferTime() - footstepTiming.getLiftoffDuration());
      }
      else if (shouldDoToeOff(last(stepFrames.get(stepSide.getOppositeSide())), stepFrame))
      {
         double toeOffTime = footstepTiming.getTransferTime() / 2.0;
         FrameConvexPolygon2D liftoffPolygon = footSupportSequences.get(stepSide).add();
         computeToePolygon(liftoffPolygon, movingPolygonsInSole.get(stepSide), stepFrame);
         footSupportInitialTimes.get(stepSide).add(stepStartTime + footstepTiming.getTransferTime() - toeOffTime);
      }
   }

   private boolean checkForTouchdown(Footstep footstep, FootstepTiming footstepTiming)
   {
      if (footstep.getTrajectoryType() != TrajectoryType.WAYPOINTS)
         return false;
      if (footstepTiming.getTouchdownDuration() <= 0.0)
         return false;
      if (!Precision.equals(last(footstep.getSwingTrajectory()).getTime(), footstepTiming.getSwingTime()))
         return false;
      if (Math.abs(computeTouchdownPitch(footstep)) < Math.toRadians(5.0))
         return false;
      return true;
   }

   private boolean checkForLiftoff(Footstep footstep, FootstepTiming footstepTiming)
   {
      if (footstep.getTrajectoryType() != TrajectoryType.WAYPOINTS)
         return false;
      if (footstepTiming.getLiftoffDuration() <= 0.0)
         return false;
      if (!Precision.equals(footstep.getSwingTrajectory().get(0).getTime(), 0.0))
         return false;
      if (Math.abs(computeLiftoffPitch(footstep)) < Math.toRadians(5.0))
         return false;
      return true;
   }

   private final FrameQuaternion tempOrientation = new FrameQuaternion();

   private double computeTouchdownPitch(Footstep footstep)
   {
      tempOrientation.setIncludingFrame(last(footstep.getSwingTrajectory()).getOrientation());
      tempOrientation.changeFrame(footstep.getSoleReferenceFrame());
      return tempOrientation.getPitch();
   }

   private double computeLiftoffPitch(Footstep footstep)
   {
      tempOrientation.setIncludingFrame(footstep.getSwingTrajectory().get(0).getOrientation());
      tempOrientation.changeFrame(last(stepFrames.get(footstep.getRobotSide())));
      return tempOrientation.getPitch();
   }

   private final FrameConvexPolygon2D framePolygonA = new FrameConvexPolygon2D();
   private final FrameConvexPolygon2D framePolygonB = new FrameConvexPolygon2D();
   private final List<Point2DReadOnly> vertices = new ArrayList<>();

   private void combinePolygons(ConvexPolygon2DBasics result, FrameConvexPolygon2DReadOnly polygonA, FrameConvexPolygon2DReadOnly polygonB)
   {
      vertices.clear();

      framePolygonA.setIncludingFrame(polygonA);
      framePolygonA.changeFrameAndProjectToXYPlane(ReferenceFrame.getWorldFrame());
      for (int i = 0; i < framePolygonA.getNumberOfVertices(); i++)
         vertices.add(framePolygonA.getVertex(i));

      framePolygonB.setIncludingFrame(polygonB);
      framePolygonB.changeFrameAndProjectToXYPlane(ReferenceFrame.getWorldFrame());
      for (int i = 0; i < framePolygonB.getNumberOfVertices(); i++)
         vertices.add(framePolygonB.getVertex(i));

      int n = EuclidGeometryPolygonTools.inPlaceGrahamScanConvexHull2D(vertices);
      result.clear();
      for (int i = 0; i < n; i++)
         result.addVertex(vertices.get(i));
      result.update();
   }

   private void computeToePolygon(FrameConvexPolygon2D toePolygon, ConvexPolygon2DReadOnly fullPolygonInSole, ReferenceFrame soleFrame)
   {
      double maxX = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < fullPolygonInSole.getNumberOfVertices(); i++)
      {
         double x = fullPolygonInSole.getVertex(i).getX();
         maxX = Math.max(maxX, x);
      }

      toePolygon.clear(soleFrame);
      for (int i = 0; i < fullPolygonInSole.getNumberOfVertices(); i++)
      {
         Point2DReadOnly vertex = fullPolygonInSole.getVertex(i);
         if (Precision.equals(vertex.getX(), maxX, 0.01))
            toePolygon.addVertex(vertex);
      }
      toePolygon.update();
   }

   private void computeHeelPolygon(FrameConvexPolygon2D heelPolygon, ConvexPolygon2DReadOnly fullPolygonInSole, ReferenceFrame soleFrame)
   {
      double minX = Double.POSITIVE_INFINITY;
      for (int i = 0; i < fullPolygonInSole.getNumberOfVertices(); i++)
      {
         double x = fullPolygonInSole.getVertex(i).getX();
         minX = Math.min(minX, x);
      }

      heelPolygon.clear(soleFrame);
      for (int i = 0; i < fullPolygonInSole.getNumberOfVertices(); i++)
      {
         Point2DReadOnly vertex = fullPolygonInSole.getVertex(i);
         if (Precision.equals(vertex.getX(), minX, 0.01))
            heelPolygon.addVertex(vertex);
      }
      heelPolygon.update();
   }

   private final Quaternion orientationError = new Quaternion();
   private final Quaternion rotation = new Quaternion();
   private final Vector3DReadOnly zAxis = new Vector3D(0.0, 0.0, 1.0);

   /**
    * This method projects the sole frame onto the x-y plane of the foothold pose. It preserves the heel location of the
    * sole and attempts to avoid modifying the yaw of the sole in world.
    *
    * @param solePose to be projected (modified)
    * @param footholdPose defines the x-y plane
    * @param soleToHeel heel location in sole frame
    */
   private void computeAdjustedSole(FixedFramePose3DBasics solePose, FramePose3DReadOnly footholdPose, Tuple2DReadOnly soleToHeel)
   {
      solePose.checkReferenceFrameMatch(footholdPose);

      orientationError.set(solePose.getOrientation());
      orientationError.multiplyConjugateThis(footholdPose.getOrientation());
      EuclidCoreMissingTools.projectRotationOnAxis(orientationError, zAxis, rotation);
      rotation.conjugate();

      solePose.appendTranslation(soleToHeel.getX(), soleToHeel.getY(), 0.0);
      solePose.appendRotation(orientationError);
      solePose.appendRotation(rotation);
      solePose.appendTranslation(-soleToHeel.getX(), -soleToHeel.getY(), 0.0);
   }

   private final FramePoint3D stepLocation = new FramePoint3D();

   private boolean shouldDoToeOff(ReferenceFrame stanceFrame, ReferenceFrame swingFootFrame)
   {
      stepLocation.setToZero(swingFootFrame);
      stepLocation.changeFrame(stanceFrame);
      return stepLocation.getX() < -0.05;
   }

   private void reset()
   {
      supportPolygons.clear();
      supportInitialTimes.reset();
      for (RobotSide robotSide : RobotSide.values)
      {
         footSupportSequences.get(robotSide).clear();
         footSupportInitialTimes.get(robotSide).reset();
         stepFrames.get(robotSide).clear();
      }
   }

   private void resetFuture(double timeInSequence)
   {
      supportPolygons.clear();
      supportInitialTimes.reset();

      for (RobotSide robotSide : RobotSide.values)
      {
         RecyclingArrayList<FrameConvexPolygon2D> footSupportSequence = footSupportSequences.get(robotSide);
         TDoubleArrayList footSupportTimes = footSupportInitialTimes.get(robotSide);

         // Only clear the foot support sequence for the future and maintain the sequence that is in the past.
         while (!footSupportTimes.isEmpty() && last(footSupportTimes) > timeInSequence)
         {
            footSupportSequence.remove(footSupportSequence.size() - 1);
            footSupportTimes.removeAt(footSupportTimes.size() - 1);
         }

         // Everything in the past needs to be transformed to a fixed frame.
         ReferenceFrame fixedFrame = soleFrames.get(robotSide);
         for (int i = footSupportSequence.size() - 1; i >= 0; i--)
         {
            // If we encounter an empty polygon the foot was not in contact and moved.
            // Everything before this point should be fixed in world.
            if (footSupportSequence.get(i).isEmpty())
               fixedFrame = ReferenceFrame.getWorldFrame();
            footSupportSequence.get(i).changeFrameAndProjectToXYPlane(fixedFrame);
         }
         stepFrames.get(robotSide).clear();
      }
   }

   private void updateViz()
   {
      int max = Math.min(vizPolygons.size(), supportPolygons.size());
      for (int i = 0; i < max; i++)
      {
         vizPolygons.get(i).set(supportPolygons.get(i));
         polygonStartTimes.get(i).set(supportInitialTimes.get(i));
      }
      for (int i = max; i < vizPolygons.size(); i++)
      {
         vizPolygons.get(i).setToNaN();
         polygonStartTimes.get(i).set(UNSET_TIME);
      }
   }

   private static void extractSupportPolygon(Footstep footstep, ConvexPolygon2D footSupportPolygon, ConvexPolygon2DReadOnly defaultSupportPolygon)
   {
      List<Point2D> predictedContactPoints = footstep.getPredictedContactPoints();
      if (predictedContactPoints != null && !predictedContactPoints.isEmpty())
      {
         footSupportPolygon.clear();
         for (int i = 0; i < predictedContactPoints.size(); i++)
            footSupportPolygon.addVertex(predictedContactPoints.get(i));
         footSupportPolygon.update();
      }
      else
      {
         footSupportPolygon.set(defaultSupportPolygon);
      }
   }

   private static double last(TDoubleList list)
   {
      return list.get(list.size() - 1);
   }

   private static <T> T last(List<T> list)
   {
      return list.get(list.size() - 1);
   }
}