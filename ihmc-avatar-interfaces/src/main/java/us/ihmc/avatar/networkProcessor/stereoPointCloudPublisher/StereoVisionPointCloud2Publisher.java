package us.ihmc.avatar.networkProcessor.stereoPointCloudPublisher;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.AtomicDouble;

import controller_msgs.msg.dds.RobotConfigurationData;
import sensor_msgs.PointCloud2;
import us.ihmc.avatar.ros.RobotROSClockCalculator;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.IHMCROS2Publisher;
import us.ihmc.communication.IHMCRealtimeROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.ROS2Tools.MessageTopicNameGenerator;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.geometry.interfaces.Pose3DBasics;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.robotModels.FullRobotModel;
import us.ihmc.robotModels.FullRobotModelFactory;
import us.ihmc.ros2.RealtimeRos2Node;
import us.ihmc.ros2.Ros2Node;
import us.ihmc.sensorProcessing.communication.producers.RobotConfigurationDataBuffer;
import us.ihmc.utilities.ros.RosMainNode;
import us.ihmc.utilities.ros.subscriber.RosPointCloudSubscriber;

public class StereoVisionPointCloud2Publisher
{
   private static final boolean Debug = false;

   private static final Class<sensor_msgs.msg.dds.PointCloud2> messageType = sensor_msgs.msg.dds.PointCloud2.class;

   private static final int MAX_NUMBER_OF_POINTS = 1000;
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   private final String name = getClass().getSimpleName();
   private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(ThreadTools.getNamedThreadFactory(name));
   private ScheduledFuture<?> publisherTask;

   private final AtomicReference<DuplicatedPointCloud2> rosPointCloud2ToPublish = new AtomicReference<>(null);

   private final String robotName;
   private final FullRobotModel fullRobotModel;
   private ReferenceFrame stereoVisionPointsFrame = worldFrame;
   private StereoVisionWorldTransformCalculator stereoVisionTransformer = null;

   private final RobotConfigurationDataBuffer robotConfigurationDataBuffer = new RobotConfigurationDataBuffer();

   private RobotROSClockCalculator rosClockCalculator = null;

   private final IHMCROS2Publisher<sensor_msgs.msg.dds.PointCloud2> pointcloudPublisher;
   private final IHMCRealtimeROS2Publisher<sensor_msgs.msg.dds.PointCloud2> pointcloudRealtimePublisher;

   /**
    * units of velocities are meter/sec and rad/sec.
    */
   private long previousTimeStamp = 0;
   private final Point3D previousSensorPosition = new Point3D();
   private final Quaternion previousSensorOrientation = new Quaternion();
   private final AtomicReference<Boolean> enableFilter = new AtomicReference<Boolean>(false);
   private final AtomicDouble linearVelocityThreshold = new AtomicDouble(Double.MAX_VALUE);
   private final AtomicDouble angularVelocityThreshold = new AtomicDouble(Double.MAX_VALUE);

   public StereoVisionPointCloud2Publisher(FullRobotModelFactory modelFactory, Ros2Node ros2Node, String robotConfigurationDataTopicName)
   {
      this(modelFactory.getRobotDescription().getName(), modelFactory.createFullRobotModel(), ros2Node, null, robotConfigurationDataTopicName,
           ROS2Tools.getDefaultTopicNameGenerator());
   }

   public StereoVisionPointCloud2Publisher(FullRobotModelFactory modelFactory, Ros2Node ros2Node, String robotConfigurationDataTopicName,
                                           MessageTopicNameGenerator defaultTopicNameGenerator)
   {
      this(modelFactory.getRobotDescription().getName(), modelFactory.createFullRobotModel(), ros2Node, null, robotConfigurationDataTopicName,
           defaultTopicNameGenerator);
   }

   public StereoVisionPointCloud2Publisher(String robotName, FullRobotModel fullRobotModel, Ros2Node ros2Node, RealtimeRos2Node realtimeRos2Node,
                                           String robotConfigurationDataTopicName, MessageTopicNameGenerator defaultTopicNameGenerator)
   {
      this.robotName = robotName;
      this.fullRobotModel = fullRobotModel;

      String generateTopicName = defaultTopicNameGenerator.generateTopicName(messageType);
      if (ros2Node != null)
      {
         ROS2Tools.createCallbackSubscription(ros2Node, RobotConfigurationData.class, robotConfigurationDataTopicName,
                                              s -> robotConfigurationDataBuffer.receivedPacket(s.takeNextData()));
         pointcloudPublisher = ROS2Tools.createPublisher(ros2Node, messageType, generateTopicName);
         pointcloudRealtimePublisher = null;
      }
      else
      {
         ROS2Tools.createCallbackSubscription(realtimeRos2Node, RobotConfigurationData.class, robotConfigurationDataTopicName,
                                              s -> robotConfigurationDataBuffer.receivedPacket(s.takeNextData()));
         pointcloudPublisher = null;
         pointcloudRealtimePublisher = ROS2Tools.createPublisher(realtimeRos2Node, messageType, generateTopicName);
      }
   }

   public void start()
   {
      publisherTask = executorService.scheduleAtFixedRate(this::readAndPublishInternal, 0L, 1L, TimeUnit.MILLISECONDS);
   }

   public void shutdown()
   {
      publisherTask.cancel(false);
      executorService.shutdownNow();
   }

   public void receiveStereoPointCloudFromROS1(String stereoPointCloudROSTopic, URI rosCoreURI)
   {
      String graphName = robotName + "/" + name;
      RosMainNode rosMainNode = new RosMainNode(rosCoreURI, graphName, true);
      receiveStereoPointCloudFromROS1(stereoPointCloudROSTopic, rosMainNode);
   }

   public void receiveStereoPointCloudFromROS1(String stereoPointCloudROSTopic, RosMainNode rosMainNode)
   {
      rosMainNode.attachSubscriber(stereoPointCloudROSTopic, createROSPointCloud2Subscriber());
   }

   public void setROSClockCalculator(RobotROSClockCalculator rosClockCalculator)
   {
      this.rosClockCalculator = rosClockCalculator;
   }

   public void setCustomStereoVisionTransformer(StereoVisionWorldTransformCalculator transformer)
   {
      stereoVisionTransformer = transformer;
   }

   private RosPointCloudSubscriber createROSPointCloud2Subscriber()
   {
      return new RosPointCloudSubscriber()
      {
         @Override
         public void onNewMessage(PointCloud2 pointCloud)
         {
            rosPointCloud2ToPublish.set(new DuplicatedPointCloud2(pointCloud, 100));

            if (Debug)
               System.out.println("Receiving point cloud, n points: " + pointCloud.getHeight() * pointCloud.getWidth());
         }
      };
   }

   public void updateScanData(DuplicatedPointCloud2 scanDataToPublish)
   {
      this.rosPointCloud2ToPublish.set(scanDataToPublish);
   }

   public void readAndPublish()
   {
      if (publisherTask != null)
         throw new RuntimeException("The publisher is running using its own thread, cannot manually update it.");

      readAndPublishInternal();
   }

   private final RigidBodyTransform transformToWorld = new RigidBodyTransform();
   private final Pose3D sensorPose = new Pose3D();

   private void readAndPublishInternal()
   {
      try
      {
         transformDataAndPublish();
      }
      catch (Exception e)
      {
         e.printStackTrace();
         executorService.shutdown();
      }
   }

   private void transformDataAndPublish()
   {
      DuplicatedPointCloud2 pointCloudData = rosPointCloud2ToPublish.getAndSet(null);

      if (pointCloudData == null)
         return;

      long robotTimestamp;

      if (rosClockCalculator == null)
      {
         robotTimestamp = pointCloudData.getTimestamp();
         robotConfigurationDataBuffer.updateFullRobotModelWithNewestData(fullRobotModel, null);
      }
      else
      {
         long rosTimestamp = pointCloudData.getTimestamp();
         robotTimestamp = rosClockCalculator.computeRobotMonotonicTime(rosTimestamp);
         boolean waitForTimestamp = true;
         if (robotConfigurationDataBuffer.getNewestTimestamp() == -1)
            return;

         boolean success = robotConfigurationDataBuffer.updateFullRobotModel(waitForTimestamp, robotTimestamp, fullRobotModel, null) != -1;

         if (!success)
            return;
      }

      if (stereoVisionTransformer != null)
      {
         stereoVisionTransformer.computeTransformToWorld(fullRobotModel, transformToWorld, sensorPose);
         //pointCloudData.applyTransform(transformToWorld);
      }
      else
      {
         if (!stereoVisionPointsFrame.isWorldFrame())
         {
            stereoVisionPointsFrame.getTransformToDesiredFrame(transformToWorld, worldFrame);
            //pointCloudData.applyTransform(transformToWorld);
         }

         fullRobotModel.getHeadBaseFrame().getTransformToDesiredFrame(transformToWorld, worldFrame);
         sensorPose.set(transformToWorld);
      }

      if (enableFilter.get())
      {
         double timeDiff = Conversions.nanosecondsToSeconds(robotTimestamp - previousTimeStamp);
         double linearVelocity = sensorPose.getPosition().distance(previousSensorPosition) / timeDiff;
         double angularVelocity = sensorPose.getOrientation().distance(previousSensorOrientation) / timeDiff;

         previousTimeStamp = robotTimestamp;
         previousSensorPosition.set(sensorPose.getPosition());
         previousSensorOrientation.set(sensorPose.getOrientation());

         if (linearVelocity > linearVelocityThreshold.get() || angularVelocity > angularVelocityThreshold.get())
            return;
      }

      sensor_msgs.msg.dds.PointCloud2 message = pointCloudData.getRos2PointCloud2();

      if (pointcloudPublisher != null)
         pointcloudPublisher.publish(message);
      else
         pointcloudRealtimePublisher.publish(message);
   }

   public void enableFilter(boolean enable)
   {
      enableFilter.set(enable);
   }

   public void setFilterThreshold(double linearVelocityThreshold, double angularVelocityThreshold)
   {
      this.linearVelocityThreshold.set(linearVelocityThreshold);
      this.angularVelocityThreshold.set(angularVelocityThreshold);
   }

   public static interface StereoVisionWorldTransformCalculator
   {
      public void computeTransformToWorld(FullRobotModel fullRobotModel, RigidBodyTransform transformToWorldToPack, Pose3DBasics sensorPoseToPack);
   }
}
