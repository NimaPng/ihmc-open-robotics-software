package us.ihmc.humanoidBehaviors.tools;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.javaFXToolkit.scenes.View3DFactory;
import us.ihmc.javafx.applicationCreator.JavaFXApplicationCreator;
import us.ihmc.pathPlanning.PlannerTestEnvironments;
import us.ihmc.pathPlanning.visibilityGraphs.ui.graphics.PlanarRegionsGraphic;
import us.ihmc.robotics.geometry.PlanarRegionsList;

public class FakeREAVirtualCameraTest
{
   private static boolean VISUALIZE = Boolean.parseBoolean(System.getProperty("visualize")); // To visualize, pass -Dvisualize=true
   private static int windowCount = 0; // for tiling

   @BeforeAll
   static public void beforeAll()
   {
      JavaFXApplicationCreator.createAJavaFXApplication();
   }

   @AfterEach
   public void afterEach()
   {
      if (VISUALIZE) ThreadTools.sleepForever();
   }

   @Test
   public void testFakeREA1()
   {
      Platform.runLater(() ->
      {
         createAndShowPlanarRegionWindow(PlannerTestEnvironments.getTrickCorridorWCutFloor(), windowCount++);
      });
   }

   @Test
   public void testFakeREACutsComplexField()
   {
      Platform.runLater(() ->
      {
         createAndShowPlanarRegionWindow(PlannerTestEnvironments.getTrickCorridor(), windowCount++);
      });
   }

   private void createAndShowPlanarRegionWindow(PlanarRegionsList trickCorridorWCutFloor, int windowCount)
   {
      int windowWidth = 800;
      int windowHeight = 600;
      View3DFactory view3dFactory = new View3DFactory(windowWidth, windowHeight);
      view3dFactory.addCameraController(0.05, 2000.0, true);
      view3dFactory.addWorldCoordinateSystem(0.3);
      view3dFactory.addDefaultLighting();

      PlanarRegionsGraphic regionsGraphic = new PlanarRegionsGraphic();
      regionsGraphic.generateMeshes(trickCorridorWCutFloor);
      regionsGraphic.update();

      view3dFactory.addNodeToView(regionsGraphic);

      Stage primaryStage = new Stage();
      primaryStage.setTitle(getClass().getSimpleName());
      primaryStage.setMaximized(false);
      primaryStage.setScene(view3dFactory.getScene());

      int numberOfColumns = 3;
      primaryStage.setX((windowCount % numberOfColumns) * windowWidth);
      primaryStage.setY((windowCount / numberOfColumns) * windowHeight);
      primaryStage.show();
      primaryStage.toFront();
   }
}
