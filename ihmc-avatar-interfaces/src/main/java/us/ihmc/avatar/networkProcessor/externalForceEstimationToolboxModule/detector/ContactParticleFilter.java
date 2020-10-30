package us.ihmc.avatar.networkProcessor.externalForceEstimationToolboxModule.detector;

import org.ejml.data.DMatrixRMaj;
import us.ihmc.avatar.networkProcessor.externalForceEstimationToolboxModule.EstimatorContactPoint;
import us.ihmc.avatar.networkProcessor.externalForceEstimationToolboxModule.ForceEstimatorDynamicMatrixUpdater;
import us.ihmc.avatar.networkProcessor.externalForceEstimationToolboxModule.JointspaceExternalContactEstimator;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tools.EuclidCoreRandomTools;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicPosition;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicVector;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.matrixlib.MatrixTools;
import us.ihmc.mecano.multiBodySystem.interfaces.JointBasics;
import us.ihmc.mecano.multiBodySystem.interfaces.JointReadOnly;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.robotics.physics.Collidable;
import us.ihmc.simulationconstructionset.util.RobotController;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFramePoint3D;
import us.ihmc.yoVariables.euclid.referenceFrame.YoFrameVector3D;
import us.ihmc.yoVariables.registry.YoRegistry;
import us.ihmc.yoVariables.variable.YoDouble;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Implementation of the particle-filter based external contact estimator presented here:
 * http://groups.csail.mit.edu/robotics-center/public_papers/Manuelli16.pdf
 */
public class ContactParticleFilter implements RobotController
{
   private static final int numberOfParticles = 20;
   private static final int estimationVariables = 3;

   private final String name = getClass().getSimpleName();
   private final YoRegistry registry = new YoRegistry(name);
   private final Random random = new Random(34098);

   private RigidBodyBasics rigidBody = null;

   private final YoDouble coefficientOfFriction = new YoDouble("coefficientOfFriction", registry);
   private final YoDouble particleMotionVariance = new YoDouble("particleMotionVariance", registry);

   private final JointBasics[] joints;
   private final int dofs;
   private final JointspaceExternalContactEstimator jointspaceExternalContactEstimator;
   private final DMatrixRMaj systemJacobian;

   private final ContactPointEvaluator contactPointEvaluator = new ContactPointEvaluator();

   private final EstimatorContactPoint[] contactPoints = new EstimatorContactPoint[numberOfParticles];
   private final ContactPointProjector[] contactPointProjectors = new ContactPointProjector[numberOfParticles];
   private final YoDouble[] contactPointProbabilities = new YoDouble[numberOfParticles];
   private final YoFramePoint3D[] contactPointPositions = new YoFramePoint3D[numberOfParticles];
   private final YoFrameVector3D[] scaledSurfaceNormal = new YoFrameVector3D[numberOfParticles];

   private final FramePoint3D pointToProject = new FramePoint3D();

   public ContactParticleFilter(JointBasics[] joints,
                                double dt,
                                ForceEstimatorDynamicMatrixUpdater dynamicMatrixUpdater,
                                List<Collidable> collidables,
                                YoGraphicsListRegistry graphicsListRegistry,
                                YoRegistry parentRegistry)
   {
      this.joints = joints;
      this.jointspaceExternalContactEstimator = new JointspaceExternalContactEstimator(joints, dt, dynamicMatrixUpdater, registry);
      this.dofs = Arrays.stream(joints).mapToInt(JointReadOnly::getDegreesOfFreedom).sum();
      this.systemJacobian = new DMatrixRMaj(estimationVariables, dofs);

      for (int i = 0; i < numberOfParticles; i++)
      {
         contactPointProbabilities[i] = new YoDouble("cpProb_" + i, registry);
         contactPointPositions[i] = new YoFramePoint3D("cpPosition_" + i, ReferenceFrame.getWorldFrame(), registry);
         scaledSurfaceNormal[i] = new YoFrameVector3D("cpNorm_" + i, ReferenceFrame.getWorldFrame(), registry);
         contactPointProjectors[i] = new ContactPointProjector(collidables);

         if (graphicsListRegistry != null)
         {
            YoGraphicPosition contactPointGraphic = new YoGraphicPosition("cpPositionViz_" + i, contactPointPositions[i], 0.015, YoAppearance.Red());
            YoGraphicVector contactVectorGraphic = new YoGraphicVector("cpNormViz_" + i, contactPointPositions[i], scaledSurfaceNormal[i], 1.0, YoAppearance.Red());
            graphicsListRegistry.registerYoGraphic(name, contactPointGraphic);
            graphicsListRegistry.registerYoGraphic(name, contactVectorGraphic);
         }
      }

      coefficientOfFriction.set(0.5);
      particleMotionVariance.set(0.07);

      if (parentRegistry != null)
      {
         parentRegistry.addChild(registry);
      }
   }

   public void setLinkToEstimate(RigidBodyBasics rigidBody)
   {
      this.rigidBody = rigidBody;

      for (int i = 0; i < numberOfParticles; i++)
      {
         contactPoints[i] = new EstimatorContactPoint(joints, rigidBody, true);
         contactPointProjectors[i].initialize(rigidBody);
      }
   }

   @Override
   public void initialize()
   {
      if (rigidBody == null)
      {
         throw new RuntimeException("Must set estimation link before initializing");
      }

      jointspaceExternalContactEstimator.initialize();

      double initialSearchRadius = 2.0;
      for (int i = 0; i < numberOfParticles; i++)
      {
         Vector3D randomVector = EuclidCoreRandomTools.nextVector3DWithFixedLength(random, initialSearchRadius);
         pointToProject.setIncludingFrame(rigidBody.getBodyFixedFrame(), randomVector);
         pointToProject.changeFrame(ReferenceFrame.getWorldFrame());
         contactPointProjectors[i].computeProjection(pointToProject);

         contactPoints[i].setContactPointOffset(pointToProject);
      }

      contactPointEvaluator.setCoefficientOfFriction(coefficientOfFriction.getDoubleValue());
   }

   @Override
   public void doControl()
   {
      jointspaceExternalContactEstimator.doControl();
      DMatrixRMaj observedExternalJointTorque = jointspaceExternalContactEstimator.getObservedExternalJointTorque();

      double totalWeight = 0.0;

      for (int i = 0; i < numberOfParticles; i++)
      {
         EstimatorContactPoint contactPoint = contactPoints[i];
         DMatrixRMaj contactPointJacobian = contactPoint.computeContactJacobian();

         for (int j = 0; j < contactPointJacobian.getNumCols(); j++)
         {
            int column = contactPoint.getSystemJacobianIndex(j);
            MatrixTools.setMatrixBlock(systemJacobian, 0, column, contactPointJacobian, 0, j, estimationVariables, 1, 1.0);
         }

         double likelihoodCost = contactPointEvaluator.computeMaximumLikelihoodForce(observedExternalJointTorque,
                                                                                     systemJacobian,
                                                                                     contactPointProjectors[i].getSurfaceFrame());
         double likelihoodWeight = Math.exp(-0.5 * likelihoodCost);
         contactPointProbabilities[i].set(likelihoodWeight);
         totalWeight += likelihoodWeight;
      }

      for (int i = 0; i < numberOfParticles; i++)
      {
         contactPointProbabilities[i].mul(1.0 / totalWeight);

         contactPointPositions[i].set(contactPointProjectors[i].getSurfacePoint());
         scaledSurfaceNormal[i].set(contactPointProjectors[i].getSurfaceNormal());
         scaledSurfaceNormal[i].scale(contactPointProbabilities[i].getDoubleValue());
      }
   }

   @Override
   public YoRegistry getYoRegistry()
   {
      return registry;
   }
}
