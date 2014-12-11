package us.ihmc.steppr.hardware.visualization;

import us.ihmc.robotDataCommunication.YoVariableClient;
import us.ihmc.robotDataCommunication.visualizer.SCSVisualizer;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.util.inputdevices.MidiSliderBoard;
import us.ihmc.steppr.hardware.StepprDashboard;
import us.ihmc.steppr.hardware.configuration.StepprNetworkParameters;
import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;
import us.ihmc.yoUtilities.dataStructure.variable.YoVariable;

public class StepprAirwalkSliderboard extends SCSVisualizer
{
   public StepprAirwalkSliderboard(int bufferSize)
   {
      super(bufferSize);
   }

   @Override
   public void starting(SimulationConstructionSet scs, Robot robot, YoVariableRegistry registry)

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
      
   }
   
   public static void main(String[] args)
   {
      System.out.println("Connecting to host " + StepprNetworkParameters.CONTROL_COMPUTER_HOST);
      SCSVisualizer scsYoVariablesUpdatedListener = new StepprAirwalkSliderboard(16384);

    
      
      YoVariableClient client = new YoVariableClient(StepprNetworkParameters.CONTROL_COMPUTER_HOST, scsYoVariablesUpdatedListener,
            "remote", false);
      client.start();

   }
}
