package us.ihmc.commonWalkingControlModules.modelPredictiveController;

import org.junit.jupiter.api.Test;
import us.ihmc.commonWalkingControlModules.modelPredictiveController.commands.*;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.geometry.interfaces.ConvexPolygon2DReadOnly;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.tools.EuclidCoreTestTools;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.yoVariables.registry.YoRegistry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CoMTrajectoryModelPredictiveControllerTest
{
   private static final double epsilon = 1e-3;

   @Test
   public void testSimpleStanding()
   {
      double gravityZ = -9.81;
      double dt = 0.001;
      double nominalHeight = 1.0;
      double duration = 1.5;
      double omega = Math.sqrt(Math.abs(gravityZ) / nominalHeight);
      YoRegistry testRegistry = new YoRegistry("testRegistry");

      CoMTrajectoryModelPredictiveController mpc = new CoMTrajectoryModelPredictiveController(gravityZ, nominalHeight, dt, testRegistry);

      List<ContactPlaneProvider> contactProviders = new ArrayList<>();

      ConvexPolygon2DReadOnly contactPolygon = MPCTestHelper.createDefaultContact();


      FramePose3D contactPose = new FramePose3D();
      contactPose.getPosition().set(0.5, 0.3, 0.0);

      ContactPlaneProvider contact = new ContactPlaneProvider();
      contact.getTimeInterval().setInterval(0.0, duration);
      contact.addContact(contactPose, contactPolygon);
      contact.setStartCopPosition(contactPose.getPosition());
      contact.setEndCopPosition(contactPose.getPosition());

      contactProviders.add(contact);

      FramePoint3D initialCoM = new FramePoint3D(contactPose.getPosition());
      initialCoM.setZ(nominalHeight);

      mpc.setInitialCenterOfMassState(initialCoM, new FrameVector3D());
      mpc.solveForTrajectory(contactProviders);
      mpc.compute(0, 0.0);

      List<CoMPositionCommand> comPositionCommands = new ArrayList<>();
      List<CoMVelocityCommand> comVelocityCommands = new ArrayList<>();
      List<DCMPositionCommand> dcmPositionCommands = new ArrayList<>();
      List<VRPPositionCommand> vrpPositionCommands = new ArrayList<>();
      List<VRPVelocityCommand> vrpVelocityCommands = new ArrayList<>();
      for (int i = 0; i < mpc.mpcCommands.getNumberOfCommands(); i++)
      {
         MPCCommand<?> command = mpc.mpcCommands.getCommand(i);
         if (command.getCommandType() == MPCCommandType.VALUE)
         {
            MPCValueType valueType = ((MPCValueCommand) command).getValueType();
            int derivativeOrder = ((MPCValueCommand) command).getDerivativeOrder();
            if (valueType == MPCValueType.COM)
            {
               if (derivativeOrder == 0)
               {
                  comPositionCommands.add((CoMPositionCommand) mpc.mpcCommands.getCommand(i));
               }
               else if (derivativeOrder == 1)
               {
                  comVelocityCommands.add((CoMVelocityCommand) mpc.mpcCommands.getCommand(i));
               }
            }
            else if (valueType == MPCValueType.DCM)
            {
               if (derivativeOrder == 0)
                  dcmPositionCommands.add((DCMPositionCommand) mpc.mpcCommands.getCommand(i));
            }
            else if (valueType == MPCValueType.VRP)
            {
               if (derivativeOrder == 0)
                  vrpPositionCommands.add((VRPPositionCommand) mpc.mpcCommands.getCommand(i));
               else if (derivativeOrder == 1)
                  vrpVelocityCommands.add((VRPVelocityCommand) mpc.mpcCommands.getCommand(i));
            }
         }
      }

      assertEquals(1, comPositionCommands.size());
      assertEquals(1, comVelocityCommands.size());
      assertEquals(1, dcmPositionCommands.size());
      assertEquals(2, vrpPositionCommands.size());
      assertEquals(2, vrpVelocityCommands.size());

      assertEquals(0.0, comPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, comPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, comPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, comVelocityCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, comVelocityCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), comVelocityCommands.get(0).getObjective(), epsilon);

      assertEquals(duration, dcmPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, dcmPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, dcmPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, vrpPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, vrpPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, vrpVelocityCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpVelocityCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), vrpVelocityCommands.get(0).getObjective(), epsilon);


      assertEquals(duration, vrpPositionCommands.get(1).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpPositionCommands.get(1).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, vrpPositionCommands.get(1).getObjective(), epsilon);

      assertEquals(duration, vrpVelocityCommands.get(1).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpVelocityCommands.get(1).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), vrpVelocityCommands.get(1).getObjective(), epsilon);

      EuclidCoreTestTools.assertPoint3DGeometricallyEquals(initialCoM, mpc.getDesiredCoMPosition(), epsilon);
   }

   @Test
   public void testSimpleStep()
   {
      double gravityZ = -9.81;
      double dt = 0.001;
      double nominalHeight = 1.0;
      double duration = 1.5;
      double omega = Math.sqrt(Math.abs(gravityZ) / nominalHeight);
      YoRegistry testRegistry = new YoRegistry("testRegistry");

      CoMTrajectoryModelPredictiveController mpc = new CoMTrajectoryModelPredictiveController(gravityZ, nominalHeight, dt, testRegistry);

      List<ContactPlaneProvider> contactProviders = new ArrayList<>();

      ConvexPolygon2DReadOnly contactPolygon = MPCTestHelper.createDefaultContact();


      FramePose3D contactPose1 = new FramePose3D();
      FramePose3D contactPose2 = new FramePose3D();
      contactPose1.getPosition().set(0.5, 0.3, 0.0);
      contactPose2.getPosition().set(0.7, 0.4, 0.0);

      ContactPlaneProvider contact1 = new ContactPlaneProvider();
      contact1.getTimeInterval().setInterval(0.0, duration);
      contact1.addContact(contactPose1, contactPolygon);
      contact1.setStartCopPosition(contactPose1.getPosition());
      contact1.setEndCopPosition(contactPose1.getPosition());
      ContactPlaneProvider contact2 = new ContactPlaneProvider();
      contact2.getTimeInterval().setInterval(duration, 2 * duration);
      contact2.addContact(contactPose2, contactPolygon);
      contact2.setStartCopPosition(contactPose2.getPosition());
      contact2.setEndCopPosition(contactPose2.getPosition());

      contactProviders.add(contact1);
      contactProviders.add(contact2);

      FramePoint3D initialCoM = new FramePoint3D(contactPose1.getPosition());
      initialCoM.setZ(nominalHeight);

      mpc.setInitialCenterOfMassState(initialCoM, new FrameVector3D());
      mpc.solveForTrajectory(contactProviders);
      mpc.compute(0, 0.0);

      List<CoMPositionCommand> comPositionCommands = new ArrayList<>();
      List<CoMVelocityCommand> comVelocityCommands = new ArrayList<>();
      List<DCMPositionCommand> dcmPositionCommands = new ArrayList<>();
      List<VRPPositionCommand> vrpPositionCommands = new ArrayList<>();
      List<VRPVelocityCommand> vrpVelocityCommands = new ArrayList<>();
      for (int i = 0; i < mpc.mpcCommands.getNumberOfCommands(); i++)
      {
         MPCCommand<?> command = mpc.mpcCommands.getCommand(i);
         if (command.getCommandType() == MPCCommandType.VALUE)
         {
            MPCValueType valueType = ((MPCValueCommand) command).getValueType();
            int derivativeOrder = ((MPCValueCommand) command).getDerivativeOrder();
            if (valueType == MPCValueType.COM)
            {
               if (derivativeOrder == 0)
               {
                  comPositionCommands.add((CoMPositionCommand) mpc.mpcCommands.getCommand(i));
               }
               else if (derivativeOrder == 1)
               {
                  comVelocityCommands.add((CoMVelocityCommand) mpc.mpcCommands.getCommand(i));
               }
            }
            else if (valueType == MPCValueType.DCM)
            {
               if (derivativeOrder == 0)
                  dcmPositionCommands.add((DCMPositionCommand) mpc.mpcCommands.getCommand(i));
            }
            else if (valueType == MPCValueType.VRP)
            {
               if (derivativeOrder == 0)
                  vrpPositionCommands.add((VRPPositionCommand) mpc.mpcCommands.getCommand(i));
               else if (derivativeOrder == 1)
                  vrpVelocityCommands.add((VRPVelocityCommand) mpc.mpcCommands.getCommand(i));
            }
         }
      }

      assertEquals(1, comPositionCommands.size());
      assertEquals(1, comVelocityCommands.size());
      assertEquals(1, dcmPositionCommands.size());
      assertEquals(2, vrpPositionCommands.size());
      assertEquals(2, vrpVelocityCommands.size());

      assertEquals(0.0, comPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, comPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, comPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, comVelocityCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, comVelocityCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), comVelocityCommands.get(0).getObjective(), epsilon);

      assertEquals(duration, dcmPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, dcmPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, dcmPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, vrpPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, vrpPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, vrpVelocityCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpVelocityCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), vrpVelocityCommands.get(0).getObjective(), epsilon);


      assertEquals(duration, vrpPositionCommands.get(1).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpPositionCommands.get(1).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, vrpPositionCommands.get(1).getObjective(), epsilon);

      assertEquals(duration, vrpVelocityCommands.get(1).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpVelocityCommands.get(1).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), vrpVelocityCommands.get(1).getObjective(), epsilon);

      EuclidCoreTestTools.assertPoint3DGeometricallyEquals(initialCoM, mpc.getDesiredCoMPosition(), epsilon);
   }

   @Test
   public void testSimpleStandingFewRhos()
   {
      double gravityZ = -9.81;
      double dt = 0.001;
      double nominalHeight = 1.0;
      double duration = 1.5;
      double omega = Math.sqrt(Math.abs(gravityZ) / nominalHeight);
      YoRegistry testRegistry = new YoRegistry("testRegistry");

      CoMTrajectoryModelPredictiveController mpc = new CoMTrajectoryModelPredictiveController(gravityZ, nominalHeight, dt, testRegistry);

      List<ContactPlaneProvider> contactProviders = new ArrayList<>();

      ConvexPolygon2D contactPolygon = new ConvexPolygon2D();
      contactPolygon.addVertex(0, 0);
      contactPolygon.update();

      FramePose3D contactPose = new FramePose3D();
      contactPose.getPosition().set(0.5, 0.3, 0.0);

      ContactPlaneProvider contact = new ContactPlaneProvider();
      contact.getTimeInterval().setInterval(0.0, duration);
      contact.addContact(contactPose, contactPolygon);
      contact.setStartCopPosition(contactPose.getPosition());
      contact.setEndCopPosition(contactPose.getPosition());

      contactProviders.add(contact);

      FramePoint3D initialCoM = new FramePoint3D(contactPose.getPosition());
      initialCoM.setZ(nominalHeight);

      mpc.setInitialCenterOfMassState(initialCoM, new FrameVector3D());
      mpc.solveForTrajectory(contactProviders);
      mpc.compute(0, 0.0);

      List<CoMPositionCommand> comPositionCommands = new ArrayList<>();
      List<CoMVelocityCommand> comVelocityCommands = new ArrayList<>();
      List<DCMPositionCommand> dcmPositionCommands = new ArrayList<>();
      List<VRPPositionCommand> vrpPositionCommands = new ArrayList<>();
      List<VRPVelocityCommand> vrpVelocityCommands = new ArrayList<>();
      for (int i = 0; i < mpc.mpcCommands.getNumberOfCommands(); i++)
      {
         MPCCommand<?> command = mpc.mpcCommands.getCommand(i);
         if (command.getCommandType() == MPCCommandType.VALUE)
         {
            MPCValueType valueType = ((MPCValueCommand) command).getValueType();
            int derivativeOrder = ((MPCValueCommand) command).getDerivativeOrder();
            if (valueType == MPCValueType.COM)
            {
               if (derivativeOrder == 0)
               {
                  comPositionCommands.add((CoMPositionCommand) mpc.mpcCommands.getCommand(i));
               }
               else if (derivativeOrder == 1)
               {
                  comVelocityCommands.add((CoMVelocityCommand) mpc.mpcCommands.getCommand(i));
               }
            }
            else if (valueType == MPCValueType.DCM)
            {
               if (derivativeOrder == 0)
                  dcmPositionCommands.add((DCMPositionCommand) mpc.mpcCommands.getCommand(i));
            }
            else if (valueType == MPCValueType.VRP)
            {
               if (derivativeOrder == 0)
                  vrpPositionCommands.add((VRPPositionCommand) mpc.mpcCommands.getCommand(i));
               else if (derivativeOrder == 1)
                  vrpVelocityCommands.add((VRPVelocityCommand) mpc.mpcCommands.getCommand(i));
            }
         }
      }

      assertEquals(1, comPositionCommands.size());
      assertEquals(1, comVelocityCommands.size());
      assertEquals(1, dcmPositionCommands.size());
      assertEquals(2, vrpPositionCommands.size());
      assertEquals(2, vrpVelocityCommands.size());

      assertEquals(0.0, comPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, comPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, comPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, comVelocityCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, comVelocityCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), comVelocityCommands.get(0).getObjective(), epsilon);

      assertEquals(duration, dcmPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, dcmPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, dcmPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, vrpPositionCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpPositionCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, vrpPositionCommands.get(0).getObjective(), epsilon);

      assertEquals(0.0, vrpVelocityCommands.get(0).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpVelocityCommands.get(0).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), vrpVelocityCommands.get(0).getObjective(), epsilon);


      assertEquals(duration, vrpPositionCommands.get(1).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpPositionCommands.get(1).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(initialCoM, vrpPositionCommands.get(1).getObjective(), epsilon);

      assertEquals(duration, vrpVelocityCommands.get(1).getTimeOfObjective(), epsilon);
      assertEquals(omega, vrpVelocityCommands.get(1).getOmega(), epsilon);
      EuclidCoreTestTools.assertTuple3DEquals(new Vector3D(), vrpVelocityCommands.get(1).getObjective(), epsilon);

      for (int i = 0 ; i < 22; i++)
      {
         assertNotEquals(0.0, mpc.qpSolver.solverInput_H.get(i, i), epsilon);
      }
      EuclidCoreTestTools.assertPoint3DGeometricallyEquals(initialCoM, mpc.getDesiredCoMPosition(), epsilon);
   }
}
