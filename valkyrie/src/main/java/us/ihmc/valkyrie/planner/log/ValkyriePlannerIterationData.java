package us.ihmc.valkyrie.planner.log;

import us.ihmc.footstepPlanning.graphSearch.footstepSnapping.FootstepNodeSnapData;
import us.ihmc.footstepPlanning.graphSearch.graph.FootstepNode;
import us.ihmc.pathPlanning.graph.structure.GraphEdge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ValkyriePlannerIterationData
{
   private FootstepNode stanceNode = null;
   private FootstepNode idealStep = null;
   private final List<FootstepNode> childNodes = new ArrayList<>();
   private final FootstepNodeSnapData stanceNodeSnapData = FootstepNodeSnapData.identityData();

   public ValkyriePlannerIterationData()
   {
      clear();
   }

   public void setStanceNode(FootstepNode stanceNode)
   {
      this.stanceNode = stanceNode;
   }

   public void setIdealStep(FootstepNode idealStep)
   {
      this.idealStep = idealStep;
   }

   public void setStanceNodeSnapData(FootstepNodeSnapData stanceNodeSnapData)
   {
      this.stanceNodeSnapData.set(stanceNodeSnapData);
   }

   public void addChildNode(FootstepNode childNode)
   {
      childNodes.add(childNode);
   }

   public FootstepNode getStanceNode()
   {
      return stanceNode;
   }

   public FootstepNode getIdealStep()
   {
      return idealStep;
   }

   public List<FootstepNode> getChildNodes()
   {
      return childNodes;
   }

   public FootstepNodeSnapData getStanceNodeSnapData()
   {
      return stanceNodeSnapData;
   }

   public void clear()
   {
      stanceNode = null;
      idealStep = null;
      childNodes.clear();
      stanceNodeSnapData.clear();
   }
}
