package us.ihmc.graphics3DDescription.instructions;

import us.ihmc.graphics3DDescription.MeshDataHolder;
import us.ihmc.graphics3DDescription.appearance.AppearanceDefinition;
import us.ihmc.graphics3DDescription.instructions.listeners.MeshChangedListener;

public class Graphics3DAddMeshDataInstruction extends Graphics3DInstruction
{
   private MeshDataHolder meshData;
   private MeshChangedListener meshChangedListener;

   public Graphics3DAddMeshDataInstruction(MeshDataHolder meshData, AppearanceDefinition appearance)
   {
      this.meshData = meshData;
      setAppearance(appearance);
   }

   public MeshDataHolder getMeshData()
   {
      return meshData;
   }


   public String toString()
   {
      return "\t\t\t<MeshDataInstruction@"+hashCode()+">\n";
   }


   public void setMeshChangedListener(MeshChangedListener meshChangedListener)
   {
      this.meshChangedListener = meshChangedListener;
   }

   public void setMesh(MeshDataHolder newMesh)
   {
      this.meshData = newMesh;
      if(meshChangedListener != null)
      {
         meshChangedListener.meshChanged(newMesh);
      }
   }
}
