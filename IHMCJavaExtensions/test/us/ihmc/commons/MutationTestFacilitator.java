package us.ihmc.commons;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import org.pitest.mutationtest.commandline.MutationCoverageReport;

import us.ihmc.tools.io.files.BasicPathVisitor;
import us.ihmc.tools.io.files.FileTools;
import us.ihmc.tools.io.files.PathTools;

public class MutationTestFacilitator
{
   private static final int NUMBER_OF_HOURS_BEFORE_EXPIRATION = 3;
   private static final String REPORT_DIRECTORY_NAME = "pit-reports";

   private Set<Class<?>> testClassesToRun = new HashSet<>();
   private Set<String> classPathsToMutate = new TreeSet<>();
   private Set<Mutator> mutators = new TreeSet<>();

   public enum Mutator
   {
      RETURN_VALS,
      INLINE_CONSTS,
      MATH,
      VOID_METHOD_CALLS,
      NEGATE_CONDITIONALS,
      CONDITIONALS_BOUNDARY,
      INCREMENTS,
      REMOVE_INCREMENTS,
      NON_VOID_METHOD_CALLS,
      CONSTRUCTOR_CALLS,
      REMOVE_CONDITIONALS_EQ_IF,
      REMOVE_CONDITIONALS_EQ_ELSE,
      REMOVE_CONDITIONALS_ORD_IF,
      REMOVE_CONDITIONALS_ORD_ELSE,
      REMOVE_CONDITIONALS,
      EXPERIMENTAL_MEMBER_VARIABLE,
      EXPERIMENTAL_SWITCH,
      EXPERIMENTAL_ARGUMENT_PROPAGATION,
      REMOVE_SWITCH
   }

   /**
    * If this is never called, defaults to ALL.
    */
   public void addMutators(Mutator... mutators)
   {
      this.mutators.addAll(Arrays.asList(mutators));
   }

   public void addTestClassesToRun(Class<?>... testClassesToRun)
   {
      this.testClassesToRun.addAll(Arrays.asList(testClassesToRun));
   }

   public void addPackagePathsToMutate(String... packagePathsToMutate)
   {
      this.classPathsToMutate.addAll(Arrays.asList(packagePathsToMutate));
   }

   public void addNeighborClassesToMutate(Class<?> neighborToMutate)
   {
      this.classPathsToMutate.add(neighborToMutate.getName().substring(0, neighborToMutate.getName().lastIndexOf('.')) + "*");
   }

   public void addClassesToMutate(Class<?>... classesToMutate)
   {
      for (int i = 0; i < classesToMutate.length; i++)
      {
         this.classPathsToMutate.add(classesToMutate[i].getName());
      }
   }

   public void doMutationTest()
   {
      // Delete all entries older than three hours
      PathTools.walkFlat(Paths.get(REPORT_DIRECTORY_NAME), new BasicPathVisitor()
      {
         @Override
         public FileVisitResult visitPath(Path path, PathType pathType)
         {
            if (pathType == PathType.DIRECTORY)
            {
               String baseName = PathTools.getBaseName(path);
               SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmm");
               Date directoryDate = null;
               try
               {
                  directoryDate = simpleDateFormat.parse(baseName);
                  Date currentDate = new Date();
                  long difference = currentDate.getTime() - directoryDate.getTime();
                  if (TimeUnit.HOURS.convert(difference, TimeUnit.MILLISECONDS) > NUMBER_OF_HOURS_BEFORE_EXPIRATION)
                  {
                     FileTools.deleteDirectory(path);
                  }
               }
               catch (ParseException e)
               {
                  e.printStackTrace();
               }
            }

            return FileVisitResult.CONTINUE;
         }
      });

      if (testClassesToRun.isEmpty())
         throw new RuntimeException("No test classes to run!");
      if (classPathsToMutate.isEmpty())
         throw new RuntimeException("No class paths to mutate!");

      String targetClasses = "";
      for (String classPath : classPathsToMutate)
      {
         targetClasses += classPath + ",";
      }
      targetClasses = targetClasses.substring(0, targetClasses.lastIndexOf(','));

      String targetTests = "";
      for (Class<?> testClass : testClassesToRun)
      {
         targetTests += testClass.getName() + ",";
      }
      targetTests = targetTests.substring(0, targetTests.lastIndexOf(','));

      String mutatorsList = "";
      if (mutators.isEmpty())
      {
         mutatorsList = "ALL";
      }
      else
      {
         for (Mutator mutator : mutators)
         {
            mutatorsList += mutator.name() + ",";
         }
         mutatorsList = mutatorsList.substring(0, mutatorsList.lastIndexOf(','));
      }

      MutationCoverageReport.main(new String[] {"--reportDir", REPORT_DIRECTORY_NAME, "--targetClasses", targetClasses, "--targetTests", targetTests,
            "--sourceDirs", "src,test", "--mutators", mutatorsList});
   }

   public void openResultInBrowser()
   {
      File reportDirectory = new File(REPORT_DIRECTORY_NAME);
      if (reportDirectory.isDirectory() && reportDirectory.exists())
      {
         String[] list = reportDirectory.list();
         final String lastDirectoryName = list[list.length - 1];

         System.out.println("Found last directory " + lastDirectoryName);

         PathTools.walkFlat(Paths.get(REPORT_DIRECTORY_NAME, lastDirectoryName), new BasicPathVisitor()
         {
            @Override
            public FileVisitResult visitPath(Path path, PathType pathType)
            {
               String longPathName = path.getFileName().toString();
               if (longPathName.length() > 50)
               {
                  String newPathName = longPathName.substring(0, 20) + "..." + longPathName.substring(longPathName.length() - 20, longPathName.length());
                  Path newPath = Paths.get(REPORT_DIRECTORY_NAME, lastDirectoryName, newPathName);

                  Path indexPath = Paths.get(REPORT_DIRECTORY_NAME, lastDirectoryName, "index.html");
                  List<String> lines = FileTools.readAllLines(indexPath);
                  ArrayList<String> newLines = new ArrayList<>();
                  for (String originalLine : lines)
                  {
                     newLines.add(originalLine.replaceAll(longPathName, newPathName));
                  }
                  FileTools.writeAllLines(newLines, indexPath);

                  try
                  {
                     Files.move(path, newPath);
                  }
                  catch (IOException e)
                  {
                     e.printStackTrace();
                  }
               }
               return FileVisitResult.CONTINUE;
            }
         });

         File reportFile = new File(reportDirectory, lastDirectoryName + "/index.html");
         String absolutePath;
         try
         {
            absolutePath = reportFile.getCanonicalPath();

            absolutePath = absolutePath.replace("\\", "/");
            System.out.println("Opening " + "file://" + absolutePath);

            URI uri = new URI("file://" + absolutePath);
            Desktop.getDesktop().browse(uri);
         }
         catch (IOException e)
         {
            e.printStackTrace();
         }
         catch (URISyntaxException e)
         {
            e.printStackTrace();
         }
      }
   }

   public static void facilitateMutationTestForClass(Class<?> applicationClass, Class<?> testClass)
   {
      MutationTestFacilitator mutationTestFacilitator = new MutationTestFacilitator();
      mutationTestFacilitator.addClassesToMutate(applicationClass);
      mutationTestFacilitator.addTestClassesToRun(testClass);
      mutationTestFacilitator.doMutationTest();
      mutationTestFacilitator.openResultInBrowser();
   }

   public static void facilitateMutationTestForPackage(Class<?> testClass)
   {
      MutationTestFacilitator mutationTestFacilitator = new MutationTestFacilitator();
      mutationTestFacilitator.addNeighborClassesToMutate(testClass);
      mutationTestFacilitator.addTestClassesToRun(testClass);
      mutationTestFacilitator.doMutationTest();
      mutationTestFacilitator.openResultInBrowser();
   }
}
