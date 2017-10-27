package us.ihmc.avatar.networkProcessor.kinematicsToolboxModule;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import gnu.trove.map.hash.THashMap;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.CollisionAvoidanceCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.InverseKinematicsCommand;
import us.ihmc.commonWalkingControlModules.controllerCore.command.inverseKinematics.InverseKinematicsCommandList;
import us.ihmc.geometry.polytope.DCELPolytope.Frame.FrameConvexPolytope;
import us.ihmc.robotics.screwTheory.RigidBody;
import us.ihmc.robotics.screwTheory.ScrewTools;

/**
 * Simple module that checks if the rigid bodies are colliding with the list of obstacles submitted
 * and generates collision avoidance commands for the optimization module
 * TODO include method for checking self collisions
 * @author Apoorv S
 */
public class CollisionAvoidanceModule
{
   /**
    * Stores the collision detectors for the individual rigid bodies the idea here being that simplices can be 
    * carried forward into the next iteration to speed up the process
    */
   private THashMap<RigidBody, RigidBodyCollisionDetector> collisionDetectorMap = new THashMap<>();

   /**
    * Stores the provided obstacle / collision object meshes
    */
   private List<FrameConvexPolytope> obstacleMeshes = new ArrayList<>();

   /**
    * List of robot rigid bodies that the class will test for collisions
    */
   private List<RigidBody> rigidBodies;

   /**
    * List of rigid bodies that can collide with each other 
    * TODO this is unimplemented functionality
    */
   private List<Pair<RigidBody, RigidBody>> selfCollisionList = new ArrayList<>();

   /**
    * Settings for collision detection and avoidance
    */
   private final CollisionAvoidanceModuleSettings parameters = new CollisionAvoidanceModuleSettings();
   
   /**
    * Maintains the status if the collision avoidance is enabled
    */
   private boolean isEnabled = false;
   
   /**
    * Maintains the status if collision avoidance is on
    */
   private boolean isWorking = false;
   
   /**
    * List of collision avoidance commands to submit to the controller core
    */
   private InverseKinematicsCommandList commandList = new InverseKinematicsCommandList();
   
   public void setRigidBodyCollisionMeshes(THashMap<RigidBody, FrameConvexPolytope> collisionMeshMap)
   {
      isWorking = (collisionMeshMap != null);
      if(!isWorking)
         return;
      this.rigidBodies = new ArrayList<>(collisionMeshMap.keySet());
      for(int i = 0; i < rigidBodies.size(); i++)
      {
         collisionDetectorMap.put(rigidBodies.get(i), new RigidBodyCollisionDetector(collisionMeshMap.get(rigidBodies.get(i)), parameters));
      }
   }
   
   public void submitSelfCollisionPair(RigidBody rigidBody1, RigidBody rigidBody2)
   {
      selfCollisionList.add(new ImmutablePair<RigidBody, RigidBody>(rigidBody1, rigidBody2));
   }
   
   public void submitObstacleCollisionMesh(FrameConvexPolytope obstacleCollisionMesh)
   {
      this.obstacleMeshes.add(obstacleCollisionMesh);
   }
   
   public void submitObstacleCollisionMesh(FrameConvexPolytope... obstacleMeshes)
   {
      for(int i = 0; i < obstacleMeshes.length; i++)
      {
         this.obstacleMeshes.add(obstacleMeshes[i]);
      }
   }
   
   public void submitObstacleCollisionMesh(List<FrameConvexPolytope> obstacleMeshes)
   {
      obstacleMeshes.addAll(obstacleMeshes);
   }
   
   public void clearObstacleMeshList()
   {
      obstacleMeshes.clear();
   }
   
   public void checkCollisions()
   {
      if(!isEnabled || !isWorking)
         return;
      commandList.clear();
      for(int i = 0; i < rigidBodies.size(); i++)
      {
         RigidBody rigidBody = rigidBodies.get(i);
         RigidBodyCollisionDetector collisionDetector = collisionDetectorMap.get(rigidBody);
         collisionDetector.checkCollisionsWithObstacles(obstacleMeshes);
      }
   }

   public boolean isEnabled()
   {
      return isEnabled;
   }

   public void enable(boolean enabled)
   {
      this.isEnabled = enabled;
   }

   public InverseKinematicsCommandList getCollisionAvoidanceCommands()
   {
      return commandList;
   }
}
