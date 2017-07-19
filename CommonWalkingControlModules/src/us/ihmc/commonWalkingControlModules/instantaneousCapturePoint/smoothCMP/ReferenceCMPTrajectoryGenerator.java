package us.ihmc.commonWalkingControlModules.instantaneousCapturePoint.smoothCMP;

import java.util.ArrayList;
import java.util.List;

import us.ihmc.commonWalkingControlModules.angularMomentumTrajectoryGenerator.YoFrameTrajectory3D;
import us.ihmc.commons.PrintTools;
import us.ihmc.robotics.geometry.FramePoint;
import us.ihmc.robotics.geometry.FrameVector;
import us.ihmc.robotics.math.frames.YoFramePoint;
import us.ihmc.robotics.math.frames.YoFrameVector;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoInteger;

public class ReferenceCMPTrajectoryGenerator
{
   private static final int maxNumberOfFootstepsToConsider = 4;

   private final List<YoDouble> swingDurations;
   private final List<YoDouble> swingSplitFractions;

   private final List<YoDouble> transferDurations;
   private final List<YoDouble> transferSplitFractions;

   private final List<CMPTrajectory> transferCMPTrajectories = new ArrayList<>();
   private final List<CMPTrajectory> swingCMPTrajectories = new ArrayList<>();

   private final YoInteger numberOfFootstepsToConsider;

   private double initialTime;
   private int numberOfRegisteredSteps;
   private CMPTrajectory activeTrajectory;

   private final FramePoint desiredCMP = new FramePoint();
   private final FrameVector desiredCMPVelocity = new FrameVector();

   public ReferenceCMPTrajectoryGenerator(String namePrefix, YoInteger numberOfFootstepsToConsider, List<YoDouble> swingDurations,
                                          List<YoDouble> transferDurations, List<YoDouble> swingSplitFractions, List<YoDouble> transferSplitFractions,
                                          YoVariableRegistry registry)
   {
      this.swingDurations = swingDurations;
      this.transferDurations = transferDurations;
      this.swingSplitFractions = swingSplitFractions;
      this.transferSplitFractions = transferSplitFractions;

      this.numberOfFootstepsToConsider = numberOfFootstepsToConsider;

      for (int i = 0; i < maxNumberOfFootstepsToConsider; i++)
      {
         CMPTrajectory transferCMPTrajectory = new CMPTrajectory(namePrefix + "Transfer" + i, registry);
         CMPTrajectory swingCMPTrajectory = new CMPTrajectory(namePrefix + "Swing" + i, registry);
         transferCMPTrajectories.add(transferCMPTrajectory);
         swingCMPTrajectories.add(swingCMPTrajectory);
      }
   }

   public void reset()
   {
      for (int i = 0; i < maxNumberOfFootstepsToConsider; i++)
      {
         transferCMPTrajectories.get(i).reset();
         swingCMPTrajectories.get(i).reset();
      }

      activeTrajectory = null;
   }

   public void update(double currentTime)
   {
      double timeInState = currentTime - initialTime;

      if (activeTrajectory != null)
         activeTrajectory.update(timeInState, desiredCMP, desiredCMPVelocity);
   }

   public void getPosition(FramePoint desiredCMPToPack)
   {
      desiredCMPToPack.setIncludingFrame(desiredCMP);
   }

   public void getPosition(YoFramePoint desiredCMPToPack)
   {
      desiredCMPToPack.set(desiredCMP);
   }
   
   public void getVelocity(FrameVector desiredCMPVelocityToPack)
   {
      desiredCMPVelocityToPack.setIncludingFrame(desiredCMPVelocity);
   }

   public void getVelocity(YoFrameVector desiredCMPVelocityToPack)
   {
      desiredCMPVelocityToPack.set(desiredCMPVelocity);
   }
   
   public void getLinearData(FramePoint positionToPack, FrameVector velocityToPack)
   {
      getPosition(positionToPack);
      getVelocity(velocityToPack);
   }
   
   public void getLinearData(YoFramePoint positionToPack, YoFrameVector velocityToPack)
   {
      getPosition(positionToPack);
      getVelocity(velocityToPack);
   }

   public List<CMPTrajectory> getTransferCMPTrajectories()
   {
      return transferCMPTrajectories;
   }

   public List<CMPTrajectory> getSwingCMPTrajectories()
   {
      return swingCMPTrajectories;
   }

   public void setNumberOfRegisteredSteps(int numberOfRegisteredSteps)
   {
      this.numberOfRegisteredSteps = numberOfRegisteredSteps;
   }

   public void initializeForTransfer(double currentTime, List<? extends CoPTrajectoryInterface> transferCoPTrajectories,
                                     List<? extends CoPTrajectoryInterface> swingCoPTrajectories)
   {

      initialTime = currentTime;

      // TODO this needs to combine the angular momentum trajectory with the cop trajectory

      int numberOfSteps = Math.min(numberOfRegisteredSteps, numberOfFootstepsToConsider.getIntegerValue());
      for (int stepIndex = 0; stepIndex < numberOfSteps; stepIndex++)
      {
         CMPTrajectory transferCMPTrajectory = transferCMPTrajectories.get(stepIndex);
         CoPTrajectoryInterface transferCoPTrajectory = transferCoPTrajectories.get(stepIndex);

         for (int segmentIndex = 0; segmentIndex < transferCoPTrajectory.getNumberOfSegments(); segmentIndex++)
         {
            YoFrameTrajectory3D cmpSegment = transferCMPTrajectory.getNextSegment();
            YoFrameTrajectory3D copSegment = transferCoPTrajectory.getPolynomials().get(segmentIndex);

            cmpSegment.set(copSegment);
         }

         CMPTrajectory swingCMPTrajectory = swingCMPTrajectories.get(stepIndex);
         CoPTrajectoryInterface swingCoPTrajectory = swingCoPTrajectories.get(stepIndex);
         for (int segmentIndex = 0; segmentIndex < swingCoPTrajectory.getNumberOfSegments(); segmentIndex++)
         {
            YoFrameTrajectory3D cmpSegment = swingCMPTrajectory.getNextSegment();
            YoFrameTrajectory3D copSegment = swingCoPTrajectory.getPolynomials().get(segmentIndex);
            cmpSegment.set(copSegment);
         }
      }

      // handle final transfer
      CMPTrajectory transferCMPTrajectory = transferCMPTrajectories.get(numberOfSteps);
      CoPTrajectoryInterface transferCoPTrajectory = transferCoPTrajectories.get(numberOfSteps);

      for (int segmentIndex = 0; segmentIndex < transferCoPTrajectory.getNumberOfSegments(); segmentIndex++)
      {
         YoFrameTrajectory3D cmpSegment = transferCMPTrajectory.getNextSegment();
         cmpSegment.set(transferCoPTrajectory.getPolynomials().get(segmentIndex));
      }

      activeTrajectory = transferCMPTrajectories.get(0);
   }

   public void initializeForSwing(double currentTime, List<? extends CoPTrajectoryInterface> transferCoPTrajectories,
                                  List<? extends CoPTrajectoryInterface> swingCoPTrajectories)
   {
      initialTime = currentTime;
      // TODO this needs to combine the angular momentum trajectory with the cop trajectory

      // handle current swing
      CMPTrajectory swingCMPTrajectory = swingCMPTrajectories.get(0);
      CoPTrajectoryInterface swingCoPTrajectory = swingCoPTrajectories.get(0);
      //      for (int segmentIndex = 0; segmentIndex < swingCoPTrajectory.getNumberOfSegments(); segmentIndex++)
      //      {
      //         YoFrameTrajectory3D cmpSegment = swingCMPTrajectory.getNextSegment();
      //         YoFrameTrajectory3D copSegment = swingCoPTrajectory.getPolynomials().get(segmentIndex);
      //
      //         cmpSegment.set(copSegment);
      //      }

      int numberOfSteps = Math.min(numberOfRegisteredSteps, numberOfFootstepsToConsider.getIntegerValue());
      for (int stepIndex = 0; stepIndex < numberOfSteps; stepIndex++)
      {
         CMPTrajectory transferCMPTrajectory = transferCMPTrajectories.get(stepIndex);
         CoPTrajectoryInterface transferCoPTrajectory = transferCoPTrajectories.get(stepIndex);

         for (int segmentIndex = 0; segmentIndex < transferCoPTrajectory.getNumberOfSegments(); segmentIndex++)
         {
            YoFrameTrajectory3D cmpSegment = transferCMPTrajectory.getNextSegment();
            YoFrameTrajectory3D copSegment = transferCoPTrajectory.getPolynomials().get(segmentIndex);

            cmpSegment.set(copSegment);
         }

         swingCMPTrajectory = swingCMPTrajectories.get(stepIndex);
         swingCoPTrajectory = swingCoPTrajectories.get(stepIndex);
         for (int segmentIndex = 0; segmentIndex < swingCoPTrajectory.getNumberOfSegments(); segmentIndex++)
         {
            YoFrameTrajectory3D cmpSegment = swingCMPTrajectory.getNextSegment();
            YoFrameTrajectory3D copSegment = swingCoPTrajectory.getPolynomials().get(segmentIndex);

            cmpSegment.set(copSegment);
         }
      }

      // handle final transfer
      CMPTrajectory transferCMPTrajectory = transferCMPTrajectories.get(numberOfSteps);
      CoPTrajectoryInterface transferCoPTrajectory = transferCoPTrajectories.get(numberOfSteps);

      for (int segmentIndex = 0; segmentIndex < transferCoPTrajectory.getNumberOfSegments(); segmentIndex++)
      {
         YoFrameTrajectory3D cmpSegment = transferCMPTrajectory.getNextSegment();
         YoFrameTrajectory3D copSegment = transferCoPTrajectory.getPolynomials().get(segmentIndex);

         cmpSegment.set(copSegment);
      }

      activeTrajectory = swingCMPTrajectories.get(0);
   }
}
