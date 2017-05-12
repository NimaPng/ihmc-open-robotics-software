package us.ihmc.humanoidBehaviors.behaviors.complexBehaviors;

import us.ihmc.communication.packets.TextToSpeechPacket;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.humanoidBehaviors.behaviors.AbstractBehavior;
import us.ihmc.humanoidBehaviors.communication.CommunicationBridge;
import us.ihmc.humanoidBehaviors.communication.ConcurrentListeningQueue;
import us.ihmc.humanoidRobotics.communication.packets.behaviors.HatchLocationPacket;

public class SearchForHatchBehavior extends AbstractBehavior
{
   private RigidBodyTransform hatchTransformToWorld;
   private boolean recievedNewHatchLocation = false;

   protected final ConcurrentListeningQueue<HatchLocationPacket> hatchLocationQueue = new ConcurrentListeningQueue<HatchLocationPacket>(10);

   public SearchForHatchBehavior(CommunicationBridge behaviorCommunicationBridge)
   {
      super("SearchForHatch", behaviorCommunicationBridge);
      attachNetworkListeningQueue(hatchLocationQueue, HatchLocationPacket.class);
   }

   @Override
   public void onBehaviorEntered()
   {
      TextToSpeechPacket p1 = new TextToSpeechPacket("Searching For The Hatch");
      sendPacket(p1);
   }

   @Override
   public void doControl()
   {
      if (hatchLocationQueue.isNewPacketAvailable())
      {
         receivedHatchLocation(hatchLocationQueue.getLatestPacket());
      }
   }

   @Override
   public boolean isDone()
   {
      return recievedNewHatchLocation;
   }

   @Override
   public void onBehaviorExited()
   {
      recievedNewHatchLocation = false;
   }

   public RigidBodyTransform getLocation()
   {
      return hatchTransformToWorld;
   }


   private void receivedHatchLocation(HatchLocationPacket hatchLocationPacket)
   {
      TextToSpeechPacket p1 = new TextToSpeechPacket("Received Hatch Location From UI");
      sendPacket(p1);
      hatchTransformToWorld = hatchLocationPacket.getHatchTransformToWorld();

      recievedNewHatchLocation = true;

   }

   @Override
   public void onBehaviorAborted()
   {
   }

   @Override
   public void onBehaviorPaused()
   {
   }

   @Override
   public void onBehaviorResumed()
   {
   }

}
