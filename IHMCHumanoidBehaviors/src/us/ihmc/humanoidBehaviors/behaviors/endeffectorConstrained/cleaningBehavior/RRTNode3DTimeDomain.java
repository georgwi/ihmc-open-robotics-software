package us.ihmc.humanoidBehaviors.behaviors.endeffectorConstrained.cleaningBehavior;

import us.ihmc.commons.PrintTools;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.manipulation.planning.rrt.constrainedplanning.tools.WheneverWholeBodyKinematicsSolver;
import us.ihmc.manipulation.planning.rrt.generalrrt.RRTNode;
import us.ihmc.manipulation.planning.solarpanelmotion.SolarPanelPath;
import us.ihmc.robotics.robotSide.RobotSide;

public class RRTNode3DTimeDomain extends RRTNode
{
   public static WheneverWholeBodyKinematicsSolver nodeValidityTester;
   public static SolarPanelPath cleaningPath;
   
   public RRTNode3DTimeDomain()
   {
      super(4);
   }

   public RRTNode3DTimeDomain(double timeK, double pelvisHeight, double chestYaw, double chestPitch)
   {
      super(4);
      super.setNodeData(0, timeK);
      super.setNodeData(1, pelvisHeight);
      super.setNodeData(2, chestYaw);
      super.setNodeData(3, chestPitch);
   }  
   
   @Override
   public boolean isValidNode()
   {
      PrintTools.info("isvalid START");
      
      nodeValidityTester.initialize();      
      nodeValidityTester.holdCurrentTrajectoryMessages();
      
      // Hand
      Quaternion desiredHandOrientation = new Quaternion();
      desiredHandOrientation.appendPitchRotation(Math.PI*30/180);
      nodeValidityTester.setDesiredHandPose(RobotSide.RIGHT, new Pose3D(new Point3D(0.6, -0.4, 1.0), desiredHandOrientation));
      
      Quaternion desiredChestOrientation = new Quaternion();
      desiredChestOrientation.appendPitchRotation(Math.PI*10/180);
      nodeValidityTester.setDesiredChestOrientation(desiredChestOrientation);
            
      nodeValidityTester.setDesiredPelvisHeight(0.75);
      
      nodeValidityTester.putTrajectoryMessages();
      
      
      
      PrintTools.info("isvalid END");
      return true;
   }

   @Override
   public RRTNode createNode()
   {
      return new RRTNode3DTimeDomain();
   }

   @Override
   public void setRandomNodeData()
   {
      // TODO Auto-generated method stub
      
   }
}