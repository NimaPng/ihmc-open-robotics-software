package us.ihmc.ihmcPerception.bagTools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import controller_msgs.msg.dds.LidarScanMessage;
import controller_msgs.msg.dds.LidarScanMessagePubSubType;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.IHMCROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.idl.serializers.extra.JSONSerializer;
import us.ihmc.pubsub.DomainFactory.PubSubImplementation;
import us.ihmc.ros2.Ros2Node;

import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and broadcasts to JSON file written by LidarScanMessageExporter, analogous to "rosbag play"
 */
public class LidarScanMessageImporter
{
   private static final long publishPeriodMillis = 85;

   public LidarScanMessageImporter(InputStream inputStream) throws IOException
   {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNode = objectMapper.readTree(inputStream);
      int numberOfMessages = jsonNode.size();

      List<LidarScanMessage> lidarScanMessages = new ArrayList<>();
      JSONSerializer<LidarScanMessage> messageSerializer = new JSONSerializer<>(new LidarScanMessagePubSubType());

      for (int i = 0; i < numberOfMessages; i++)
      {
         lidarScanMessages.add(messageSerializer.deserialize(jsonNode.get(i).toString()));
      }

      System.out.println("Loaded " + numberOfMessages + " messages");

      Ros2Node ros2Node = ROS2Tools.createRos2Node(PubSubImplementation.FAST_RTPS, getClass().getSimpleName());
      IHMCROS2Publisher<LidarScanMessage> publisher = ROS2Tools.createPublisher(ros2Node, LidarScanMessage.class, "/ihmc/lidar_scan");

      for (int i = 0; i < lidarScanMessages.size(); i++)
      {
         System.out.println("Publishing message " + i);
         publisher.publish(lidarScanMessages.get(i));
         ThreadTools.sleep(publishPeriodMillis);
      }
   }

   public static void main(String[] args) throws IOException
   {
      JFileChooser outputChooser = new JFileChooser();
      int returnVal = outputChooser.showSaveDialog(null);
      if (returnVal != JFileChooser.APPROVE_OPTION)
      {
         return;
      }

      File file = outputChooser.getSelectedFile();
      InputStream inputStream = new FileInputStream(file);
      new LidarScanMessageImporter(inputStream);
   }
}
