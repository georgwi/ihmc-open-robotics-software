package us.ihmc.commonWalkingControlModules.controlModules.arm;

import java.util.EnumMap;

import us.ihmc.commonWalkingControlModules.controlModuleInterfaces.ArmControlModule;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.ArmJointName;
import us.ihmc.commonWalkingControlModules.partNamesAndTorques.ArmTorques;
import us.ihmc.robotSide.SideDependentList;

public interface RefInputArmControlModule extends ArmControlModule
{
   public abstract void doArmControl(ArmTorques[] armTorquesToPack);

   public abstract void setDesireds(SideDependentList<EnumMap<ArmJointName, Double>> desiredJointPositions);
   
}
