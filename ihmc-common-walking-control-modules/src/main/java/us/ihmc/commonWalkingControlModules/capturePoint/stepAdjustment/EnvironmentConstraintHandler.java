package us.ihmc.commonWalkingControlModules.capturePoint.stepAdjustment;

import us.ihmc.commonWalkingControlModules.capturePoint.ICPControlPlane;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.geometry.tools.EuclidGeometryPolygonTools;
import us.ihmc.euclid.orientation.interfaces.Orientation3DReadOnly;
import us.ihmc.euclid.referenceFrame.*;
import us.ihmc.euclid.referenceFrame.interfaces.FixedFramePose3DBasics;
import us.ihmc.euclid.referenceFrame.interfaces.FrameConvexPolygon2DReadOnly;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.interfaces.Point2DBasics;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.YoArtifactPolygon;
import us.ihmc.humanoidRobotics.bipedSupportPolygons.StepConstraintRegion;
import us.ihmc.robotics.contactable.ContactablePlaneBody;
import us.ihmc.robotics.geometry.ConvexPolygonScaler;
import us.ihmc.robotics.geometry.ConvexPolygonTools;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.yoVariables.parameters.BooleanParameter;
import us.ihmc.yoVariables.parameters.DoubleParameter;
import us.ihmc.yoVariables.providers.BooleanProvider;
import us.ihmc.yoVariables.providers.DoubleProvider;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoBoolean;
import us.ihmc.yoVariables.variable.YoFrameConvexPolygon2D;

import java.awt.*;
import java.util.List;

public class EnvironmentConstraintHandler
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private static final double defaultDesiredDistanceInside = 0.04;
   private static final boolean defaultUsePredictedContactPoints = false;
   private static final double defaultMaxConcaveEstimateRatio = 1.1;

   private final DoubleProvider desiredDistanceInsideConstraint;
   private final BooleanProvider usePredictedContactPoints;
   private final DoubleProvider maxConcaveEstimateRatio;

   private final YoBoolean isEnvironmentConstraintValid;

   private final ConvexPolygonScaler scaler = new ConvexPolygonScaler();

   private final SideDependentList<? extends ContactablePlaneBody> contactableFeet;

   private final YoFrameConvexPolygon2D yoConvexHullConstraint;
   private final YoFrameConvexPolygon2D yoShrunkConvexHullConstraint;
   private final FrameConvexPolygon2D reachabilityRegionInConstraintPlane = new FrameConvexPolygon2D();
   private final FrameConvexPolygon2D shrunkHullConstraint = new FrameConvexPolygon2D();
   private final ConvexPolygonTools convexPolygonTools = new ConvexPolygonTools();

   private StepConstraintRegion stepConstraintRegion = null;

   private final FramePoint3D projectedReachablePoint = new FramePoint3D();

   private final ConvexPolygon2D footstepPolygon = new ConvexPolygon2D();
   private final RigidBodyTransform footOrientationTransform = new RigidBodyTransform();
   private final FramePoint2D stepXY = new FramePoint2D();

   private final ICPControlPlane icpControlPlane;

   public EnvironmentConstraintHandler(ICPControlPlane icpControlPlane,
                                       SideDependentList<? extends ContactablePlaneBody> contactableFeet,
                                       String yoNamePrefix,
                                       YoVariableRegistry registry,
                                       YoGraphicsListRegistry yoGraphicsListRegistry)
   {
      this.icpControlPlane = icpControlPlane;
      this.contactableFeet = contactableFeet;

      desiredDistanceInsideConstraint = new DoubleParameter("desiredDistanceInsideEnvironmentConstraint", registry, defaultDesiredDistanceInside);
      usePredictedContactPoints = new BooleanParameter("usePredictedContactPointsInStep", registry, defaultUsePredictedContactPoints);
      maxConcaveEstimateRatio = new DoubleParameter("maxConcaveEstimateRatio", registry, defaultMaxConcaveEstimateRatio);

      isEnvironmentConstraintValid = new YoBoolean("isEnvironmentConstraintValid", registry);

      yoConvexHullConstraint = new YoFrameConvexPolygon2D(yoNamePrefix + "ConvexHullConstraint", "", worldFrame, 12, registry);
      yoShrunkConvexHullConstraint = new YoFrameConvexPolygon2D(yoNamePrefix + "ShrunkConvexHullConstraint", "", worldFrame, 12, registry);

      if (yoGraphicsListRegistry != null)
      {
         YoArtifactPolygon activePlanarRegionViz = new YoArtifactPolygon("ConvexHullConstraint", yoConvexHullConstraint, Color.RED, false);
         YoArtifactPolygon shrunkActivePlanarRegionViz = new YoArtifactPolygon("ShrunkConvexHullConstraint",
                                                                               yoShrunkConvexHullConstraint,
                                                                               Color.RED,
                                                                               false,
                                                                               true);

         yoGraphicsListRegistry.registerArtifact(getClass().getSimpleName(), activePlanarRegionViz);
         yoGraphicsListRegistry.registerArtifact(getClass().getSimpleName(), shrunkActivePlanarRegionViz);
      }
   }

   public void setStepConstraintRegion(StepConstraintRegion stepConstraintRegion)
   {
      this.stepConstraintRegion = stepConstraintRegion;
   }

   public boolean hasStepConstraintRegion()
   {
      return stepConstraintRegion != null;
   }

   public void reset()
   {
      stepConstraintRegion = null;
      yoConvexHullConstraint.clear();
      yoShrunkConvexHullConstraint.clear();
      isEnvironmentConstraintValid.set(false);
   }

   public void setReachabilityRegion(FrameConvexPolygon2DReadOnly reachabilityRegion)
   {
      if (stepConstraintRegion == null)
         return;

      reachabilityRegionInConstraintPlane.clear();
      for (int i = 0; i < reachabilityRegion.getNumberOfVertices(); i++)
      {
         icpControlPlane.projectPointFromControlPlaneOntoConstraintRegion(worldFrame,
                                                                          reachabilityRegion.getVertex(i),
                                                                          projectedReachablePoint,
                                                                          stepConstraintRegion);
         reachabilityRegionInConstraintPlane.addVertex(projectedReachablePoint);
      }
      reachabilityRegionInConstraintPlane.update();
   }

   private final Point2DBasics centroidToThrowAway = new Point2D();

   public boolean validateConvexityOfPlanarRegion()
   {
      if (stepConstraintRegion == null)
      {
         isEnvironmentConstraintValid.set(true);
         return isEnvironmentConstraintValid.getBooleanValue();
      }

      double concaveHullArea = EuclidGeometryPolygonTools.computeConvexPolygon2DArea(stepConstraintRegion.getConcaveHullVertices(),
                                                                                     stepConstraintRegion.getConcaveHullSize(),
                                                                                     true,
                                                                                     centroidToThrowAway);
      double convexHullArea = stepConstraintRegion.getConvexHullInConstraintRegion().getArea();

      isEnvironmentConstraintValid.set(concaveHullArea / convexHullArea < maxConcaveEstimateRatio.getValue());
      return isEnvironmentConstraintValid.getBooleanValue();
   }

   private final FramePose3D originalPose = new FramePose3D();

   public boolean applyEnvironmentConstraintToFootstep(RobotSide upcomingFootstepSide,
                                                    FixedFramePose3DBasics footstepPoseToPack,
                                                    List<Point2D> predictedContactPoints)
   {
      if (stepConstraintRegion == null)
         return false;

      computeShrunkConvexHull(stepConstraintRegion, upcomingFootstepSide, predictedContactPoints, footstepPoseToPack.getOrientation());

      stepXY.set(footstepPoseToPack.getPosition());
      yoShrunkConvexHullConstraint.orthogonalProjection(stepXY);

      originalPose.set(footstepPoseToPack);

      footstepPoseToPack.getPosition().set(stepXY, stepConstraintRegion.getPlaneZGivenXY(stepXY.getX(), stepXY.getY()));
      footstepPoseToPack.getOrientation().set(stepConstraintRegion.getTransformToWorld().getRotation());

      return originalPose.getPositionDistance(footstepPoseToPack) > 1e-5 || originalPose.getOrientationDistance(footstepPoseToPack) > 1e-5;
   }

   private void computeShrunkConvexHull(StepConstraintRegion stepConstraintRegion,
                                        RobotSide upcomingFootstepSide,
                                        List<? extends Point2DBasics> predictedContactPoints,
                                        Orientation3DReadOnly orientation)
   {
      computeFootstepPolygon(upcomingFootstepSide, predictedContactPoints, orientation);

      yoConvexHullConstraint.set(stepConstraintRegion.getConvexHullInConstraintRegion());
      yoConvexHullConstraint.applyTransform(stepConstraintRegion.getTransformToWorld(), false);

      scaler.scaleConvexPolygonToContainInteriorPolygon(yoConvexHullConstraint,
                                                        footstepPolygon,
                                                        desiredDistanceInsideConstraint.getValue(),
                                                        shrunkHullConstraint);

      convexPolygonTools.computeIntersectionOfPolygons(shrunkHullConstraint, reachabilityRegionInConstraintPlane, yoShrunkConvexHullConstraint);
   }

   private void computeFootstepPolygon(RobotSide upcomingFootstepSide, List<? extends Point2DBasics> predictedContactPoints, Orientation3DReadOnly orientation)
   {
      if (predictedContactPoints.isEmpty() || !usePredictedContactPoints.getValue())
         predictedContactPoints = contactableFeet.get(upcomingFootstepSide).getContactPoints2d();

      footstepPolygon.clear();
      for (int i = 0; i < predictedContactPoints.size(); i++)
         footstepPolygon.addVertex(predictedContactPoints.get(i));
      footstepPolygon.update();

      footOrientationTransform.getRotation().set(orientation);

      footstepPolygon.applyTransform(footOrientationTransform, false);
   }
}