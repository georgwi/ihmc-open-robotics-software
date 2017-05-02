package us.ihmc.commonWalkingControlModules.configurations;

import us.ihmc.commonWalkingControlModules.inverseKinematics.JointPrivilegedConfigurationHandler;

public class StraightLegWalkingParameters
{
   public boolean includeHipPitchPrivilegedConfiguration()
   {
      return false;
   }

   /**
    * This is the speed used to straighten the desire privileged configuration of the support leg's knee.
    * This is used whenever a leg is first loaded to straighten from the current configuration to the
    * straight configuration defined by {@link #getStraightKneeAngle()}.
    *
    * @return knee rad/second for straightening
    */
   public double getSpeedForSupportKneeStraightening()
   {
      return 0.25;
   }

   /**
    * Angle used to what it means to set the knee privileged configuration to straight.
    * This is used in the straight leg state by the support legs when the robot is attempting to walk with
    * straight legs, and also to help extend the leg at the end of the swing state.
    *
    * @return knee angle in radians
    */
   public double getStraightKneeAngle()
   {
      return 0.2;
   }

   /**
    * Returns a fraction of the swing state to switch the knee privileged configuration to being straight.
    * This is critical to allow the robot to "extend" the leg out to the next foothold.
    *
    * @return fraction of swing state (0.0 to 1.0)
    */
   public double getFractionOfSwingToStraightenLeg()
   {
      return 0.8;
   }

   /**
    * Returns a fraction of the transfer state to switch the knee privileged configuration to bent.
    * This is important to start collapsing the leg to avoid being stuck in the singularity, allowing the
    * upcoming swing leg to start the swing motion naturally.
    *
    * @return fraction of transfer state (0.0 to 1.0)
    */
   public double getFractionOfTransferToCollapseLeg()
   {
      return 0.9;
   }

   /**
    * Determines whether or not to attempt to use straight legs when indirectly controlling the center of mass
    * height using the nullspace in the full task Jacobian.
    * This will not do anything noticeable unless {@link WalkingControllerParameters#controlHeightWithMomentum()}
    * returns true, as that indicates whether or not to use the pelvis to control the center of mass height.
    *
    * @return boolean (true = try and straighten, false = do not try and straighten)
    */
   public boolean attemptToStraightenLegs()
   {
      return false;
   }

   /**
    * <p>
    * This is the configuration gain used to control the privileged joint accelerations or privileged joint velocities
    * for the other leg pitch joints. For a typical humanoid, these joints are the hip pitch and ankle pitch.
    * These additional degrees of freedom are important to stabilize the knee pitch joint when attempting
    * to stand and walk with straight legs.
    * This is the proportional gain used by the {@link JointPrivilegedConfigurationHandler} to determine either
    * the privileged acceleration or the privileged velocity to project into the nullspace of the full task Jacobian.
    * </p>
    * @return privileged configuration gain.
    */
   public double getLegPitchPrivilegedConfigurationGain()
   {
      return 40.0;
   }

   /**
    * <p>
    * This is the velocity gain used to damp the privileged joint accelerations for the other leg pitch joints.
    * For a typical humanoid, these joints are the hip pitch and ankle pitch.
    * These additional degrees of freedom are important to stabilize the knee pitch joint when attempting
    * to stand and walk with straight legs.
    * This is the velocity gain used by the {@link JointPrivilegedConfigurationHandler} to damp the privileged
    * accelerations to project into the nullspace of the full task Jacobian. Note that if using the inverse kinematics
    * module, this gain does nothing, as that is determining privileged joint velocities rather than privileged
    * joint accelerations.
    * </p>
    * @return privileged velocity gain.
    */
   public double getLegPitchPrivilegedVelocityGain()
   {
      return 6.0;
   }

   /**
    * <p>
    * This is the weight placed on the privileged joint accelerations for the other leg pitch joints in the
    * optimization. For a typical humanoid, these joints are the hip pitch and ankle pitch.
    * These additional degrees of freedom are important to stabilize the knee pitch joint when attempting
    * to stand and walk with straight legs.
    * </p>
    * @return privileged configuration weight.
    */
   public double getLegPitchPrivilegedWeight()
   {
      return 5.0;
   }

   public double getKneeStraightLegPrivilegedConfigurationGain()
   {
      return 40.0;
   }

   public double getKneeStraightLegPrivilegedVelocityGain()
   {
      return 6.0;
   }

   public double getKneeStraightLegPrivilegedWeight()
   {
      return 5.0;
   }

   public double getKneeBentLegPrivilegedConfigurationGain()
   {
      return 40.0;
   }

   public double getKneeBentLegPrivilegedVelocityGain()
   {
      return 6.0;
   }

   public double getKneeBentLegPrivilegedWeight()
   {
      return 5.0;
   }

   public double getPrivilegedMaxVelocity()
   {
      return 2.0;
   }

   public double getPrivilegedMaxAcceleration()
   {
      return Double.POSITIVE_INFINITY;
   }
}
