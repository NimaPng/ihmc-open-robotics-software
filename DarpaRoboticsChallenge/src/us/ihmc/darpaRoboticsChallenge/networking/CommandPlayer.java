package us.ihmc.darpaRoboticsChallenge.networking;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.media.j3d.Transform3D;

import us.ihmc.commonWalkingControlModules.desiredFootStep.dataObjects.EndOfScriptCommand;
import us.ihmc.darpaRoboticsChallenge.configuration.DRCNetClassList;
import us.ihmc.darpaRoboticsChallenge.scriptEngine.ScriptEngineSettings;
import us.ihmc.darpaRoboticsChallenge.scriptEngine.ScriptFileLoader;
import us.ihmc.utilities.ThreadTools;
import us.ihmc.utilities.net.AtomicSettableTimestampProvider;
import us.ihmc.utilities.net.ObjectCommunicator;
import us.ihmc.utilities.net.TimestampListener;
import us.ihmc.utilities.net.TimestampProvider;

public class CommandPlayer implements TimestampListener
{
   private final ExecutorService threadPool = Executors.newSingleThreadExecutor(ThreadTools.getNamedThreadFactory("CommandPlaybackThread"));
   private final TimestampProvider timestampProvider;
   private final ObjectCommunicator fieldComputerClient;
   
   private Object syncObject = new Object();
   
   private boolean playingBack = false;
   private Transform3D playbackTransform = new Transform3D();
   private long startTime = Long.MIN_VALUE; 
   private long nextCommandtimestamp = Long.MIN_VALUE;
   
   private ScriptFileLoader loader;
   
   public CommandPlayer(AtomicSettableTimestampProvider timestampProvider, ObjectCommunicator fieldComputerClient, DRCNetClassList drcNetClassList)
   {
      this.timestampProvider = timestampProvider;
      this.fieldComputerClient = fieldComputerClient;
      timestampProvider.attachListener(this);
   }
   
   public void startPlayback(String filename, Transform3D playbackTransform)
   {
      synchronized (syncObject)
      {
         if(playingBack)
         {
            System.err.println("Already playing back, ignoring command");
            return;
         }
      }
      try
      {
         String fullpath = ScriptEngineSettings.scriptDirectory + filename + ScriptEngineSettings.extension;
         
         loader = new ScriptFileLoader(fullpath);
         startTime = timestampProvider.getTimestamp();
         this.playbackTransform.set(playbackTransform);
                  
         synchronized (syncObject)
         {
            nextCommandtimestamp = loader.getTimestamp();
            playingBack = true;
         }
         System.out.println("Started playback of " + filename);
      }
      catch(IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   public void timestampChanged(final long newTimestamp)
   {
      synchronized (syncObject)
      {
         if(playingBack)
         {
            if(((newTimestamp - startTime) >= nextCommandtimestamp))
            {
               threadPool.execute(new Runnable()
               {
                  
                  public void run()
                  {
                     executeNewCommand(newTimestamp);
                  }
               });
            }
         }
      }
   }
   
   public void executeNewCommand(long timestamp)
   {
      try
      {
         Object object = loader.getObject(playbackTransform);
         
         if(!(object instanceof EndOfScriptCommand))
         {
            fieldComputerClient.consumeObject(object);
         }
         

         nextCommandtimestamp = loader.getTimestamp();
       
         
      }
      catch (IOException e)
      {
         System.out.println("End of inputstream reached, stopping playback");
         synchronized (syncObject)
         {
            playingBack = false;
         }
         
         loader.close();
      }
   }
}
