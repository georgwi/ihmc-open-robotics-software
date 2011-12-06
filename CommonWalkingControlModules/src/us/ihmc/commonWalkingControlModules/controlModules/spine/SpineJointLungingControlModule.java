package us.ihmc.commonWalkingControlModules.controlModules.spine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;

import javax.vecmath.Vector2d;
import javax.vecmath.Vector3d;

import us.ihmc.commonWalkingControlModules.controlModuleInterfaces.SpineLungingControlModule;
import us.ihmc.commonWalkingControlModules.controlModuleInterfaces.SpineControlModule;
import us.ihmc.commonWalkingControlModules.couplingRegistry.CouplingRegistry;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.LegJointName;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.R2SpineLinkName;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.SpineJointName;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.SpineTorques;
import us.ihmc.commonWalkingControlModules.referenceFrames.CommonWalkingReferenceFrames;
import us.ihmc.commonWalkingControlModules.sensors.ProcessedSensorsInterface;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.utilities.containers.ContainerTools;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.InverseDynamicsCalculator;
import us.ihmc.utilities.screwTheory.InverseDynamicsJoint;
import us.ihmc.utilities.screwTheory.RevoluteJoint;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.SpatialAccelerationVector;
import us.ihmc.utilities.screwTheory.Wrench;

import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.YoVariableRegistry;
import com.yobotics.simulationconstructionset.util.PIDController;

public class SpineJointLungingControlModule implements SpineLungingControlModule
{
   private final YoVariableRegistry registry = new YoVariableRegistry("SpineJointLungingControlModule");
   
   public ArrayList<DoubleYoVariable> spinePitchErrorList = new ArrayList<DoubleYoVariable>();

   private final EnumMap<SpineJointName, DoubleYoVariable> desiredAngles = ContainerTools.createEnumMap(SpineJointName.class);
   private final EnumMap<SpineJointName, PIDController> spineControllers = ContainerTools.createEnumMap(SpineJointName.class);

//   private final EnumMap<SpineJointName, DoubleYoVariable> actualAngles;
//   private final EnumMap<SpineJointName, DoubleYoVariable> actualAngleVelocities;

   private final ProcessedSensorsInterface processedSensors;
   private final double controlDT;
   
   private final EnumMap<SpineJointName, PIDController> spineJointIDQddControllers = ContainerTools.createEnumMap(SpineJointName.class);
   private InverseDynamicsCalculator spineJointIDCalc;
   private RigidBody pelvisRigidBody;
   private ArrayList<RevoluteJoint> spineRevoluteJointList;
   
   private FrameVector desiredTorqueBetweenPelvisAndChest;
   private SpineTorques spineTorques = new SpineTorques();

   private final RigidBody chest;

   private Wrench externalWrench;

   private final CommonWalkingReferenceFrames referenceFrames;
 
   public SpineJointLungingControlModule(ProcessedSensorsInterface processedSensors, double controlDT, YoVariableRegistry parentRegistry,
         InverseDynamicsCalculator spineJointIDCalc, RigidBody chest, ArrayList<RevoluteJoint> spineRevoluteJointList,
         CommonWalkingReferenceFrames referenceFrames)
   {
      this.spineJointIDCalc = spineJointIDCalc;
//      this.pelvisRigidBody = pelvisRigidBody;
      this.pelvisRigidBody = processedSensors.getFullRobotModel().getPelvis();
      this.spineRevoluteJointList = spineRevoluteJointList;
      this.processedSensors = processedSensors;
      this.controlDT = controlDT;
      this.chest = chest;
      this.referenceFrames = referenceFrames;
      parentRegistry.addChild(registry);
      
      populateYoVariables();
      populateControllers();
      initializeVariables();
      setDesireds();
      setGains();
   }
   
   private void initializeVariables()
   {
      ReferenceFrame pelvisFrame = pelvisRigidBody.getBodyFixedFrame();
      externalWrench = new Wrench(pelvisFrame, pelvisFrame);
   }

   /**
    * Old. Do not use.
    */
   public void doSpineControl(SpineTorques spineTorquesToPack)
   {
      for (SpineJointName spineJointName : SpineJointName.values())
      {
         PIDController pidController = spineControllers.get(spineJointName);
         
         double desiredPosition = desiredAngles.get(spineJointName).getDoubleValue();
         double desiredVelocity = 0.0;

         double actualPosition = processedSensors.getSpineJointPosition(spineJointName); // actualAngles.get(spineJointName).getDoubleValue();
         double actualVelocity = processedSensors.getSpineJointVelocity(spineJointName); //actualAngleVelocities.get(spineJointName).getDoubleValue();

         double torque = pidController.compute(actualPosition, desiredPosition, actualVelocity, desiredVelocity, controlDT);
         spineTorques.setTorque(spineJointName, torque);
      }
   }

   /**
    * Sets the pitch and roll spine torques corresponding to the desired deltaCMP 
    * @param deltaCMP: The normalized vector from the desired ICP to the current ICP (in the x-y plane), scaled by the CMP radius corresponding to the maximum hip torque
    * @param spineTorquesToPack: The spine pitch and roll torques corresponding to a 90 degree rotation of deltaCMP about the z-axis
    */
   public void setPitchRollSpineTorquesFromDeltaCMP(Vector2d deltaCMP)
   {
      double mass = processedSensors.getTotalMass();
      double gravity = processedSensors.getGravityInWorldFrame().getZ();

//      this.spineTorques.setTorque(SpineJointName.SPINE_PITCH, mass * gravity * deltaCMP.getX());
//      this.spineTorques.setTorque(SpineJointName.SPINE_ROLL, mass * gravity * - deltaCMP.getY());
      this.spineTorques.setTorque(SpineJointName.SPINE_ROLL, mass * gravity * - deltaCMP.getX());
      this.spineTorques.setTorque(SpineJointName.SPINE_PITCH, mass * gravity * - deltaCMP.getY());
   }
   

   private void populateYoVariables()
   {
      for (SpineJointName spineJointName : SpineJointName.values())
      {
         String name = "desired" + spineJointName.getCamelCaseNameForMiddleOfExpression();
         DoubleYoVariable variable = new DoubleYoVariable(name, registry);
         desiredAngles.put(spineJointName, variable);
         
         spinePitchErrorList.add(new DoubleYoVariable(spineJointName + "Error", registry));
      }
   }

   private void populateControllers()
   {
      for (SpineJointName spineJointName : SpineJointName.values())
      {
         spineControllers.put(spineJointName, new PIDController(spineJointName.getCamelCaseNameForStartOfExpression() + "ctrl", registry));
         spineJointIDQddControllers.put(spineJointName, new PIDController(spineJointName.getCamelCaseNameForStartOfExpression() + "qddDesired" + "ctrl", registry));            
      }
   }

   private void setDesireds()
   {
      /*
       * 100610 pdn: I tried setting this to 0.1 immediately and the robot fell
       * I could start off with it 0.05 and once it got walking, change it to 0.1
       */
      desiredAngles.get(SpineJointName.SPINE_PITCH).set(0.0);
   }

   public void setGains()
   {
      spineControllers.get(SpineJointName.SPINE_YAW).setProportionalGain(3000.0);
      spineControllers.get(SpineJointName.SPINE_PITCH).setProportionalGain(3000.0);
      spineControllers.get(SpineJointName.SPINE_ROLL).setProportionalGain(3000.0);

      spineControllers.get(SpineJointName.SPINE_YAW).setDerivativeGain(200.0);
      spineControllers.get(SpineJointName.SPINE_PITCH).setDerivativeGain(200.0);
      spineControllers.get(SpineJointName.SPINE_ROLL).setDerivativeGain(200.0);
      
      
      spineJointIDQddControllers.get(SpineJointName.SPINE_YAW).setProportionalGain(3000.0);
      spineJointIDQddControllers.get(SpineJointName.SPINE_PITCH).setProportionalGain(700.0);
      spineJointIDQddControllers.get(SpineJointName.SPINE_ROLL).setProportionalGain(10000.0);

      spineJointIDQddControllers.get(SpineJointName.SPINE_YAW).setDerivativeGain(200.0);
      spineJointIDQddControllers.get(SpineJointName.SPINE_PITCH).setDerivativeGain(100.0);
      spineJointIDQddControllers.get(SpineJointName.SPINE_ROLL).setDerivativeGain(1000.0);
      
   }
   
   public void doMaintainDesiredChestOrientation()
   {
      spineTorques.setTorquesToZero();
      
      setDesiredAccelerationOnSpineJoints();
      
      spineJointIDCalc.compute();
      
      setSpineTorquesFromSpineJoints();
   }
   
   public void getWrenchByUpperBody(Wrench upperBodyWrenchToPack)
   {
      processedSensors.getFullRobotModel().getRootJoint().packWrench(upperBodyWrenchToPack);
      upperBodyWrenchToPack.changeBodyFrameAttachedToSameBody(referenceFrames.getPelvisFrame());
      upperBodyWrenchToPack.changeFrame(referenceFrames.getPelvisFrame());
   }
   
   public void setGainsToZero(ArrayList<SpineJointName> spineJointsWithZeroGain)
   {
      for (int index = 0; index < spineJointsWithZeroGain.size(); index ++)
      {
         SpineJointName spineJointName = spineJointsWithZeroGain.get(index);
         spineJointIDQddControllers.get(spineJointName).setProportionalGain(0.0);
         spineJointIDQddControllers.get(spineJointName).setDerivativeGain(0.0);
      }
   }
   
   public void setWrench(Wrench wrenchOnPelvis)
   {
      // to prevent wrong frame stuff
      externalWrench.setAngularPart(wrenchOnPelvis.getAngularPartCopy());
      spineJointIDCalc.setExternalWrench(pelvisRigidBody, externalWrench);
   }

   public void getSpineTorques(SpineTorques spineTorquesToPack)
   {
      spineTorquesToPack.setTorques(this.spineTorques.getTorquesCopy());
   }

   public void scaleGainsBasedOnLungeAxis(Vector2d lungeAxis)
   {
      lungeAxis.absolute();
      lungeAxis.normalize();

      // x
      double scaleFactor = lungeAxis.getX(); 
      SpineJointName spineJointName = SpineJointName.SPINE_PITCH;
      scalePDGainsForIDController(scaleFactor, spineJointName);

      // y
      scaleFactor = lungeAxis.getY(); 
      spineJointName = SpineJointName.SPINE_ROLL;
      scalePDGainsForIDController(scaleFactor, spineJointName);
   }

   private void scalePDGainsForIDController(double scaleFactor, SpineJointName spineJointName)
   {
      double proportionalGain = spineJointIDQddControllers.get(spineJointName).getProportionalGain();
      spineJointIDQddControllers.get(spineJointName).setProportionalGain(proportionalGain * scaleFactor);
      
      double derivativeGain = spineJointIDQddControllers.get(spineJointName).getDerivativeGain();
      spineJointIDQddControllers.get(spineJointName).setDerivativeGain(derivativeGain * scaleFactor);
   }
   
   private void setDesiredAccelerationOnSpineJoints()
   {
      for (SpineJointName spineJointName : SpineJointName.values())
      {
         RevoluteJoint spineRevoluteJoint = spineRevoluteJointList.get(spineJointName.ordinal());
         
         double actualPosition = processedSensors.getSpineJointPosition(spineJointName);
         double actualVelocity = processedSensors.getSpineJointVelocity(spineJointName);
         
         double desiredPosition = desiredAngles.get(spineJointName).getDoubleValue();
         double desiredVelocity = 0.0;
         
         spinePitchErrorList.get(spineJointName.ordinal()).set(0.0 - actualPosition);
         double qddDesired = spineJointIDQddControllers.get(spineJointName).compute(actualPosition, desiredPosition, actualVelocity, desiredVelocity, controlDT);
         spineRevoluteJoint.setQddDesired(qddDesired);
      }
   }


   private void setSpineTorquesFromSpineJoints()
   {
      for (SpineJointName spineJointName : SpineJointName.values())
      {
         RevoluteJoint spineRevoluteJoint = spineRevoluteJointList.get(spineJointName.ordinal());
         spineTorques.setTorque(spineJointName, spineRevoluteJoint.getTau());   
      }
   }

   @Override
   public void setHipXYTorque(Vector3d desiredTorqueVector)
   {
      spineTorques.setTorque(SpineJointName.SPINE_PITCH, desiredTorqueVector.getY());
      spineTorques.setTorque(SpineJointName.SPINE_ROLL, desiredTorqueVector.getX());
      spineTorques.setTorque(SpineJointName.SPINE_YAW, desiredTorqueVector.getZ());

      
   }
}

