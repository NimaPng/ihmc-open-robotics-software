package us.ihmc.darpaRoboticsChallenge.initialSetup;

import javax.vecmath.Vector3d;

import us.ihmc.SdfLoader.SDFRobot;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotJointMap;

public class SquaredUpVRCQual3Platform extends SquaredUpDRCRobotInitialSetup implements DRCRobotInitialSetup<SDFRobot>
{
   private final Vector3d additionalOffset = new Vector3d(16.4, 0.0, 1.0);
   private final double yaw = - 0.5*Math.PI;

   private Vector3d newOffset = null;

   public SquaredUpVRCQual3Platform(double groundHeight)
   {
      super(groundHeight);
   }
   
   public SquaredUpVRCQual3Platform()
   {
      this(0.0);
   }

   public void initializeRobot(SDFRobot robot, DRCRobotJointMap jointMap)
   {
      super.initializeRobot(robot, jointMap);

      if (newOffset == null)
      {
         newOffset = new Vector3d();
         super.getOffset(newOffset);
         newOffset.add(additionalOffset);
      }

      super.setOffset(newOffset);
      robot.setPositionInWorld(newOffset);
      robot.setOrientation(yaw, 0.0, 0.0);
   }
}
