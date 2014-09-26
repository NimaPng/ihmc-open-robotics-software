package us.ihmc.steppr.hardware.visualization;

import us.ihmc.SdfLoader.SDFRobot;
import us.ihmc.acsell.parameters.BonoRobotModel;
import us.ihmc.robotDataCommunication.YoVariableClient;
import us.ihmc.robotDataCommunication.visualizer.SCSYoVariablesUpdatedListener;
import us.ihmc.steppr.hardware.StepprDashboard;
import us.ihmc.steppr.hardware.configuration.StepprNetworkParameters;
import us.ihmc.yoUtilities.dataStructure.variable.YoVariable;

import com.yobotics.simulationconstructionset.Robot;
import com.yobotics.simulationconstructionset.util.inputdevices.MidiSliderBoard;
import com.yobotics.simulationconstructionset.util.inputdevices.SliderBoardConfigurationManager;

public class StepprAirwalkSliderboard extends SCSYoVariablesUpdatedListener
{
   public StepprAirwalkSliderboard(Robot robot, int bufferSize)
   {
      super(robot, bufferSize);
   }

   @Override
   public void start()
   {
      StepprDashboard.createDashboard(scs, registry);
      MidiSliderBoard sliderBoard = new MidiSliderBoard(scs);

      YoVariable<?> hipAmplitude = registry.getVariable("StepprAirwalk", "hipAmplitude");
      YoVariable<?> hipFrequency = registry.getVariable("StepprAirwalk", "hipFrequency");
      YoVariable<?> kneeAmplitude = registry.getVariable("StepprAirwalk", "kneeAmplitude");
      YoVariable<?> kneeFrequency = registry.getVariable("StepprAirwalk", "kneeFrequency");
      YoVariable<?> ankleAmplitude = registry.getVariable("StepprAirwalk", "ankleAmplitude");
      YoVariable<?> ankleFrequency = registry.getVariable("StepprAirwalk", "ankleFrequency");
      
      sliderBoard.setSlider(1, hipAmplitude, 0, 1);
      sliderBoard.setSlider(2, hipFrequency, 0, 1);
      sliderBoard.setSlider(3, kneeAmplitude, 0, 1);
      sliderBoard.setSlider(4, kneeFrequency, 0, 1);
      sliderBoard.setSlider(5, ankleAmplitude, 0, 1);
      sliderBoard.setSlider(6, ankleFrequency, 0, 1);
      
      super.start();
   }
   
   public static void main(String[] args)
   {
      System.out.println("Connecting to host " + StepprNetworkParameters.CONTROL_COMPUTER_HOST);
      BonoRobotModel robotModel = new BonoRobotModel(true, false);
      SDFRobot robot = robotModel.createSdfRobot(false);

      SCSYoVariablesUpdatedListener scsYoVariablesUpdatedListener = new StepprAirwalkSliderboard(robot, 16384);

    
      
      YoVariableClient client = new YoVariableClient(StepprNetworkParameters.CONTROL_COMPUTER_HOST, StepprNetworkParameters.VARIABLE_SERVER_PORT,
            scsYoVariablesUpdatedListener, "remote", false);
      client.start();

   }
}
