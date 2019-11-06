package us.ihmc.footstepPlanning.graphSearch.graph.visualization;

import controller_msgs.msg.dds.FootstepNodeDataMessage;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.geometry.interfaces.Pose3DReadOnly;
import us.ihmc.footstepPlanning.graphSearch.footstepSnapping.FootstepNodeSnapData;
import us.ihmc.footstepPlanning.graphSearch.graph.FootstepNodeTools;
import us.ihmc.footstepPlanning.graphSearch.graph.LatticeNode;
import us.ihmc.robotics.robotSide.RobotSide;

public class PlannerNodeData
{
   private final int parentNodeId;
   private final int nodeId;
   private final RobotSide robotSide;

   private final Pose3D pose = new Pose3D();
   private final LatticeNode latticeNode;

   private final int hashCode;

   private BipedalFootstepPlannerNodeRejectionReason rejectionReason;

   public PlannerNodeData(int nodeId, int parentNodeId, int xIndex, int yIndex, int yawIndex, RobotSide robotSide, Pose3DReadOnly pose, BipedalFootstepPlannerNodeRejectionReason rejectionReason)
   {
      this.latticeNode = new LatticeNode(xIndex, yIndex, yawIndex);
      this.nodeId = nodeId;
      this.parentNodeId = parentNodeId;
      this.robotSide = robotSide;
      this.pose.set(pose);
      this.rejectionReason = rejectionReason;

      hashCode = latticeNode.hashCode();
   }

   public PlannerNodeData(FootstepNodeDataMessage message)
   {
      this(message.getNodeId(), message.getParentNodeId(), message.getXIndex(), message.getYIndex(), message.getYawIndex(),
           RobotSide.fromByte(message.getRobotSide()), new Pose3D(message.getPosition(), message.getOrientation()),
           BipedalFootstepPlannerNodeRejectionReason.fromByte(message.getBipedalFootstepPlannerNodeRejectionReason()));
   }

   public LatticeNode getLatticeNode()
   {
      return latticeNode;
   }

   public int getNodeId()
   {
      return nodeId;
   }

   public int getParentNodeId()
   {
      return parentNodeId;
   }

   public RobotSide getRobotSide()
   {
      return robotSide;
   }

   public Pose3DReadOnly getNodePose()
   {
      return pose;
   }

   public BipedalFootstepPlannerNodeRejectionReason getRejectionReason()
   {
      return rejectionReason;
   }

   public void setRejectionReason(BipedalFootstepPlannerNodeRejectionReason rejectionReason)
   {
      this.rejectionReason = rejectionReason;
   }

   public FootstepNodeDataMessage getAsMessage()
   {
      FootstepNodeDataMessage message = new FootstepNodeDataMessage();
      getAsMessage(message);
      return message;
   }

   public void getAsMessage(FootstepNodeDataMessage message)
   {
      byte rejectionReason = getRejectionReason() != null ? getRejectionReason().toByte() : (byte) 255;

      message.setNodeId(getNodeId());
      message.setParentNodeId(getParentNodeId());
      message.setXIndex(getLatticeNode().getXIndex());
      message.setYIndex(getLatticeNode().getYIndex());
      message.setYawIndex(getLatticeNode().getYawIndex());
      message.setRobotSide(getRobotSide().toByte());
      message.getPosition().set(getNodePose().getPosition());
      message.getOrientation().set(getNodePose().getOrientation());
      message.setBipedalFootstepPlannerNodeRejectionReason(rejectionReason);
   }

   @Override
   public int hashCode()
   {
      return hashCode;
   }

   @Override
   public boolean equals(Object obj)
   {
      return getLatticeNode().equals(obj);
   }
}
