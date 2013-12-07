package us.ihmc.darpaRoboticsChallenge.calib;

import static java.lang.Double.parseDouble;
import georegression.geometry.RotationMatrixGenerator;
import georegression.struct.point.Vector3D_F64;
import georegression.struct.se.Se3_F64;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.media.j3d.Transform3D;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.vecmath.AxisAngle4d;
import javax.vecmath.Matrix3d;
import javax.vecmath.Vector3d;

import us.ihmc.commonWalkingControlModules.partNamesAndTorques.LimbName;
import us.ihmc.graphics3DAdapter.graphics.appearances.YoAppearanceRGBColor;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.utilities.math.MatrixTools;
import us.ihmc.utilities.math.geometry.FrameOrientation;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FramePose;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.OneDoFJoint;

import boofcv.misc.BoofMiscOps;
import boofcv.struct.calib.IntrinsicParameters;

import com.yobotics.simulationconstructionset.IndexChangedListener;
import com.yobotics.simulationconstructionset.util.graphics.DynamicGraphicCoordinateSystem;
import com.yobotics.simulationconstructionset.util.graphics.DynamicGraphicPosition;
import com.yobotics.simulationconstructionset.util.math.frames.YoFramePoint;
import com.yobotics.simulationconstructionset.util.math.frames.YoFramePose;

public class AtlasHeadLoopKinematicCalibrator extends AtlasKinematicCalibrator
{
   public static String TARGET_TO_CAMERA_KEY = "targetToCamera";
   public static String CAMERA_IMAGE_KEY = "cameraImage";
   //YoVariables for Display
   private final YoFramePoint ypLeftEE, ypRightEE;
   private final YoFramePose yposeLeftEE, yposeRightEE, yposeBoard,yposeLeftCamera;
   private final ArrayList<Map<String,Object>> metaData;
   final ReferenceFrame cameraFrame;

   private ImageIcon iiDisplay = null;
   private boolean alignCamera = true;


   public AtlasHeadLoopKinematicCalibrator()
   {
      super();
      ypLeftEE = new YoFramePoint("leftEE", ReferenceFrame.getWorldFrame(), registry);
      ypRightEE = new YoFramePoint("rightEE", ReferenceFrame.getWorldFrame(),registry);
      yposeLeftEE = new YoFramePose("leftPoseEE", "", ReferenceFrame.getWorldFrame(), registry);
      yposeRightEE = new YoFramePose("rightPoseEE", "", ReferenceFrame.getWorldFrame(), registry);
      yposeBoard = new YoFramePose("board","",ReferenceFrame.getWorldFrame(),registry);
      yposeLeftCamera = new YoFramePose("leftCamera","",ReferenceFrame.getWorldFrame(),registry);

      cameraFrame = fullRobotModel.getCameraFrame("stereo_camera_left");
      metaData = new ArrayList<>();
   }

   @Override
   protected void setupDynamicGraphicObjects()
   {
      //standard SCS Dynamic Graphics Object - automatically updated to the associated yoVariables
      double transparency = 0.5;
      double scale=0.02;
      DynamicGraphicPosition dgpLeftEE = new DynamicGraphicPosition("dgpLeftEE", ypLeftEE, scale, new YoAppearanceRGBColor(Color.BLUE, transparency));
      DynamicGraphicPosition dgpRightEE = new DynamicGraphicPosition("dgpRightEE", ypRightEE, scale, new YoAppearanceRGBColor(Color.RED, transparency));

      scs.addDynamicGraphicObject(dgpLeftEE);
      scs.addDynamicGraphicObject(dgpRightEE);

      DynamicGraphicCoordinateSystem dgPoseLeftEE = new DynamicGraphicCoordinateSystem("dgposeLeftEE", yposeLeftEE, 5*scale);
      DynamicGraphicCoordinateSystem dgPoseRightEE = new DynamicGraphicCoordinateSystem("dgposeRightEE", yposeRightEE, 5*scale);
      DynamicGraphicCoordinateSystem dgPoseBoard = new DynamicGraphicCoordinateSystem("dgposeBoard", yposeBoard, 5*scale);
      DynamicGraphicCoordinateSystem dgPoseLeftCamera = new DynamicGraphicCoordinateSystem("dgposeLeftCamera", yposeLeftCamera, 5*scale);
      scs.addDynamicGraphicObject(dgPoseLeftEE);
      scs.addDynamicGraphicObject(dgPoseRightEE);
      scs.addDynamicGraphicObject(dgPoseBoard);
      scs.addDynamicGraphicObject(dgPoseLeftCamera);

      //Homemade Image Display Panel - updated by the IndexChangedListener 
      iiDisplay = new ImageIcon();
      JPanel panel = new JPanel(new BorderLayout());
      final JLabel lblDisplay= new JLabel("", iiDisplay, JLabel.CENTER);
      panel.add(lblDisplay, BorderLayout.CENTER);
      scs.addExtraJpanel(panel,"Image");
      scs.getStandardSimulationGUI().selectPanel("Image");
      scs.getDataBuffer().attachIndexChangedListener(new IndexChangedListener()
      {
         @Override
         public void indexChanged(int newIndex, double newTime)
         {
            updateBoard((newIndex+q.size()-1) % q.size());
            lblDisplay.repaint();
            if(alignCamera)
               scsAlignCameraToRobotCamera();
         }
      });
      //scs.getStandardSimulationGUI().selectPanel("Image");

      //Set Camera Info
      String intrinsicFile = "../DarpaRoboticsChallenge/data/calibration_images/intrinsic_ros.xml";
      IntrinsicParameters intrinsic = BoofMiscOps.loadXML(intrinsicFile);
      double fovh=Math.atan(intrinsic.getCx()/intrinsic.getFx())+Math.atan((intrinsic.width-intrinsic.getCx())/intrinsic.getFx());
      System.out.println("Set fov to " + Math.toDegrees(fovh) + "degs from " + intrinsicFile)    ;
      scs.setFieldOfView(fovh);
      scs.maximizeMainWindow();

      JCheckBox chkAlignCamera = new JCheckBox("AlignCamera", alignCamera);
      chkAlignCamera.addItemListener(new ItemListener()
      {

         @Override
         public void itemStateChanged(ItemEvent e)
         {
            alignCamera = !alignCamera;
            if(alignCamera)
               scsAlignCameraToRobotCamera();
         }
      });
      scs.addCheckBox(chkAlignCamera);
   }

   @Override
   protected void updateDynamicGraphicsObjects(int index)
   {
      /*put yo-variablized objects here */
      FramePoint
            leftEE=new FramePoint(fullRobotModel.getEndEffectorFrame(RobotSide.LEFT, LimbName.ARM)  ,0, 0.13,0),
            rightEE=new FramePoint(fullRobotModel.getEndEffectorFrame(RobotSide.RIGHT, LimbName.ARM),0,-0.13,0);


      ypLeftEE.set(leftEE.changeFrameCopy(CalibUtil.world));
      ypRightEE.set(rightEE.changeFrameCopy(CalibUtil.world));

      yposeLeftEE.set(new FramePose(leftEE, new FrameOrientation(leftEE.getReferenceFrame())).changeFrameCopy(CalibUtil.world));
      yposeRightEE.set(new FramePose(rightEE,new FrameOrientation(rightEE.getReferenceFrame())).changeFrameCopy(CalibUtil.world));

      updateBoard(index);
   }

   private void scsAlignCameraToRobotCamera()
   {
      //Camera Pos(behind the eye 10cm), Fix(Eye farme origin)
      FramePoint cameraPos = new FramePoint(cameraFrame,-0.01,0,0).changeFrameCopy(CalibUtil.world);
      FramePoint cameraFix = new FramePoint(cameraFrame).changeFrameCopy(CalibUtil.world);
      scs.setCameraPosition(cameraPos.getX(), cameraPos.getY(), cameraPos.getZ());
      scs.setCameraFix(cameraFix.getX(), cameraFix.getY(), cameraFix.getZ());
   }

   private void updateBoard(int index)
   {
      //update camera pose display
      Transform3D imageToCamera = new Transform3D(new double[]{ 0,0,1,0,  -1,0,0,0,  0,-1,0,0,  0,0,0,1});
      ReferenceFrame cameraImageFrame = ReferenceFrame.
            constructBodyFrameWithUnchangingTransformToParent("cameraImage", cameraFrame,imageToCamera);
      yposeLeftCamera.set(new FramePose(cameraImageFrame).changeFrameCopy(CalibUtil.world));

      //update board
      Map<String,Object> mEntry = metaData.get(index);
      Transform3D targetToCamera = new Transform3D((Transform3D)mEntry.get(TARGET_TO_CAMERA_KEY)); //in camera frame
//      System.out.println("Original Rot\n"+targetToCamera);

      //update
      yposeBoard.set(new FramePose(cameraImageFrame, targetToCamera).changeFrameCopy(CalibUtil.world));
//      System.out.println("Index: "+ index);
//      System.out.println(targetToCamera);

      //image update
      iiDisplay.setImage((BufferedImage)mEntry.get(CAMERA_IMAGE_KEY));
   }

   private ArrayList<OneDoFJoint> getArmJoints()
   {
      ArrayList<OneDoFJoint> armJoints = new ArrayList<OneDoFJoint>();
      for(int i=0;i<joints.length;i++)
      {
         if(joints[i].getName().matches(".*arm.*"))
         {
            armJoints.add(joints[i]);
            if(DEBUG)
               System.out.println("arm "+ i + " "+joints[i].getName());
         }

      }
      return armJoints;
   }

   //   public KinematicCalibrationWristLoopResidual getArmLoopResidualObject()
//   {
//      ArrayList<String> calJointNames = CalibUtil.toStringArrayList(getArmJoints());
//      return new KinematicCalibrationWristLoopResidual(fullRobotModel, calJointNames, q);
//   }
//
   public void loadData(String directory ) throws IOException
   {
      File[] files = new File(directory).listFiles();

      Arrays.sort(files);
//      files = new File[]{files[3],files[20]};
      for( File f : files ) {
         if( !f.isDirectory() )
            continue;
         System.out.println("datafolder:" + f.toString());
         File fileTarget = new File(f,"target.txt");

         if( !fileTarget.exists() || fileTarget.length() == 0 )
            continue;

         Map<String,Object> mEntry = new HashMap<String, Object>();

         // parse targetToCamera transform
         BufferedReader reader = new BufferedReader(new FileReader(fileTarget));

         Se3_F64 targetToCamera = new Se3_F64();

         reader.readLine();         // skip comments

         //read rotation
         String row0[] = reader.readLine().split(" ");
         String row1[] = reader.readLine().split(" ");
         String row2[] = reader.readLine().split(" ");

         for( int col = 0; col < 3; col++ ) {
            targetToCamera.getR().set(0,col, parseDouble(row0[col]));
            targetToCamera.getR().set(1,col, parseDouble(row1[col]));
            targetToCamera.getR().set(2,col, parseDouble(row2[col]));
         }

         //read translation
         reader.readLine();
         String s[] = reader.readLine().split(" ");
         targetToCamera.getT().set( parseDouble(s[0]),parseDouble(s[1]),parseDouble(s[2]));

         //copy Translation and Rotation
         Transform3D transform = new Transform3D();
         Vector3D_F64 T = targetToCamera.T;
         transform.setTranslation(new Vector3d(T.x, T.y, T.z));

         Matrix3d matrix3d = new Matrix3d();
         MatrixTools.denseMatrixToMatrix3d(targetToCamera.getR(),matrix3d,0,0);
         transform.setRotation(matrix3d);
         mEntry.put(TARGET_TO_CAMERA_KEY,transform);

         //load image
         mEntry.put(CAMERA_IMAGE_KEY,ImageIO.read(new File(f,"/detected.jpg")));


         // load joint angles
         Properties properties = new Properties();
         properties.load(new FileReader(new File(f,"q.m")));
         Map<String,Double> qEntry = new HashMap<>();
         for(Map.Entry e : properties.entrySet() ) {
            qEntry.put((String)e.getKey(),Double.parseDouble((String)e.getValue()));
         }

         //storage
         metaData.add(mEntry);
         q.add(qEntry);
      }
   }


   public static void main(String[] arg) throws InterruptedException, IOException
   {
      AtlasHeadLoopKinematicCalibrator calib = new AtlasHeadLoopKinematicCalibrator();
      calib.loadData("data/chessboard_joints_20131204");

      // calJointNames order is the prm order
//      KinematicCalibrationWristLoopResidual residualFunc = calib.getArmLoopResidualObject();
//      double[] prm = new double[residualFunc.getN()];
//      double[] residual0 = residualFunc.calcResiduals(prm);
//      calib.calibrate(residualFunc,prm, 100);
//      double[] residual = residualFunc.calcResiduals(prm);
//
//
//      //display prm in readable format
//      Map<String,Double> qoffset= residualFunc.prmArrayToJointMap(prm);
//      for(String jointName: qoffset.keySet())
//      {
//         System.out.println("jointAngleOffsetPreTransmission.put(AtlasJointId.JOINT_" + jointName.toUpperCase()+", "+qoffset.get(jointName)+");");
//         //System.out.println(jointName + " "+ qoffset.get(jointName));
//      }
//      System.out.println("wristSpacing "+prm[prm.length-1]);
//
      //push data to visualizer
      boolean start_scs=true;
      if(start_scs)
      {
         //Yovariables for display
//         YoFramePose yoResidual0 = new YoFramePose("residual0", "", ReferenceFrame.getWorldFrame(),calib.registry);
//         YoFramePose yoResidual = new YoFramePose("residual", "",ReferenceFrame.getWorldFrame(),calib.registry);

         calib.createDisplay(calib.q.size());

         for(int i=0;i<calib.q.size();i++)
         {
            CalibUtil.setRobotModelFromData(calib.fullRobotModel, (Map)calib.q.get(i) );
//            CalibUtil.setRobotModelFromData(calib.fullRobotModel, CalibUtil.addQ(calib.q.get(i),qoffset));
//            yoResidual0.setXYZYawPitchRoll(Arrays.copyOfRange(residual0, i*RESIDUAL_DOF, i*RESIDUAL_DOF+6));
//            yoResidual.setXYZYawPitchRoll(Arrays.copyOfRange(residual, i*RESIDUAL_DOF, i*RESIDUAL_DOF+6));
            calib.displayUpdate(i);
         }
      } //viz

   }
}
