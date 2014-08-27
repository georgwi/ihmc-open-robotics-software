package com.yobotics.simulationconstructionset.physics.collision.gdx;

import us.ihmc.yoUtilities.dataStructure.registry.YoVariableRegistry;

import com.yobotics.simulationconstructionset.physics.ScsCollisionDetector;
import com.yobotics.simulationconstructionset.physics.collision.SCSCollisionDetectorTest;

/**
 * @author Peter Abeles
 */
public class GdxCollisionDetectorTest extends SCSCollisionDetectorTest
{
   @Override
   public ScsCollisionDetector createCollisionInterface()
   {
      return new GdxCollisionDetector(new YoVariableRegistry("Dummy"), 1000);
   }
}
