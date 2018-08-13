package us.ihmc.robotEnvironmentAwareness.hardware;

import controller_msgs.msg.dds.LidarScanMessage;
import geometry_msgs.Point;
import scan_to_cloud.PointCloud2WithSource;
import us.ihmc.communication.IHMCRealtimeROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.packets.MessageTools;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.ros2.RealtimeRos2Node;
import us.ihmc.utilities.ros.RosMainNode;
import us.ihmc.utilities.ros.subscriber.AbstractRosTopicSubscriber;
import us.ihmc.utilities.ros.subscriber.RosPointCloudSubscriber;
import us.ihmc.utilities.ros.subscriber.RosPointCloudSubscriber.UnpackedPointCloud;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class MultisensePointCloud2WithSourceReceiver extends AbstractRosTopicSubscriber<PointCloud2WithSource>
{
   private final RealtimeRos2Node ros2Node = ROS2Tools.createRealtimeRos2Node(PubSubImplementation.FAST_RTPS, "lidarScanPublisherNode");

   private final IHMCRealtimeROS2Publisher<LidarScanMessage> lidarScanPublisher;
   int n = 0;

   public MultisensePointCloud2WithSourceReceiver() throws URISyntaxException, IOException
   {
      super(PointCloud2WithSource._TYPE);
      URI masterURI = new URI("http://10.6.192.14:11311");
      RosMainNode rosMainNode = new RosMainNode(masterURI, "LidarScanPublisher", true);
      rosMainNode.attachSubscriber("/singleScanAsCloudWithSource", this);
      rosMainNode.execute();

      lidarScanPublisher = ROS2Tools.createPublisher(ros2Node, LidarScanMessage.class, ROS2Tools.getDefaultTopicNameGenerator());


      ROS2Tools.createCallbackSubscription(ros2Node, LidarScanMessage.class, ROS2Tools.getDefaultTopicNameGenerator(), s-> System.out.println("Next message id: " + s.takeNextData().sequence_id_));

      ros2Node.spin();
   }

   @Override
   public void onNewMessage(PointCloud2WithSource cloudHolder)
   {
      UnpackedPointCloud pointCloudData = RosPointCloudSubscriber.unpackPointsAndIntensities(cloudHolder.getCloud());
      Point3D[] points = pointCloudData.getPoints();

      Point translation = cloudHolder.getTranslation();
      Point3D lidarPosition = new Point3D(translation.getX(), translation.getY(), translation.getZ());
      geometry_msgs.Quaternion orientation = cloudHolder.getOrientation();
      Quaternion lidarQuaternion = new Quaternion(orientation.getX(), orientation.getY(), orientation.getZ(), orientation.getW());

      LidarScanMessage lidarScanMessage = new LidarScanMessage();
      lidarScanMessage.getLidarPosition().set(lidarPosition);
      lidarScanMessage.getLidarOrientation().set(lidarQuaternion);
      MessageTools.packScan(lidarScanMessage, points);
      lidarScanMessage.setSequenceId(n);
      System.out.println("Publishing: " + n++ + " " + points.length + " T:[" + lidarPosition.getX() +","+
              lidarPosition.getY() + "," + lidarPosition.getZ() + "] R:[" + orientation.getW() + ","+
      orientation.getX()+","+orientation.getY()+","+orientation.getZ()+"]\n");

      System.out.println("Publish success: " + lidarScanPublisher.publish(lidarScanMessage));

   }

   public static void main(String[] args) throws URISyntaxException, IOException
   {
      new MultisensePointCloud2WithSourceReceiver();
   }
}
